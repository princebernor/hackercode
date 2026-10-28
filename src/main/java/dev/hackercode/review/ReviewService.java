package dev.hackercode.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hackercode.activity.ActivityEvent;
import dev.hackercode.activity.ActivityService;
import dev.hackercode.admin.PlatformSettingsService;
import dev.hackercode.common.ApiException;
import dev.hackercode.config.AppProperties;
import dev.hackercode.config.RateLimiter;
import dev.hackercode.execution.Language;
import dev.hackercode.problem.Problem;
import dev.hackercode.problem.ProblemCatalog;
import dev.hackercode.user.PreferencesService;
import dev.hackercode.user.User;
import dev.hackercode.user.UserPreferences;
import dev.hackercode.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Combines the AI reviewer (when configured and enabled) with the always-available static checks. The response
 * states exactly which parts are AI-generated, and a review is never presented as an execution or test result.
 */
@Service
public class ReviewService {
    public static final int MAX_CODE = 50_000;

    public record AiSection(boolean available, boolean generated, String provider, String model, String notice,
                            String errorCode, ReviewReport report) {}

    public record Response(Long id, String language, String problemId, Instant createdAt, AiSection ai, StaticAnalyzer.Result staticChecks,
                           String disclaimer) {}

    private final List<AiReviewer> reviewers;
    private final AppProperties.Review cfg;
    private final StaticAnalyzer analyzer;
    private final PlatformSettingsService platform;
    private final RateLimiter limiter;
    private final UserRepository users;
    private final PreferencesService preferences;
    private final ProblemCatalog catalog;
    private final CodeReviewRepository reviews;
    private final ActivityService activity;
    private final ObjectMapper json;

    public ReviewService(List<AiReviewer> reviewers, AppProperties props, StaticAnalyzer analyzer, PlatformSettingsService platform,
                         RateLimiter limiter, UserRepository users, PreferencesService preferences, ProblemCatalog catalog,
                         CodeReviewRepository reviews, ActivityService activity, ObjectMapper json) {
        this.reviewers = reviewers;
        this.cfg = props.review();
        this.analyzer = analyzer;
        this.platform = platform;
        this.limiter = limiter;
        this.users = users;
        this.preferences = preferences;
        this.catalog = catalog;
        this.reviews = reviews;
        this.activity = activity;
        this.json = json;
    }

    public AiReviewer active() {
        String wanted = cfg.provider() == null ? "" : cfg.provider().trim().toLowerCase();
        return reviewers.stream().filter(r -> r.name().equals(wanted) && r.configured()).findFirst().orElse(null);
    }

    public Map<String, Object> status() {
        AiReviewer r = active();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("provider", cfg.provider());
        out.put("configured", r != null);
        out.put("enabled", platform.flag(PlatformSettingsService.AI_REVIEW_ENABLED));
        if (r != null) out.put("model", r.model());
        else out.put("setupHint", reviewers.stream().map(AiReviewer::setupHint).findFirst().orElse("No AI provider is available."));
        return out;
    }

    public Response review(Long userId, Language language, String code, String problemId) {
        if (code == null || code.isBlank()) throw ApiException.badRequest("empty_code", "Paste or write some code to review.");
        if (code.length() > MAX_CODE) throw ApiException.badRequest("code_too_large", "Reviews are limited to 50,000 characters of code.");
        Problem problem = problemId == null ? null : catalog.find(problemId).orElseThrow(() -> ApiException.notFound("Problem"));
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        UserPreferences prefs = preferences.get(userId);

        StaticAnalyzer.Result checks = analyzer.analyze(language, code);

        AiReviewer reviewer = active();
        boolean enabled = platform.flag(PlatformSettingsService.AI_REVIEW_ENABLED);
        AiSection ai;
        if (!enabled) {
            ai = new AiSection(false, false, null, null, "AI reviews are temporarily disabled by an administrator. Static checks are shown below.", "ai_disabled", null);
        } else if (reviewer == null) {
            ai = new AiSection(false, false, null, null,
                    "No AI provider is configured on this server, so no AI review was generated. Static checks are shown below.", "ai_not_configured", null);
        } else if (!user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "email_unverified", "Verify your email address to request AI reviews.");
        } else {
            int perHour = platform.number(PlatformSettingsService.RATE_REVIEW);
            if (!limiter.tryAcquire("review:" + userId, perHour, Duration.ofHours(1))) {
                throw ApiException.tooMany("You have reached the hourly AI review limit. Try again later.");
            }
            UserPreferences.Review rp = prefs.review();
            AiReviewer.Outcome outcome = reviewer.review(new AiReviewer.Request(language, code, problem == null ? null : problem.getTitle(),
                    rp.detailLevel(), rp.categories(), rp.explainForBeginners(), rp.includeRefactorSuggestion()));
            ai = outcome.succeeded()
                    ? new AiSection(true, true, reviewer.name(), outcome.model(),
                    "AI-generated review. It can miss problems or be wrong; verify suggestions before relying on them.", null, outcome.report())
                    : new AiSection(true, false, reviewer.name(), null, outcome.failureMessage(), outcome.failureCode(), null);
        }

        String disclaimer = "This is a review, not an execution: no code was run and no tests were checked.";
        Long id = null;
        Instant created = Instant.now();
        if (prefs.privacy().keepReviewHistory()) {
            String body;
            try {
                body = json.writeValueAsString(Map.of("ai", ai, "staticChecks", checks));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
            ReviewReport r = ai.report();
            CodeReview saved = reviews.save(new CodeReview(userId, problem == null ? null : problem.getId(), language.id(),
                    ai.generated() ? ai.provider() : "static", ai.model(), ai.generated(),
                    r != null ? r.summary() : summaryOf(checks), r != null ? r.score() : null,
                    (r != null && r.findings() != null ? r.findings().size() : 0) + checks.findings().size(), body, code));
            id = saved.getId();
            created = saved.getCreatedAt();
        }
        activity.record(userId, ActivityEvent.Type.REVIEW_REQUESTED, problem == null ? null : problem.getId(), language.id());
        return new Response(id, language.id(), problem == null ? null : problem.getId(), created, ai, checks, disclaimer);
    }

    private static String summaryOf(StaticAnalyzer.Result checks) {
        return checks.findings().isEmpty() ? "Static checks found no issues." : "Static checks found " + checks.findings().size() + " potential issue(s).";
    }
}
