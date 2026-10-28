package dev.hackercode.auth;

import dev.hackercode.activity.Notification;
import dev.hackercode.activity.NotificationRepository;
import dev.hackercode.admin.AuditService;
import dev.hackercode.admin.PlatformSettingsService;
import dev.hackercode.common.ApiException;
import dev.hackercode.common.Tokens;
import dev.hackercode.user.HcPrincipal;
import dev.hackercode.user.PreferencesService;
import dev.hackercode.user.User;
import dev.hackercode.user.UserPreferences;
import dev.hackercode.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Duration VERIFY_TTL = Duration.ofHours(24);
    private static final Duration RESET_TTL = Duration.ofMinutes(30);

    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final UserSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final SecurityContextRepository contextRepository;
    private final RememberMeServices rememberMe;
    private final PersistentTokenRepository rememberMeTokens;
    private final MailService mail;
    private final AuditService audit;
    private final PreferencesService preferences;
    private final NotificationRepository notifications;
    private final PlatformSettingsService platform;

    public AuthService(UserRepository users, AuthTokenRepository tokens, UserSessionRepository sessions, PasswordEncoder encoder,
                       AuthenticationManager authManager, SecurityContextRepository contextRepository, RememberMeServices rememberMe,
                       PersistentTokenRepository rememberMeTokens, MailService mail, AuditService audit,
                       PreferencesService preferences, NotificationRepository notifications, PlatformSettingsService platform) {
        this.users = users;
        this.tokens = tokens;
        this.sessions = sessions;
        this.encoder = encoder;
        this.authManager = authManager;
        this.contextRepository = contextRepository;
        this.rememberMe = rememberMe;
        this.rememberMeTokens = rememberMeTokens;
        this.mail = mail;
        this.audit = audit;
        this.preferences = preferences;
        this.notifications = notifications;
        this.platform = platform;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public User signUp(String rawEmail, String password, String displayName) {
        if (!platform.flag(PlatformSettingsService.SIGNUP_ENABLED)) {
            throw ApiException.forbidden("New registrations are currently closed.");
        }
        String email = normalizeEmail(rawEmail);
        PasswordPolicy.check(password, email);
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw ApiException.conflict("email_taken", "An account with this email already exists. Try signing in instead.");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(password));
        user.setDisplayName(displayName == null || displayName.isBlank() ? defaultName(email) : displayName.trim());
        users.save(user);
        afterAccountCreated(user);
        sendVerification(user);
        audit.recordAs(user.getId(), email, "auth.signup", "user", user.getId(), "method=password");
        return user;
    }

    /** Shared by password and Google registration. */
    public void afterAccountCreated(User user) {
        preferences.save(user.getId(), UserPreferences.defaults());
        if (platform.flag(PlatformSettingsService.NOTIFY_WELCOME)) {
            notifications.save(new Notification(user.getId(), "Welcome to HackerCode",
                    "Start with the onboarding checklist, then pick a topic from Practice. Your progress is saved automatically."));
        }
    }

    static String defaultName(String email) {
        String local = email.split("@")[0].replaceAll("[^A-Za-z0-9]+", " ").trim();
        if (local.isEmpty()) return "Coder";
        String name = Character.toUpperCase(local.charAt(0)) + local.substring(1);
        return name.length() > 60 ? name.substring(0, 60) : name;
    }

    /** Authenticates with email + password and establishes a fresh session (new session id; optional remember-me). */
    @Transactional
    public User login(String rawEmail, String password, boolean remember, HttpServletRequest request, HttpServletResponse response) {
        String email = normalizeEmail(rawEmail);
        try {
            authManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        } catch (DisabledException | LockedException e) {
            audit.recordAs(null, email, "auth.login_blocked", "user", null, "account not active");
            throw new ApiException(HttpStatus.FORBIDDEN, "account_suspended", "This account is suspended. Contact support if you think this is a mistake.");
        } catch (AuthenticationException e) {
            audit.recordAs(null, email, "auth.login_failed", "user", null, null);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Incorrect email or password.");
        }
        User user = users.findByEmailIgnoreCase(email).orElseThrow(() -> new BadCredentialsException("vanished"));
        establishSession(user, remember, request, response);
        audit.recordAs(user.getId(), email, "auth.login", "user", user.getId(), remember ? "remember-me" : null);
        return user;
    }

    public void establishSession(User user, boolean remember, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId(); // defeat session fixation
        }
        request.getSession(true).setAttribute(SessionGuardFilter.REMEMBER_ATTR, remember);
        HcPrincipal principal = HcPrincipal.of(user);
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contextRepository.saveContext(ctx, request, response);
        if (remember) {
            request.setAttribute(HcRememberMeServices.REQUESTED_ATTR, Boolean.TRUE);
            rememberMe.loginSuccess(request, response, auth);
        }
        user.setLastLoginAt(Instant.now());
        users.save(user);
    }

    @Transactional
    public void sendVerification(User user) {
        if (user.isEmailVerified()) return;
        tokens.invalidateAll(user.getId(), AuthToken.Type.VERIFY_EMAIL, Instant.now());
        String token = Tokens.random(32);
        tokens.save(new AuthToken(user.getId(), AuthToken.Type.VERIFY_EMAIL, Tokens.sha256(token), Instant.now().plus(VERIFY_TTL)));
        mail.send(user.getEmail(), "Verify your HackerCode email",
                "Hi " + user.getDisplayName() + ",\n\nConfirm your email address to unlock code execution and AI reviews:\n\n"
                        + mail.link("/verify-email?token=" + token)
                        + "\n\nThis link expires in 24 hours. If you did not create a HackerCode account, ignore this email.");
    }

    @Transactional
    public User verifyEmail(String token) {
        AuthToken t = tokens.findByTokenHashAndType(Tokens.sha256(token == null ? "" : token), AuthToken.Type.VERIFY_EMAIL)
                .filter(AuthToken::isUsable)
                .orElseThrow(() -> ApiException.badRequest("invalid_token", "This verification link is invalid or has expired."));
        t.markUsed();
        User user = users.findById(t.getUserId()).orElseThrow(() -> ApiException.notFound("User"));
        user.setEmailVerified(true);
        user.touch();
        audit.recordAs(user.getId(), user.getEmail(), "auth.email_verified", "user", user.getId(), null);
        return user;
    }

    /** Always succeeds from the caller's point of view, so the endpoint cannot be used to discover accounts. */
    @Transactional
    public void forgotPassword(String rawEmail) {
        users.findByEmailIgnoreCase(normalizeEmail(rawEmail)).filter(u -> u.getStatus() == User.Status.ACTIVE).ifPresent(user -> {
            tokens.invalidateAll(user.getId(), AuthToken.Type.RESET_PASSWORD, Instant.now());
            String token = Tokens.random(32);
            tokens.save(new AuthToken(user.getId(), AuthToken.Type.RESET_PASSWORD, Tokens.sha256(token), Instant.now().plus(RESET_TTL)));
            mail.send(user.getEmail(), "Reset your HackerCode password",
                    "Someone asked to reset the password for this HackerCode account.\n\nChoose a new password here:\n\n"
                            + mail.link("/reset-password?token=" + token)
                            + "\n\nThe link expires in 30 minutes and works once. If this was not you, you can ignore this email.");
            audit.recordAs(user.getId(), user.getEmail(), "auth.reset_requested", "user", user.getId(), null);
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        AuthToken t = tokens.findByTokenHashAndType(Tokens.sha256(token == null ? "" : token), AuthToken.Type.RESET_PASSWORD)
                .filter(AuthToken::isUsable)
                .orElseThrow(() -> ApiException.badRequest("invalid_token", "This reset link is invalid or has expired. Request a new one."));
        User user = users.findById(t.getUserId()).orElseThrow(() -> ApiException.notFound("User"));
        PasswordPolicy.check(newPassword, user.getEmail());
        t.markUsed();
        user.setPasswordHash(encoder.encode(newPassword));
        user.setEmailVerified(true); // proving control of the inbox also verifies it
        user.touch();
        signOutEverywhere(user, "");
        audit.recordAs(user.getId(), user.getEmail(), "auth.password_reset", "user", user.getId(), null);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword, String keepSessionKey) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getPasswordHash() != null && (currentPassword == null || !encoder.matches(currentPassword, user.getPasswordHash()))) {
            throw ApiException.badRequest("wrong_password", "Your current password is incorrect.");
        }
        PasswordPolicy.check(newPassword, user.getEmail());
        user.setPasswordHash(encoder.encode(newPassword));
        user.touch();
        signOutEverywhere(user, keepSessionKey == null ? "" : keepSessionKey);
        audit.record("auth.password_changed", "user", user.getId(), "other sessions signed out");
    }

    /** Revokes every tracked session except {@code keepSessionKey} and deletes remember-me tokens. */
    @Transactional
    public int signOutEverywhere(User user, String keepSessionKey) {
        rememberMeTokens.removeUserTokens(user.getEmail());
        return sessions.revokeAllExcept(user.getId(), keepSessionKey, Instant.now());
    }
}
