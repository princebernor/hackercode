package dev.hackercode.admin;

import dev.hackercode.common.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operator-controlled platform configuration: feature flags, maintenance mode, rate limits and enabled languages.
 * Values live in the platform_setting table; unknown keys are rejected and every value is validated.
 */
@Service
public class PlatformSettingsService {

    private record Def(String defaultValue, String description, Predicate<String> valid) {}

    private static final Predicate<String> BOOL = v -> v.equals("true") || v.equals("false");
    private static final Predicate<String> SMALL_INT = v -> v.matches("\\d{1,4}") && Integer.parseInt(v) >= 1;
    private static final Set<String> LANGS = Set.of("c", "cpp", "java", "python", "javascript", "go");

    public static final String SIGNUP_ENABLED = "feature.signup";
    public static final String AI_REVIEW_ENABLED = "feature.aiReview";
    public static final String EXECUTION_ENABLED = "feature.execution";
    public static final String MAINTENANCE = "maintenance.enabled";
    public static final String MAINTENANCE_MESSAGE = "maintenance.message";
    public static final String RATE_AUTH = "rateLimit.authPer10Min";
    public static final String RATE_RUN = "rateLimit.runsPerMinute";
    public static final String RATE_REVIEW = "rateLimit.reviewsPerHour";
    public static final String LANGUAGES = "languages.enabled";
    public static final String NOTIFY_WELCOME = "notifications.welcome";

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();
    static {
        DEFS.put(SIGNUP_ENABLED, new Def("true", "Allow new account registration", BOOL));
        DEFS.put(AI_REVIEW_ENABLED, new Def("true", "Allow AI code reviews (needs a configured provider)", BOOL));
        DEFS.put(EXECUTION_ENABLED, new Def("true", "Allow code execution (needs a configured sandbox)", BOOL));
        DEFS.put(MAINTENANCE, new Def("false", "Maintenance mode: non-staff users get a read-only notice", BOOL));
        DEFS.put(MAINTENANCE_MESSAGE, new Def("HackerCode is undergoing maintenance. Please check back shortly.",
                "Message shown during maintenance", v -> v.length() <= 300));
        DEFS.put(RATE_AUTH, new Def("10", "Sign-in / sign-up / reset attempts per IP per 10 minutes", SMALL_INT));
        DEFS.put(RATE_RUN, new Def("20", "Code runs per user per minute", SMALL_INT));
        DEFS.put(RATE_REVIEW, new Def("30", "Code reviews per user per hour", SMALL_INT));
        DEFS.put(LANGUAGES, new Def("c,cpp,java,python,javascript,go", "Languages offered in the editor",
                v -> !v.isBlank() && Set.of(v.split(",")).stream().allMatch(LANGS::contains)));
        DEFS.put(NOTIFY_WELCOME, new Def("true", "Send an in-app welcome notification to new users", BOOL));
    }

    private final PlatformSettingRepository repo;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public PlatformSettingsService(PlatformSettingRepository repo) {
        this.repo = repo;
    }

    public String get(String key) {
        Def def = DEFS.get(key);
        if (def == null) throw ApiException.badRequest("unknown_setting", "Unknown setting: " + key);
        return cache.computeIfAbsent(key, k -> repo.findById(k).map(PlatformSetting::getValue).orElse(def.defaultValue()));
    }

    public boolean flag(String key) { return Boolean.parseBoolean(get(key)); }

    public int number(String key) { return Integer.parseInt(get(key)); }

    public Set<String> enabledLanguages() { return Set.of(get(LANGUAGES).split(",")); }

    public Map<String, Map<String, String>> describeAll() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        DEFS.forEach((k, d) -> out.put(k, Map.of("value", get(k), "default", d.defaultValue(), "description", d.description())));
        return out;
    }

    @Transactional
    public void set(String key, String value, String actorEmail) {
        Def def = DEFS.get(key);
        if (def == null) throw ApiException.badRequest("unknown_setting", "Unknown setting: " + key);
        String v = value == null ? "" : value.trim();
        if (!def.valid().test(v)) throw ApiException.badRequest("invalid_value", "Invalid value for " + key);
        repo.findById(key).ifPresentOrElse(s -> s.update(v, actorEmail), () -> repo.save(new PlatformSetting(key, v, actorEmail)));
        cache.remove(key);
    }
}
