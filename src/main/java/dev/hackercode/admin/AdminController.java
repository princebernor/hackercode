package dev.hackercode.admin;

import dev.hackercode.activity.ActivityEvent;
import dev.hackercode.activity.ActivityRepository;
import dev.hackercode.auth.AuthService;
import dev.hackercode.auth.MailService;
import dev.hackercode.auth.UserSessionRepository;
import dev.hackercode.common.ApiException;
import dev.hackercode.common.PageResponse;
import dev.hackercode.execution.ExecutionRunRepository;
import dev.hackercode.execution.ExecutionService;
import dev.hackercode.execution.Language;
import dev.hackercode.problem.Problem;
import dev.hackercode.problem.ProblemCatalog;
import dev.hackercode.problem.ProblemImporter;
import dev.hackercode.problem.ProblemService;
import dev.hackercode.problem.ProblemViews.Summary;
import dev.hackercode.problem.Topic;
import dev.hackercode.progress.ProblemProgress;
import dev.hackercode.progress.ProblemProgressRepository;
import dev.hackercode.review.CodeReviewRepository;
import dev.hackercode.review.ReviewService;
import dev.hackercode.user.CurrentUser;
import dev.hackercode.user.Role;
import dev.hackercode.user.User;
import dev.hackercode.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator console API. The URL rule in SecurityConfig admits MODERATOR and ADMIN; endpoints that change
 * accounts, platform configuration or read the audit log additionally require ADMIN via {@link PreAuthorize}.
 * Responses never include password hashes, tokens, secrets or users' source code.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
public class AdminController {

    public record UserRow(Long id, String email, String displayName, String role, String status, boolean emailVerified,
                          boolean googleLinked, boolean hasPassword, Instant createdAt, Instant lastLoginAt) {
        static UserRow of(User u) {
            return new UserRow(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole().name(), u.getStatus().name(), u.isEmailVerified(),
                    u.getGoogleSubject() != null, u.getPasswordHash() != null, u.getCreatedAt(), u.getLastLoginAt());
        }
    }

    public record StatusChange(@NotNull User.Status status, @Size(max = 300) String reason) {}

    public record RoleChange(@NotNull Role role) {}

    public record ArchiveRequest(boolean archived) {}

    public record SettingChange(@NotNull @Size(max = 2000) String value) {}

    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final AuthService auth;
    private final AuditService audit;
    private final AuditRepository auditRepo;
    private final PlatformSettingsService platform;
    private final ProblemService problemService;
    private final ProblemCatalog catalog;
    private final ProblemImporter importer;
    private final AdminProblemService adminProblems;
    private final ProblemProgressRepository progress;
    private final ExecutionRunRepository runs;
    private final CodeReviewRepository reviews;
    private final ActivityRepository activity;
    private final ExecutionService execution;
    private final ReviewService reviewService;
    private final MailService mail;
    private final ObjectProvider<ClientRegistrationRepository> google;
    private final RetentionJob retention;
    private final DataSource dataSource;

    public AdminController(UserRepository users, UserSessionRepository sessions, AuthService auth, AuditService audit, AuditRepository auditRepo,
                           PlatformSettingsService platform, ProblemService problemService, ProblemCatalog catalog, ProblemImporter importer,
                           AdminProblemService adminProblems, ProblemProgressRepository progress, ExecutionRunRepository runs,
                           CodeReviewRepository reviews, ActivityRepository activity, ExecutionService execution, ReviewService reviewService,
                           MailService mail, ObjectProvider<ClientRegistrationRepository> google, RetentionJob retention, DataSource dataSource) {
        this.users = users;
        this.sessions = sessions;
        this.auth = auth;
        this.audit = audit;
        this.auditRepo = auditRepo;
        this.platform = platform;
        this.problemService = problemService;
        this.catalog = catalog;
        this.importer = importer;
        this.adminProblems = adminProblems;
        this.progress = progress;
        this.runs = runs;
        this.reviews = reviews;
        this.activity = activity;
        this.execution = execution;
        this.reviewService = reviewService;
        this.mail = mail;
        this.google = google;
        this.retention = retention;
        this.dataSource = dataSource;
    }

    // ---------------------------------------------------------------- overview

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Instant now = Instant.now();
        Instant day = now.minus(Duration.ofDays(1));
        Instant week = now.minus(Duration.ofDays(7));
        Map<String, Object> userStats = new LinkedHashMap<>();
        userStats.put("total", users.count());
        userStats.put("admins", users.countByRole(Role.ADMIN));
        userStats.put("moderators", users.countByRole(Role.MODERATOR));
        userStats.put("signedInLast7Days", users.countByLastLoginAtAfter(week));
        userStats.put("activeLast24h", activity.countDistinctUsersSince(day));
        userStats.put("activeLast7Days", activity.countDistinctUsersSince(week));
        userStats.put("openSessions", sessions.countByLastSeenAtAfterAndRevokedAtIsNull(day));

        Map<String, Object> coding = new LinkedHashMap<>();
        coding.put("runsLast24h", runs.countByCreatedAtAfter(day));
        coding.put("providerErrorsLast24h", runs.countByStatusAndCreatedAtAfter("PROVIDER_ERROR", day));
        coding.put("problemsSolvedTotal", progress.countByStatus(ProblemProgress.Status.SOLVED));
        coding.put("learnersWithProgress", progress.countActiveLearners());
        coding.put("problemsSolvedLast7Days", activity.countByTypeAndCreatedAtAfter(ActivityEvent.Type.PROBLEM_SOLVED, week));

        Map<String, Object> reviewStats = new LinkedHashMap<>();
        reviewStats.put("reviewsLast24h", reviews.countByCreatedAtAfter(day));
        reviewStats.put("aiReviewsLast24h", reviews.countByAiGeneratedTrueAndCreatedAtAfter(day));
        reviewStats.put("reviewsLast7Days", reviews.countByCreatedAtAfter(week));

        ProblemImporter.Report validation = importer.validateDatabase();
        Map<String, Object> library = new LinkedHashMap<>();
        library.put("valid", validation.ok());
        library.put("sourceEntriesPresent", validation.found());
        library.put("expected", validation.expected());
        library.put("issues", validation.issues().size());
        library.put("stats", validation.stats());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("users", userStats);
        out.put("coding", coding);
        out.put("reviews", reviewStats);
        out.put("library", library);
        out.put("health", health());
        out.put("recentEvents", auditRepo.findTop10ByOrderByCreatedAtDesc());
        out.put("generatedAt", now);
        return out;
    }

    private Map<String, Object> health() {
        Map<String, Object> h = new LinkedHashMap<>();
        boolean db;
        try (var c = dataSource.getConnection()) {
            db = c.isValid(2);
        } catch (Exception e) {
            db = false;
        }
        h.put("database", db ? "up" : "down");
        h.put("execution", execution.status());
        h.put("aiReview", reviewService.status());
        h.put("email", mail.mode().name().toLowerCase());
        h.put("googleOAuth", google.getIfAvailable() != null ? "configured" : "not configured");
        h.put("maintenance", platform.flag(PlatformSettingsService.MAINTENANCE));
        return h;
    }

    // ---------------------------------------------------------------- users

    @GetMapping("/users")
    public PageResponse<UserRow> users(@RequestParam(required = false) String q, @RequestParam(required = false) Role role,
                                       @RequestParam(required = false) User.Status status, @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "25") int size) {
        String query = q == null || q.isBlank() ? null : q.trim();
        return PageResponse.of(users.search(query, role, status,
                        PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)), Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(UserRow::of));
    }

    /** Account metadata for support: counts and timestamps only, never code, notes or credentials. */
    @GetMapping("/users/{id}")
    public Map<String, Object> user(@PathVariable Long id) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        var mine = progress.findByUserId(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", UserRow.of(u));
        out.put("solved", mine.stream().filter(p -> p.getStatus() == ProblemProgress.Status.SOLVED).count());
        out.put("attempted", mine.stream().filter(p -> p.getStatus() == ProblemProgress.Status.ATTEMPTED).count());
        out.put("activeSessions", sessions.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(id).stream()
                .map(s -> Map.of("id", s.getId(), "userAgent", String.valueOf(s.getUserAgent()), "lastSeenAt", s.getLastSeenAt())).toList());
        out.put("recentActivity", activity.findTop15ByUserIdOrderByCreatedAtDesc(id).stream()
                .map(a -> Map.of("type", a.getType().name(), "at", a.getCreatedAt())).toList());
        audit.record("admin.user_viewed", "user", id, null);
        return out;
    }

    @PatchMapping("/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserRow changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChange body) {
        if (id.equals(CurrentUser.id())) throw ApiException.badRequest("self_change", "You cannot suspend your own account.");
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (u.getStatus() != body.status()) {
            u.setStatus(body.status());
            u.touch();
            if (body.status() == User.Status.SUSPENDED) auth.signOutEverywhere(u, "");
            audit.record(body.status() == User.Status.SUSPENDED ? "admin.user_suspended" : "admin.user_reactivated", "user", id,
                    body.reason() == null ? null : "reason: " + body.reason());
        }
        return UserRow.of(u);
    }

    @PatchMapping("/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserRow changeRole(@PathVariable Long id, @Valid @RequestBody RoleChange body) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (u.getRole() == body.role()) return UserRow.of(u);
        if (u.getRole() == Role.ADMIN && users.countByRole(Role.ADMIN) <= 1) {
            throw ApiException.conflict("last_admin", "At least one administrator must remain.");
        }
        Role before = u.getRole();
        u.setRole(body.role());
        u.touch();
        audit.record("admin.role_changed", "user", id, before + " -> " + body.role());
        return UserRow.of(u);
    }

    // ---------------------------------------------------------------- problems

    @GetMapping("/topics")
    public List<Map<String, Object>> topics() {
        return catalog.get().topics().stream().map(t -> Map.<String, Object>of("slug", t.getSlug(), "name", t.getName(), "position", t.getPosition())).toList();
    }

    @GetMapping("/problems")
    public dev.hackercode.common.PageResponse<Summary> problems(@RequestParam(required = false) String q, @RequestParam(required = false) String topic,
                                                                 @RequestParam(defaultValue = "true") boolean includeArchived,
                                                                 @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return problemService.search(null, new ProblemService.Query(q, topic, null, null, null, null, "number", includeArchived, page, size));
    }

    @GetMapping("/problems/{id}")
    public Map<String, Object> problem(@PathVariable String id) {
        Problem p = catalog.find(id).orElseThrow(() -> ApiException.notFound("Problem"));
        Topic t = catalog.get().topicsById().get(p.getTopicId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", p.getId());
        out.put("number", p.getSourceNumber());
        out.put("slug", p.getSlug());
        out.put("title", p.getTitle());
        out.put("topicSlug", t.getSlug());
        out.put("subsection", p.getSubsection());
        out.put("provenance", p.getProvenance().name());
        out.put("sourceText", p.getSourceText());
        out.put("sourceNote", p.getSourceNote());
        out.put("practiceUrls", String.join("\n", p.practiceUrlList()));
        out.put("difficulty", p.getDifficulty());
        out.put("statement", p.getStatement());
        out.put("constraints", p.getConstraintsText());
        out.put("examples", p.getExamplesText());
        out.put("testsJson", p.getTestsJson());
        out.put("contentUpdatedBy", p.getContentUpdatedBy());
        out.put("archived", p.isArchived());
        out.put("updatedAt", p.getUpdatedAt());
        return out;
    }

    @PutMapping("/problems/{id}")
    public Map<String, Object> updateProblem(@PathVariable String id, @RequestBody AdminProblemService.ProblemEdit edit) {
        adminProblems.update(id, edit);
        return problem(id);
    }

    @PostMapping("/problems")
    public Map<String, Object> createProblem(@RequestBody AdminProblemService.ProblemEdit edit) {
        return problem(adminProblems.create(edit).getId());
    }

    @PostMapping("/problems/{id}/archive")
    public Map<String, Object> archive(@PathVariable String id, @RequestBody ArchiveRequest body) {
        adminProblems.setArchived(id, body.archived());
        return problem(id);
    }

    @GetMapping("/problems/validation")
    public ProblemImporter.Report validate() {
        ProblemImporter.Report r = importer.validateDatabase();
        audit.record("problem.validation_run", "library", null, r.ok() ? "ok" : r.issues().size() + " issue(s)");
        return r;
    }

    @PostMapping("/problems/import")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> reimport() {
        ProblemImporter.ImportResult r = importer.importMissing();
        audit.record("problem.import_run", "library", null, "created=" + r.problemsCreated() + ", present=" + r.problemsAlreadyPresent());
        return Map.of("result", r, "validation", importer.validateDatabase());
    }

    // ---------------------------------------------------------------- platform configuration

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("settings", platform.describeAll());
        out.put("languages", Arrays.stream(Language.values()).map(l -> Map.of("id", l.id(), "label", l.label(),
                "enabled", platform.enabledLanguages().contains(l.id()), "dockerImage", l.dockerImage())).toList());
        out.put("health", health());
        out.put("retention", Map.of("policy", RetentionJob.POLICY, "lastRun", retention.lastRun()));
        return out;
    }

    @PutMapping("/config/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> setConfig(@PathVariable String key, @Valid @RequestBody SettingChange body) {
        String before = platform.get(key);
        platform.set(key, body.value(), CurrentUser.get().email());
        audit.record("config.changed", "setting", key, before + " -> " + platform.get(key));
        return config();
    }

    // ---------------------------------------------------------------- audit & operations

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AuditEvent> auditLog(@RequestParam(required = false) String category, @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        String prefix = category == null || category.isBlank() ? null : category.trim() + ".";
        return PageResponse.of(auditRepo.search(null, prefix, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)))));
    }

    @GetMapping("/operations")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> operations() {
        Instant day = Instant.now().minus(Duration.ofDays(1));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("failedLoginsLast24h", auditRepo.countByActionStartingWithAndCreatedAtAfter("auth.login_failed", day));
        out.put("blockedLoginsLast24h", auditRepo.countByActionStartingWithAndCreatedAtAfter("auth.login_blocked", day));
        out.put("executionProviderErrors", runs.findTop20ByStatusOrderByCreatedAtDesc("PROVIDER_ERROR").stream()
                .map(r -> Map.of("id", r.getId(), "language", r.getLanguage(), "provider", r.getProvider(), "createdAt", r.getCreatedAt())).toList());
        out.put("retention", Map.of("policy", RetentionJob.POLICY, "lastRun", retention.lastRun()));
        out.put("health", health());
        return out;
    }

    @PostMapping("/operations/retention")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> runRetention() {
        retention.purge();
        audit.record("ops.retention_run", "system", null, null);
        return retention.lastRun();
    }
}
