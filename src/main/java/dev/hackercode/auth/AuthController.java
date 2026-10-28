package dev.hackercode.auth;

import dev.hackercode.admin.PlatformSettingsService;
import dev.hackercode.common.ApiException;
import dev.hackercode.config.AppProperties;
import dev.hackercode.user.CurrentUser;
import dev.hackercode.user.HcPrincipal;
import dev.hackercode.user.PreferencesService;
import dev.hackercode.user.User;
import dev.hackercode.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record SignUpRequest(
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 320) String email,
            @NotBlank(message = "Password is required") @Size(max = 128) String password,
            @Size(max = 80, message = "Name is too long") String displayName,
            @AssertTrue(message = "You must accept the Terms and Privacy Policy") boolean acceptTerms) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required") @Size(max = 320) String email,
            @NotBlank(message = "Password is required") @Size(max = 128) String password,
            boolean rememberMe) {}

    public record TokenRequest(@NotBlank @Size(max = 200) String token) {}

    public record EmailRequest(@NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 320) String email) {}

    public record ResetRequest(@NotBlank @Size(max = 200) String token, @NotBlank(message = "Password is required") @Size(max = 128) String password) {}

    private final AuthService auth;
    private final UserRepository users;
    private final PreferencesService preferences;
    private final MailService mail;
    private final PlatformSettingsService platform;
    private final ObjectProvider<ClientRegistrationRepository> google;
    private final AppProperties props;

    public AuthController(AuthService auth, UserRepository users, PreferencesService preferences, MailService mail,
                          PlatformSettingsService platform, ObjectProvider<ClientRegistrationRepository> google, AppProperties props) {
        this.auth = auth;
        this.users = users;
        this.preferences = preferences;
        this.mail = mail;
        this.platform = platform;
        this.google = google;
        this.props = props;
    }

    /** Public capabilities the sign-in pages need: which providers work and whether email can be delivered. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("googleEnabled", google.getIfAvailable() != null);
        out.put("googleSetupHint", "Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET on the server to enable Google sign-in.");
        out.put("signupEnabled", platform.flag(PlatformSettingsService.SIGNUP_ENABLED));
        out.put("emailDelivery", mail.mode().name().toLowerCase());
        out.put("maintenance", Map.of(
                "enabled", platform.flag(PlatformSettingsService.MAINTENANCE),
                "message", platform.get(PlatformSettingsService.MAINTENANCE_MESSAGE)));
        return out;
    }

    /** The signed-in user, or {@code {"user": null}}. Also primes the XSRF-TOKEN cookie for the SPA. */
    @GetMapping("/me")
    public Map<String, Object> me() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof HcPrincipal p)) {
            Map<String, Object> anon = new LinkedHashMap<>();
            anon.put("user", null);
            return anon;
        }
        User user = users.findById(p.id()).orElseThrow(() -> ApiException.notFound("User"));
        return Map.of("user", UserView.of(user), "preferences", preferences.get(user.getId()));
    }

    @PostMapping("/signup")
    public Map<String, Object> signUp(@Valid @RequestBody SignUpRequest req, HttpServletRequest request, HttpServletResponse response) {
        User user = auth.signUp(req.email(), req.password(), req.displayName());
        auth.establishSession(user, false, request, response);
        return Map.of("user", UserView.of(user), "verificationSent", mail.mode() != MailService.Mode.DISABLED,
                "emailDelivery", mail.mode().name().toLowerCase());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        User user = auth.login(req.email(), req.password(), req.rememberMe(), request, response);
        return Map.of("user", UserView.of(user), "preferences", preferences.get(user.getId()));
    }

    @PostMapping("/verify-email")
    public Map<String, Object> verify(@Valid @RequestBody TokenRequest req) {
        User user = auth.verifyEmail(req.token());
        return Map.of("verified", true, "email", user.getEmail());
    }

    @PostMapping("/resend-verification")
    public Map<String, Object> resend() {
        User user = users.findById(CurrentUser.id()).orElseThrow(() -> ApiException.notFound("User"));
        if (user.isEmailVerified()) return Map.of("sent", false, "alreadyVerified", true);
        auth.sendVerification(user);
        return Map.of("sent", mail.mode() != MailService.Mode.DISABLED, "emailDelivery", mail.mode().name().toLowerCase());
    }

    @PostMapping("/forgot-password")
    public Map<String, Object> forgot(@Valid @RequestBody EmailRequest req) {
        auth.forgotPassword(req.email());
        return Map.of("ok", true, "emailDelivery", mail.mode().name().toLowerCase(),
                "message", "If an account exists for that email, a reset link is on its way.");
    }

    @PostMapping("/reset-password")
    public Map<String, Object> reset(@Valid @RequestBody ResetRequest req) {
        auth.resetPassword(req.token(), req.password());
        return Map.of("ok", true);
    }

    /** Starts Google sign-in, remembering a validated same-site path to return to afterwards. */
    @GetMapping("/google/start")
    public void googleStart(@RequestParam(required = false) String next, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (google.getIfAvailable() == null) {
            response.sendRedirect("/signin?error=google_not_configured");
            return;
        }
        request.getSession(true).setAttribute(GoogleLoginHandler.NEXT_ATTR, SafeRedirect.path(next, "/dashboard"));
        response.sendRedirect("/oauth2/authorization/google");
    }

    public record UserView(Long id, String email, String displayName, String avatarUrl, String role, boolean emailVerified,
                           boolean hasPassword, boolean googleLinked, boolean onboardingCompleted, Instant createdAt) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getEmail(), u.getDisplayName(), u.getAvatarUrl(), u.getRole().name(), u.isEmailVerified(),
                    u.getPasswordHash() != null, u.getGoogleSubject() != null, u.isOnboardingCompleted(), u.getCreatedAt());
        }
    }
}
