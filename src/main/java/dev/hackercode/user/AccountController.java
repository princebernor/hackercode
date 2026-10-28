package dev.hackercode.user;

import dev.hackercode.activity.ActivityRepository;
import dev.hackercode.activity.Notification;
import dev.hackercode.activity.NotificationRepository;
import dev.hackercode.admin.AuditService;
import dev.hackercode.auth.AuthController.UserView;
import dev.hackercode.auth.AuthService;
import dev.hackercode.auth.SessionGuardFilter;
import dev.hackercode.auth.UserSession;
import dev.hackercode.auth.UserSessionRepository;
import dev.hackercode.common.ApiException;
import dev.hackercode.execution.ExecutionRunRepository;
import dev.hackercode.progress.CodeDraftRepository;
import dev.hackercode.progress.ProblemProgressRepository;
import dev.hackercode.review.CodeReviewRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Everything under Settings: profile, security, preferences, notifications, privacy and data. */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    public record ProfileUpdate(@NotBlank(message = "Display name is required") @Size(max = 80, message = "Name is too long") String displayName,
                                @Size(max = 1024) @Pattern(regexp = "^$|^https://.+", message = "Avatar must be an https:// image URL") String avatarUrl) {}

    public record PasswordChange(@Size(max = 128) String currentPassword, @NotBlank(message = "New password is required") @Size(max = 128) String newPassword) {}

    public record DeleteRequest(@Size(max = 128) String password, @NotBlank String confirm) {}

    public record SessionView(Long id, String userAgent, String ipHint, boolean rememberMe, Instant createdAt, Instant lastSeenAt, boolean current) {}

    private final UserRepository users;
    private final PreferencesService preferences;
    private final AuthService auth;
    private final UserSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final NotificationRepository notifications;
    private final ProblemProgressRepository progress;
    private final CodeDraftRepository drafts;
    private final CodeReviewRepository reviews;
    private final ExecutionRunRepository runs;
    private final ActivityRepository activity;
    private final PersistentTokenRepository rememberMeTokens;

    public AccountController(UserRepository users, PreferencesService preferences, AuthService auth, UserSessionRepository sessions,
                             PasswordEncoder encoder, AuditService audit, NotificationRepository notifications, ProblemProgressRepository progress,
                             CodeDraftRepository drafts, CodeReviewRepository reviews, ExecutionRunRepository runs, ActivityRepository activity,
                             PersistentTokenRepository rememberMeTokens) {
        this.users = users;
        this.preferences = preferences;
        this.auth = auth;
        this.sessions = sessions;
        this.encoder = encoder;
        this.audit = audit;
        this.notifications = notifications;
        this.progress = progress;
        this.drafts = drafts;
        this.reviews = reviews;
        this.runs = runs;
        this.activity = activity;
        this.rememberMeTokens = rememberMeTokens;
    }

    private User me() {
        return users.findById(CurrentUser.id()).orElseThrow(() -> ApiException.notFound("User"));
    }

    private static String currentSessionKey(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s == null ? null : (String) s.getAttribute(SessionGuardFilter.SESSION_KEY_ATTR);
    }

    @PatchMapping("/profile")
    @Transactional
    public UserView updateProfile(@Valid @RequestBody ProfileUpdate body) {
        User u = me();
        u.setDisplayName(body.displayName().trim());
        u.setAvatarUrl(body.avatarUrl() == null || body.avatarUrl().isBlank() ? null : body.avatarUrl().trim());
        u.touch();
        return UserView.of(u);
    }

    @PostMapping("/onboarding")
    @Transactional
    public UserView completeOnboarding(@RequestBody(required = false) UserPreferences prefs) {
        User u = me();
        if (prefs != null) preferences.save(u.getId(), prefs);
        u.setOnboardingCompleted(true);
        u.touch();
        return UserView.of(u);
    }

    @GetMapping("/preferences")
    public UserPreferences getPreferences() {
        return preferences.get(CurrentUser.id());
    }

    @PutMapping("/preferences")
    public UserPreferences savePreferences(@RequestBody UserPreferences prefs) {
        return preferences.save(CurrentUser.id(), prefs);
    }

    @PostMapping("/preferences/reset")
    public UserPreferences resetPreferences() {
        return preferences.save(CurrentUser.id(), UserPreferences.defaults());
    }

    @PostMapping("/password")
    public Map<String, Object> changePassword(@Valid @RequestBody PasswordChange body, HttpServletRequest request) {
        auth.changePassword(CurrentUser.id(), body.currentPassword(), body.newPassword(), currentSessionKey(request));
        return Map.of("ok", true, "message", "Password updated. Other devices were signed out.");
    }

    @GetMapping("/sessions")
    public List<SessionView> listSessions(HttpServletRequest request) {
        String current = currentSessionKey(request);
        return sessions.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(CurrentUser.id()).stream()
                .map(s -> new SessionView(s.getId(), s.getUserAgent(), s.getIpHint(), s.isRememberMe(), s.getCreatedAt(), s.getLastSeenAt(),
                        Objects.equals(s.getSessionKey(), current)))
                .toList();
    }

    @DeleteMapping("/sessions/{id}")
    @Transactional
    public Map<String, Object> revokeSession(@PathVariable Long id) {
        UserSession s = sessions.findById(id).filter(x -> x.getUserId().equals(CurrentUser.id()))
                .orElseThrow(() -> ApiException.notFound("Session"));
        s.revoke();
        audit.record("auth.session_revoked", "session", id, null);
        return Map.of("revoked", true);
    }

    @PostMapping("/sessions/revoke-others")
    public Map<String, Object> revokeOthers(HttpServletRequest request) {
        String key = currentSessionKey(request);
        int n = auth.signOutEverywhere(me(), key == null ? "" : key);
        audit.record("auth.sessions_revoked", "user", CurrentUser.id(), n + " other session(s)");
        return Map.of("revoked", n);
    }

    @GetMapping("/notifications")
    public Map<String, Object> listNotifications() {
        Long id = CurrentUser.id();
        return Map.of("items", notifications.findTop30ByUserIdOrderByCreatedAtDesc(id), "unread", notifications.countByUserIdAndReadAtIsNull(id));
    }

    @PostMapping("/notifications/read-all")
    @Transactional
    public Map<String, Object> readAll() {
        List<Notification> unread = notifications.findByUserIdAndReadAtIsNull(CurrentUser.id());
        unread.forEach(Notification::markRead);
        return Map.of("marked", unread.size());
    }

    /** Downloads everything stored about the user as JSON (no password hash or tokens). */
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export() {
        User u = me();
        Long id = u.getId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", Instant.now());
        data.put("account", UserView.of(u));
        data.put("preferences", preferences.get(id));
        data.put("progress", progress.findByUserId(id).stream().map(p -> Map.of(
                "problemId", p.getProblemId(), "status", p.getStatus(), "bookmarked", p.isBookmarked(),
                "notes", p.getNotes() == null ? "" : p.getNotes(), "updatedAt", p.getUpdatedAt())).toList());
        data.put("drafts", drafts.findByUserId(id).stream().map(d -> Map.of(
                "key", d.getDraftKey(), "language", d.getLanguage(), "code", d.getCode(), "updatedAt", d.getUpdatedAt())).toList());
        data.put("submissions", runs.findByUserId(id).stream().filter(r -> "SUBMIT".equals(r.getKind())).map(r -> Map.of(
                "problemId", String.valueOf(r.getProblemId()), "language", r.getLanguage(), "status", r.getStatus(),
                "code", r.getCode() == null ? "" : r.getCode(), "createdAt", r.getCreatedAt())).toList());
        data.put("reviews", reviews.findByUserId(id).stream().map(r -> Map.of(
                "id", r.getId(), "language", r.getLanguage(), "aiGenerated", r.isAiGenerated(), "summary", String.valueOf(r.getSummary()),
                "code", r.getCode(), "createdAt", r.getCreatedAt())).toList());
        audit.record("account.exported", "user", id, null);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"hackercode-export.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(data);
    }

    /**
     * Permanently deletes the account and all of its data (cascading foreign keys). Requires typing DELETE and,
     * for password accounts, the current password.
     */
    @PostMapping("/delete")
    @Transactional
    public Map<String, Object> deleteAccount(@Valid @RequestBody DeleteRequest body, HttpServletRequest request, HttpServletResponse response) {
        User u = me();
        if (!"DELETE".equals(body.confirm())) throw ApiException.badRequest("confirmation_required", "Type DELETE to confirm.");
        if (u.getPasswordHash() != null && (body.password() == null || !encoder.matches(body.password(), u.getPasswordHash()))) {
            throw ApiException.badRequest("wrong_password", "Your password is incorrect.");
        }
        if (u.getRole() == Role.ADMIN && users.countByRole(Role.ADMIN) <= 1) {
            throw ApiException.conflict("last_admin", "You are the only administrator. Promote another admin before deleting this account.");
        }
        rememberMeTokens.removeUserTokens(u.getEmail());
        audit.recordAs(u.getId(), u.getEmail(), "account.deleted", "user", u.getId(), "self-service deletion");
        users.delete(u);
        HttpSession s = request.getSession(false);
        if (s != null) s.invalidate();
        SecurityContextHolder.clearContext();
        return Map.of("deleted", true);
    }
}
