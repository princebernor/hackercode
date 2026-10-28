package dev.hackercode.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hackercode.admin.PlatformSettingsService;
import dev.hackercode.common.GlobalExceptionHandler;
import dev.hackercode.user.HcPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** In maintenance mode the API is read-only for everyone except moderators and administrators. */
public class MaintenanceFilter extends OncePerRequestFilter {
    private final PlatformSettingsService platform;
    private final ObjectMapper json;

    public MaintenanceFilter(PlatformSettingsService platform, ObjectMapper json) {
        this.platform = platform;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()) || !uri.startsWith("/api/")
                || uri.startsWith("/api/auth/") || uri.startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (platform.flag(PlatformSettingsService.MAINTENANCE)) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean staff = auth != null && auth.getPrincipal() instanceof HcPrincipal p && p.role().isStaff();
            if (!staff) {
                response.setStatus(503);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                json.writeValue(response.getOutputStream(), GlobalExceptionHandler.body("maintenance",
                        platform.get(PlatformSettingsService.MAINTENANCE_MESSAGE), null));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
