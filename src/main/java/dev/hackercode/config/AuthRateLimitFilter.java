package dev.hackercode.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hackercode.admin.PlatformSettingsService;
import dev.hackercode.common.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Throttles credential-related endpoints per client address to slow down brute force and email flooding. */
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> PATHS = Set.of(
            "/api/auth/login", "/api/auth/signup", "/api/auth/forgot-password",
            "/api/auth/reset-password", "/api/auth/resend-verification", "/api/auth/verify-email");

    private final RateLimiter limiter;
    private final PlatformSettingsService settings;
    private final ObjectMapper json;

    public AuthRateLimitFilter(RateLimiter limiter, PlatformSettingsService settings, ObjectMapper json) {
        this.limiter = limiter;
        this.settings = settings;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = "auth:" + request.getRequestURI() + ":" + request.getRemoteAddr();
        int limit = settings.number(PlatformSettingsService.RATE_AUTH);
        if (!limiter.tryAcquire(key, limit, Duration.ofMinutes(10))) {
            response.setStatus(429);
            response.setHeader("Retry-After", "600");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(response.getOutputStream(), GlobalExceptionHandler.body("rate_limited",
                    "Too many attempts. Please wait a few minutes and try again.", null));
            return;
        }
        chain.doFilter(request, response);
    }
}
