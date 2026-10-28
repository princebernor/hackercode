package dev.hackercode.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hackercode.common.GlobalExceptionHandler;
import dev.hackercode.common.Requests;
import dev.hackercode.common.Tokens;
import dev.hackercode.user.HcPrincipal;
import dev.hackercode.user.User;
import dev.hackercode.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs on every request after authentication and enforces server-side truth:
 * <ul>
 *   <li>suspended or deleted accounts are signed out immediately;</li>
 *   <li>sessions revoked from "Security → Sessions" (or by a password change) stop working;</li>
 *   <li>role changes made by an administrator take effect on the next request.</li>
 * </ul>
 * It also records each signed-in browser as a {@link UserSession} so users can manage their devices.
 */
public class SessionGuardFilter extends OncePerRequestFilter {
    public static final String SESSION_KEY_ATTR = "hc.sessionKey";
    public static final String REMEMBER_ATTR = "hc.rememberMe";

    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final RememberMeServices rememberMe;
    private final SecurityContextRepository contextRepository;
    private final ObjectMapper json;

    public SessionGuardFilter(UserRepository users, UserSessionRepository sessions, RememberMeServices rememberMe,
                              SecurityContextRepository contextRepository, ObjectMapper json) {
        this.users = users;
        this.sessions = sessions;
        this.rememberMe = rememberMe;
        this.contextRepository = contextRepository;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HcPrincipal principal) {
            Optional<User> user = users.findById(principal.id());
            if (user.isEmpty() || user.get().getStatus() != User.Status.ACTIVE) {
                terminate(request, response, auth, chain, "account_unavailable",
                        "This account is not active. Contact support if you think this is a mistake.");
                return;
            }
            HttpSession session = request.getSession(true);
            String key = (String) session.getAttribute(SESSION_KEY_ATTR);
            if (key == null) {
                key = Tokens.random(24);
                boolean remembered = Boolean.TRUE.equals(session.getAttribute(REMEMBER_ATTR))
                        || auth instanceof RememberMeAuthenticationToken;
                sessions.save(new UserSession(principal.id(), key, Requests.userAgent(request), Requests.ipHint(request), remembered));
                session.setAttribute(SESSION_KEY_ATTR, key);
            } else {
                UserSession record = sessions.findBySessionKey(key).orElse(null);
                if (record == null || record.getRevokedAt() != null || !record.getUserId().equals(principal.id())) {
                    terminate(request, response, auth, chain, "session_ended", "This session was signed out. Please sign in again.");
                    return;
                }
                if (record.getLastSeenAt().isBefore(Instant.now().minus(Duration.ofMinutes(1)))) {
                    record.seen();
                    sessions.save(record);
                }
            }
            User u = user.get();
            if (u.getRole() != principal.role() || !u.getEmail().equals(principal.email())) {
                HcPrincipal fresh = HcPrincipal.of(u);
                SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                ctx.setAuthentication(new UsernamePasswordAuthenticationToken(fresh, null, fresh.getAuthorities()));
                SecurityContextHolder.setContext(ctx);
                contextRepository.saveContext(ctx, request, response);
            }
        }
        chain.doFilter(request, response);
    }

    private void terminate(HttpServletRequest request, HttpServletResponse response, Authentication auth,
                           FilterChain chain, String code, String message) throws IOException, ServletException {
        if (rememberMe instanceof LogoutHandler handler) handler.logout(request, response, auth);
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        if (request.getRequestURI().startsWith("/api/") && !request.getRequestURI().startsWith("/api/auth/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(response.getOutputStream(), GlobalExceptionHandler.body(code, message, null));
            return;
        }
        chain.doFilter(request, response);
    }
}
