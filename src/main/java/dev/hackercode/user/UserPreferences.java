package dev.hackercode.user;

import dev.hackercode.common.ApiException;
import java.util.List;
import java.util.Set;

/**
 * Everything a user can personalise. Stored as one JSON document per user; {@link #validated()} rejects
 * values outside the allowed sets so a crafted request cannot store arbitrary data.
 */
public record UserPreferences(
        Appearance appearance,
        Editor editor,
        Learning learning,
        Review review,
        Notifications notifications,
        Privacy privacy,
        Accessibility accessibility) {

    public record Appearance(String theme, String reducedMotion, String dashboardDensity,
                             boolean showActivityHeatmap, boolean showLanguageChart, boolean showRecommendations) {}

    public record Editor(String theme, int fontSize, int tabSize, boolean insertSpaces, boolean wordWrap,
                         boolean minimap, String defaultLanguage, String layout, boolean autosave, boolean formatOnRun) {}

    public record Learning(int dailyGoal, int weeklyGoal, boolean remindersEnabled, String reminderTime, String focusTopic) {}

    public record Review(String detailLevel, List<String> categories, boolean explainForBeginners, boolean includeRefactorSuggestion) {}

    public record Notifications(boolean inApp, boolean emailReminders, boolean emailProductUpdates, boolean emailSecurityAlerts) {}

    public record Privacy(boolean trackActivity, boolean keepReviewHistory, boolean keepRunHistory) {}

    public record Accessibility(String fontScale, boolean highContrast, boolean underlineLinks, boolean alwaysShowFocus) {}

    public static final Set<String> LANGUAGES = Set.of("c", "cpp", "java", "python", "javascript", "go");
    public static final Set<String> REVIEW_CATEGORIES = Set.of(
            "correctness", "security", "performance", "complexity", "readability", "best-practices");

    public static UserPreferences defaults() {
        return new UserPreferences(
                new Appearance("system", "system", "comfortable", true, true, true),
                new Editor("hackercode-dark", 14, 4, true, false, false, "java", "split", true, false),
                new Learning(2, 10, false, "19:00", null),
                new Review("standard", List.copyOf(REVIEW_CATEGORIES.stream().sorted().toList()), false, true),
                new Notifications(true, false, false, true),
                new Privacy(true, true, true),
                new Accessibility("100", false, false, false));
    }

    /** Fills missing sections with defaults and validates every enumerated or bounded value. */
    public UserPreferences validated() {
        UserPreferences d = defaults();
        Appearance a = appearance == null ? d.appearance : appearance;
        Editor e = editor == null ? d.editor : editor;
        Learning l = learning == null ? d.learning : learning;
        Review r = review == null ? d.review : review;
        Notifications n = notifications == null ? d.notifications : notifications;
        Privacy p = privacy == null ? d.privacy : privacy;
        Accessibility x = accessibility == null ? d.accessibility : accessibility;

        oneOf("appearance.theme", a.theme(), "light", "dark", "system");
        oneOf("appearance.reducedMotion", a.reducedMotion(), "system", "reduce", "full");
        oneOf("appearance.dashboardDensity", a.dashboardDensity(), "comfortable", "compact");
        oneOf("editor.theme", e.theme(), "hackercode-dark", "hackercode-light", "vs-dark", "vs", "hc-black");
        range("editor.fontSize", e.fontSize(), 10, 28);
        oneOf("editor.tabSize", String.valueOf(e.tabSize()), "2", "4", "8");
        if (!LANGUAGES.contains(e.defaultLanguage())) throw invalid("editor.defaultLanguage");
        oneOf("editor.layout", e.layout(), "split", "stacked");
        range("learning.dailyGoal", l.dailyGoal(), 1, 50);
        range("learning.weeklyGoal", l.weeklyGoal(), 1, 200);
        if (l.reminderTime() == null || !l.reminderTime().matches("([01]\\d|2[0-3]):[0-5]\\d")) throw invalid("learning.reminderTime");
        if (l.focusTopic() != null && l.focusTopic().length() > 120) throw invalid("learning.focusTopic");
        oneOf("review.detailLevel", r.detailLevel(), "brief", "standard", "thorough");
        if (r.categories() == null || r.categories().isEmpty() || !REVIEW_CATEGORIES.containsAll(r.categories())) {
            throw invalid("review.categories");
        }
        oneOf("accessibility.fontScale", x.fontScale(), "90", "100", "110", "125");
        return new UserPreferences(a, e, l, new Review(r.detailLevel(), List.copyOf(Set.copyOf(r.categories())),
                r.explainForBeginners(), r.includeRefactorSuggestion()), n, p, x);
    }

    private static void oneOf(String field, String value, String... allowed) {
        if (value == null || !List.of(allowed).contains(value)) throw invalid(field);
    }

    private static void range(String field, int value, int min, int max) {
        if (value < min || value > max) throw invalid(field);
    }

    private static ApiException invalid(String field) {
        return ApiException.badRequest("invalid_setting", "Invalid value for " + field + ".");
    }
}
