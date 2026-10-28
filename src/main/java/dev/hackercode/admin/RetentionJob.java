package dev.hackercode.admin;

import dev.hackercode.activity.ActivityRepository;
import dev.hackercode.auth.AuthTokenRepository;
import dev.hackercode.auth.UserSessionRepository;
import dev.hackercode.execution.ExecutionRunRepository;
import dev.hackercode.review.CodeReviewRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data retention. Runs daily:
 * plain code runs 30 days, code reviews 180 days, activity events and audit log 365 days,
 * expired email tokens immediately, inactive device sessions 60 days. Saved submissions are kept until the
 * user deletes their account.
 */
@Component
public class RetentionJob {
    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    public static final Map<String, String> POLICY = Map.of(
            "Code runs", "30 days",
            "Saved submissions", "Until account deletion",
            "Code reviews", "180 days",
            "Activity events", "365 days",
            "Audit log", "365 days",
            "Inactive sessions", "60 days");

    private final ExecutionRunRepository runs;
    private final CodeReviewRepository reviews;
    private final ActivityRepository activity;
    private final AuditRepository audit;
    private final AuthTokenRepository tokens;
    private final UserSessionRepository sessions;
    private volatile Map<String, Object> lastRun = Map.of("status", "not run since startup");

    public RetentionJob(ExecutionRunRepository runs, CodeReviewRepository reviews, ActivityRepository activity, AuditRepository audit,
                        AuthTokenRepository tokens, UserSessionRepository sessions) {
        this.runs = runs;
        this.reviews = reviews;
        this.activity = activity;
        this.audit = audit;
        this.tokens = tokens;
        this.sessions = sessions;
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purge() {
        Instant now = Instant.now();
        try {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("runs", runs.deleteRunsOlderThan(now.minus(Duration.ofDays(30))));
            r.put("reviews", reviews.deleteOlderThan(now.minus(Duration.ofDays(180))));
            r.put("activity", activity.deleteOlderThan(now.minus(Duration.ofDays(365))));
            r.put("audit", audit.deleteOlderThan(now.minus(Duration.ofDays(365))));
            r.put("tokens", tokens.deleteExpired(now));
            r.put("sessions", sessions.deleteStale(now.minus(Duration.ofDays(60))));
            r.put("status", "ok");
            r.put("finishedAt", Instant.now());
            lastRun = r;
            log.info("Retention purge finished: {}", r);
        } catch (RuntimeException e) {
            lastRun = Map.of("status", "failed", "finishedAt", Instant.now(), "error", e.getClass().getSimpleName());
            log.error("Retention purge failed", e);
            throw e;
        }
    }

    public Map<String, Object> lastRun() {
        return lastRun;
    }
}
