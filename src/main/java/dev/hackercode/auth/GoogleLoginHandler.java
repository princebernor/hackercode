package dev.hackercode.auth;

import dev.hackercode.admin.AuditService;
import dev.hackercode.admin.PlatformSettingsService;
import dev.hackercode.user.User;
import dev.hackercode.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns a successful Google sign-in into a HackerCode session: finds the account by Google subject, links an
 * existing account with the same verified email, or creates a new one. Unverified Google emails are refused.
 */
@Component
public class GoogleLoginHandler implements AuthenticationSuccessHandler {
    public static final String NEXT_ATTR = "hc.oauthNext";

    private final UserRepository users;
    private final AuthService authService;
    private final AuditService audit;
    private final PlatformSettingsService platform;
    private final TransactionTemplate tx;

    public GoogleLoginHandler(UserRepository users, AuthService authService, AuditService audit,
                              PlatformSettingsService platform, TransactionTemplate tx) {
        this.users = users;
        this.authService = authService;
        this.audit = audit;
        this.platform = platform;
        this.tx = tx;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        if (!(authentication.getPrincipal() instanceof OidcUser oidc)) {
            fail(request, response, "oauth");
            return;
        }
        String email = AuthService.normalizeEmail(oidc.getEmail());
        if (email == null || !Boolean.TRUE.equals(oidc.getEmailVerified())) {
            fail(request, response, "google_unverified");
            return;
        }
        String[] error = {null};
        User user = tx.execute(status -> {
            User found = users.findByGoogleSubject(oidc.getSubject()).orElse(null);
            if (found == null) {
                found = users.findByEmailIgnoreCase(email).orElse(null);
                if (found != null) {
                    found.setGoogleSubject(oidc.getSubject());
                    found.setEmailVerified(true);
                    found.touch();
                    audit.recordAs(found.getId(), email, "auth.google_linked", "user", found.getId(), null);
                }
            }
            if (found == null) {
                if (!platform.flag(PlatformSettingsService.SIGNUP_ENABLED)) {
                    error[0] = "signup_closed";
                    return null;
                }
                found = new User();
                found.setEmail(email);
                found.setGoogleSubject(oidc.getSubject());
                found.setEmailVerified(true);
                String name = oidc.getFullName();
                found.setDisplayName(name == null || name.isBlank() ? AuthService.defaultName(email) : name.length() > 80 ? name.substring(0, 80) : name);
                if (oidc.getPicture() != null && oidc.getPicture().startsWith("https://")) found.setAvatarUrl(oidc.getPicture());
                users.save(found);
                authService.afterAccountCreated(found);
                audit.recordAs(found.getId(), email, "auth.signup", "user", found.getId(), "method=google");
            }
            if (found.getStatus() != User.Status.ACTIVE) {
                error[0] = "suspended";
                return null;
            }
            return found;
        });
        if (user == null) {
            fail(request, response, error[0] == null ? "oauth" : error[0]);
            return;
        }
        HttpSession session = request.getSession(false);
        String next = session == null ? null : (String) session.getAttribute(NEXT_ATTR);
        if (session != null) session.removeAttribute(NEXT_ATTR);
        tx.executeWithoutResult(s -> authService.establishSession(users.findById(user.getId()).orElseThrow(), false, request, response));
        audit.recordAs(user.getId(), email, "auth.login", "user", user.getId(), "method=google");
        String target = !user.isOnboardingCompleted() ? "/onboarding" : SafeRedirect.path(next, "/dashboard");
        response.sendRedirect(target);
    }

    private void fail(HttpServletRequest request, HttpServletResponse response, String code) throws IOException {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        response.sendRedirect("/signin?error=" + code);
    }
}
