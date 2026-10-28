package dev.hackercode.activity;

import dev.hackercode.execution.ExecutionService;
import dev.hackercode.problem.Problem;
import dev.hackercode.problem.ProblemCatalog;
import dev.hackercode.problem.ProblemService;
import dev.hackercode.problem.ProblemViews.Summary;
import dev.hackercode.problem.ProblemViews.TopicStats;
import dev.hackercode.problem.Topic;
import dev.hackercode.progress.ProblemProgress;
import dev.hackercode.progress.ProgressService;
import dev.hackercode.review.CodeReviewRepository;
import dev.hackercode.review.ReviewService;
import dev.hackercode.user.CurrentUser;
import dev.hackercode.user.PreferencesService;
import dev.hackercode.user.UserPreferences;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard data. Every number here is computed from the signed-in user's stored activity; nothing is synthetic. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    static final int HEATMAP_DAYS = 182;

    public record Recommendation(Summary problem, String reason) {}

    public record ActivityItem(String type, String problemId, String problemTitle, String language, Instant at) {}

    private final ProblemService problems;
    private final ProblemCatalog catalog;
    private final ProgressService progress;
    private final ActivityRepository activity;
    private final CodeReviewRepository reviews;
    private final PreferencesService preferences;
    private final ExecutionService execution;
    private final ReviewService reviewService;

    public DashboardController(ProblemService problems, ProblemCatalog catalog, ProgressService progress, ActivityRepository activity,
                               CodeReviewRepository reviews, PreferencesService preferences, ExecutionService execution, ReviewService reviewService) {
        this.problems = problems;
        this.catalog = catalog;
        this.progress = progress;
        this.activity = activity;
        this.reviews = reviews;
        this.preferences = preferences;
        this.execution = execution;
        this.reviewService = reviewService;
    }

    @GetMapping
    public Map<String, Object> dashboard() {
        Long userId = CurrentUser.id();
        ProblemCatalog.Snapshot s = catalog.get();
        Map<String, ProblemProgress> mine = progress.byProblem(userId);
        UserPreferences prefs = preferences.get(userId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<Problem> visible = s.problems().stream().filter(p -> !p.isArchived()).toList();
        long solved = visible.stream().filter(p -> status(mine, p) == ProblemProgress.Status.SOLVED).count();
        long attempted = visible.stream().filter(p -> status(mine, p) == ProblemProgress.Status.ATTEMPTED).count();
        long bookmarked = mine.values().stream().filter(ProblemProgress::isBookmarked).count();

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("problems", visible.size());
        totals.put("solved", solved);
        totals.put("attempted", attempted);
        totals.put("bookmarked", bookmarked);
        totals.put("completionPercent", visible.isEmpty() ? 0 : Math.round(solved * 1000.0 / visible.size()) / 10.0);

        long solvedToday = mine.values().stream().filter(p -> p.getSolvedAt() != null
                && p.getSolvedAt().atZone(ZoneOffset.UTC).toLocalDate().equals(today)).count();
        LocalDate weekStart = today.minusDays(6);
        long solvedThisWeek = mine.values().stream().filter(p -> p.getSolvedAt() != null
                && !p.getSolvedAt().atZone(ZoneOffset.UTC).toLocalDate().isBefore(weekStart)).count();

        Set<LocalDate> activeDays = new HashSet<>(activity.activeDays(userId, today.minusDays(400)));
        Map<String, Object> streak = new LinkedHashMap<>();
        streak.put("current", currentStreak(activeDays, today));
        streak.put("longest", longestStreak(activeDays));
        streak.put("activeToday", activeDays.contains(today));

        List<Map<String, Object>> heatmap = new ArrayList<>();
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        activity.countByDay(userId, today.minusDays(HEATMAP_DAYS - 1)).forEach(d -> counts.put(d.getDay(), d.getCount()));
        for (int i = HEATMAP_DAYS - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            heatmap.add(Map.of("day", d.toString(), "count", counts.getOrDefault(d, 0L)));
        }

        List<ActivityItem> recent = activity.findTop15ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(a -> new ActivityItem(a.getType().name(), a.getProblemId(),
                        a.getProblemId() == null || !s.byId().containsKey(a.getProblemId()) ? null : s.byId().get(a.getProblemId()).getTitle(),
                        a.getLanguage(), a.getCreatedAt()))
                .toList();

        List<Summary> recentlyViewed = mine.values().stream().filter(p -> p.getLastViewedAt() != null && s.byId().containsKey(p.getProblemId()))
                .sorted((a, b) -> b.getLastViewedAt().compareTo(a.getLastViewedAt())).limit(6)
                .map(p -> summary(s, s.byId().get(p.getProblemId()), p)).toList();
        List<Summary> bookmarks = mine.values().stream().filter(p -> p.isBookmarked() && s.byId().containsKey(p.getProblemId()))
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt())).limit(6)
                .map(p -> summary(s, s.byId().get(p.getProblemId()), p)).toList();

        Map<String, Long> languages = new LinkedHashMap<>();
        activity.countByLanguage(userId).forEach(l -> languages.put(l.getLanguage(), l.getCount()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totals", totals);
        out.put("goals", Map.of("dailyGoal", prefs.learning().dailyGoal(), "solvedToday", solvedToday,
                "weeklyGoal", prefs.learning().weeklyGoal(), "solvedThisWeek", solvedThisWeek));
        out.put("streak", streak);
        out.put("heatmap", heatmap);
        out.put("topics", problems.topics(userId));
        out.put("recentActivity", recent);
        out.put("recentlyViewed", recentlyViewed);
        out.put("bookmarks", bookmarks);
        out.put("languages", languages);
        out.put("recentReviews", reviews.findTop5ByUserIdOrderByCreatedAtDesc(userId).stream().map(r -> Map.of(
                "id", r.getId(), "language", r.getLanguage(), "aiGenerated", r.isAiGenerated(),
                "summary", r.getSummary() == null ? "" : r.getSummary(), "findingCount", r.getFindingCount(), "createdAt", r.getCreatedAt())).toList());
        out.put("recommendations", recommend(s, mine, prefs, visible));
        out.put("services", Map.of("execution", execution.status(), "aiReview", reviewService.status()));
        out.put("isNew", mine.isEmpty() && recent.isEmpty());
        return out;
    }

    private static ProblemProgress.Status status(Map<String, ProblemProgress> mine, Problem p) {
        ProblemProgress pr = mine.get(p.getId());
        return pr == null ? ProblemProgress.Status.NOT_STARTED : pr.getStatus();
    }

    private static Summary summary(ProblemCatalog.Snapshot s, Problem p, ProblemProgress pr) {
        return Summary.of(p, s.topicsById().get(p.getTopicId()), pr);
    }

    /**
     * Next problems: continue attempted ones first, then the next unsolved problems in the focus topic (or the most
     * recently practised incomplete topic), then the first problem of the next untouched topic in sheet order.
     */
    private List<Recommendation> recommend(ProblemCatalog.Snapshot s, Map<String, ProblemProgress> mine, UserPreferences prefs, List<Problem> visible) {
        List<Recommendation> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        mine.values().stream().filter(p -> p.getStatus() == ProblemProgress.Status.ATTEMPTED && s.byId().containsKey(p.getProblemId()))
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt())).limit(2)
                .forEach(p -> {
                    out.add(new Recommendation(summary(s, s.byId().get(p.getProblemId()), p), "You started this one. Finish it while it is fresh."));
                    used.add(p.getProblemId());
                });

        Topic focus = null;
        String focusSlug = prefs.learning().focusTopic();
        if (focusSlug != null) focus = s.topicsBySlug().get(focusSlug);
        if (focus == null) {
            focus = mine.values().stream().filter(p -> p.getLastViewedAt() != null && s.byId().containsKey(p.getProblemId()))
                    .sorted((a, b) -> b.getLastViewedAt().compareTo(a.getLastViewedAt()))
                    .map(p -> s.topicsById().get(s.byId().get(p.getProblemId()).getTopicId()))
                    .filter(t -> visible.stream().anyMatch(p -> p.getTopicId().equals(t.getId()) && status(mine, p) != ProblemProgress.Status.SOLVED))
                    .findFirst().orElse(null);
        }
        if (focus != null) {
            Long topicId = focus.getId();
            String why = focusSlug != null && focus.getSlug().equals(focusSlug) ? "Next in your focus topic, " : "Continue where you left off in ";
            String label = dev.hackercode.problem.Labels.of(focus.getName()).label();
            visible.stream().filter(p -> p.getTopicId().equals(topicId) && status(mine, p) == ProblemProgress.Status.NOT_STARTED && !used.contains(p.getId()))
                    .limit(3).forEach(p -> {
                        out.add(new Recommendation(summary(s, p, mine.get(p.getId())), why + label + "."));
                        used.add(p.getId());
                    });
        }
        for (TopicStats t : problems.topics(CurrentUser.id())) {
            if (out.size() >= 6) break;
            if (t.solved() == 0 && t.attempted() == 0 && t.total() > 0) {
                Topic topic = s.topicsBySlug().get(t.slug());
                visible.stream().filter(p -> p.getTopicId().equals(topic.getId()) && !used.contains(p.getId())).findFirst().ifPresent(p -> {
                    out.add(new Recommendation(summary(s, p, mine.get(p.getId())), "Start " + t.label() + ", the next untouched topic in the sheet."));
                    used.add(p.getId());
                });
                break;
            }
        }
        return out;
    }

    static int currentStreak(Set<LocalDate> days, LocalDate today) {
        LocalDate d = days.contains(today) ? today : today.minusDays(1);
        int n = 0;
        while (days.contains(d)) {
            n++;
            d = d.minusDays(1);
        }
        return n;
    }

    static int longestStreak(Set<LocalDate> days) {
        int best = 0;
        for (LocalDate d : days) {
            if (days.contains(d.minusDays(1))) continue;
            int n = 0;
            LocalDate x = d;
            while (days.contains(x)) {
                n++;
                x = x.plus(1, ChronoUnit.DAYS);
            }
            best = Math.max(best, n);
        }
        return best;
    }
}
