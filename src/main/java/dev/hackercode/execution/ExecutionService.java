package dev.hackercode.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hackercode.activity.ActivityEvent;
import dev.hackercode.activity.ActivityService;
import dev.hackercode.admin.PlatformSettingsService;
import dev.hackercode.common.ApiException;
import dev.hackercode.config.AppProperties;
import dev.hackercode.config.RateLimiter;
import dev.hackercode.problem.Problem;
import dev.hackercode.problem.ProblemCatalog;
import dev.hackercode.progress.ProblemProgress;
import dev.hackercode.progress.ProgressService;
import dev.hackercode.user.PreferencesService;
import dev.hackercode.user.User;
import dev.hackercode.user.UserRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ExecutionService {
    public static final int MAX_CODE = 100_000;
    public static final int MAX_STDIN = 64_000;
    public static final int MAX_TESTS = 20;

    public record CaseResult(int index, boolean passed, boolean hidden, String input, String expected, String actual, String status) {}

    public record RunResponse(Long runId, String kind, String status, String message, String stdout, String stderr, String compileOutput,
                              Integer exitCode, Long timeMs, Long memoryKb, String provider, Integer testsPassed, Integer testsTotal,
                              List<CaseResult> cases, String progressStatus) {}

    private final Map<String, ExecutionProvider> providers = new LinkedHashMap<>();
    private final AppProperties.Execution cfg;
    private final PlatformSettingsService platform;
    private final RateLimiter limiter;
    private final ExecutionRunRepository runs;
    private final UserRepository users;
    private final ProblemCatalog catalog;
    private final ProgressService progress;
    private final ActivityService activity;
    private final PreferencesService preferences;
    private final ObjectMapper json;

    public ExecutionService(List<ExecutionProvider> providerList, AppProperties props, PlatformSettingsService platform, RateLimiter limiter,
                            ExecutionRunRepository runs, UserRepository users, ProblemCatalog catalog, ProgressService progress,
                            ActivityService activity, PreferencesService preferences, ObjectMapper json) {
        providerList.forEach(p -> providers.put(p.name(), p));
        this.cfg = props.execution();
        this.platform = platform;
        this.limiter = limiter;
        this.runs = runs;
        this.users = users;
        this.catalog = catalog;
        this.progress = progress;
        this.activity = activity;
        this.preferences = preferences;
        this.json = json;
    }

    /** The provider selected by EXECUTION_PROVIDER, if it exists and is configured. */
    public ExecutionProvider active() {
        ExecutionProvider p = providers.get(cfg.provider() == null ? "" : cfg.provider().toLowerCase());
        return p != null && p.configured() ? p : null;
    }

    public Map<String, Object> status() {
        ExecutionProvider p = active();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("provider", cfg.provider());
        out.put("configured", p != null);
        out.put("enabled", platform.flag(PlatformSettingsService.EXECUTION_ENABLED));
        out.put("timeoutSeconds", cfg.timeoutSeconds());
        out.put("memoryMb", cfg.memoryMb());
        if (p == null) {
            out.put("setupHint", "Code execution is not configured. Choose one: "
                    + providers.values().stream().map(ExecutionProvider::setupHint).reduce((a, b) -> a + " | " + b).orElse(""));
        }
        return out;
    }

    private ExecutionProvider.Limits limits() {
        return new ExecutionProvider.Limits(Math.max(1, Math.min(cfg.timeoutSeconds(), 15)), Math.max(64, Math.min(cfg.memoryMb(), 1024)),
                Math.max(1024, cfg.maxOutputBytes()));
    }

    private ExecutionProvider checkAllowed(Long userId, Language language, String code, String stdin) {
        if (!platform.flag(PlatformSettingsService.EXECUTION_ENABLED)) {
            throw ApiException.unavailable("execution_disabled", "Code execution is temporarily disabled by an administrator.");
        }
        if (!platform.enabledLanguages().contains(language.id())) {
            throw ApiException.badRequest("language_disabled", language.label() + " is currently disabled.");
        }
        ExecutionProvider provider = active();
        if (provider == null) {
            throw ApiException.unavailable("execution_not_configured",
                    "Code execution is not configured on this server, so nothing was run. An operator needs to connect a sandbox (Docker, Judge0 or Piston).");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "email_unverified", "Verify your email address to run code.");
        }
        if (code == null || code.isBlank()) throw ApiException.badRequest("empty_code", "There is no code to run.");
        if (code.length() > MAX_CODE) throw ApiException.badRequest("code_too_large", "Code is limited to 100,000 characters.");
        if (stdin != null && stdin.length() > MAX_STDIN) throw ApiException.badRequest("input_too_large", "Input is limited to 64,000 characters.");
        int perMinute = platform.number(PlatformSettingsService.RATE_RUN);
        if (!limiter.tryAcquire("run:" + userId, perMinute, Duration.ofMinutes(1))) {
            throw ApiException.tooMany("You are running code too quickly. Wait a moment and try again.");
        }
        return provider;
    }

    public RunResponse run(Long userId, String problemId, Language language, String code, String stdin) {
        Problem problem = problemId == null ? null : catalog.find(problemId).orElseThrow(() -> ApiException.notFound("Problem"));
        ExecutionProvider provider = checkAllowed(userId, language, code, stdin);
        ExecutionProvider.Result r = provider.execute(language, code, stdin, limits());
        boolean keep = preferences.get(userId).privacy().keepRunHistory();
        ExecutionRun run = new ExecutionRun(userId, problem == null ? null : problem.getId(), language.id(), "RUN", provider.name());
        run.setResult(r.status().name(), keep ? r.stdout() : null, keep ? r.stderr() : null, keep ? r.compileOutput() : null, r.exitCode(), r.timeMs(), r.memoryKb());
        runs.save(run);
        activity.record(userId, ActivityEvent.Type.CODE_RUN, problem == null ? null : problem.getId(), language.id());
        String progressStatus = null;
        if (problem != null) progressStatus = progress.markAttempted(userId, problem.getId()).getStatus().name();
        return new RunResponse(run.getId(), "RUN", r.status().name(), r.message(), r.stdout(), r.stderr(), r.compileOutput(), r.exitCode(),
                r.timeMs(), r.memoryKb(), provider.name(), null, null, List.of(), progressStatus);
    }

    /**
     * Runs the code against the problem's operator-supplied test cases. The source PDF provides no tests, so for
     * problems without tests the code is executed once with the user's input and saved, and the response says so.
     */
    public RunResponse submit(Long userId, String problemId, Language language, String code, String stdin) {
        Problem problem = catalog.find(problemId).orElseThrow(() -> ApiException.notFound("Problem"));
        ExecutionProvider provider = checkAllowed(userId, language, code, stdin);
        List<TestCase> tests = parseTests(problem.getTestsJson());
        ExecutionRun run = new ExecutionRun(userId, problem.getId(), language.id(), "SUBMIT", provider.name());
        run.setCode(code);
        String progressStatus;

        if (tests.isEmpty()) {
            ExecutionProvider.Result r = provider.execute(language, code, stdin, limits());
            run.setResult(r.status().name(), r.stdout(), r.stderr(), r.compileOutput(), r.exitCode(), r.timeMs(), r.memoryKb());
            run.setTests(0, 0);
            runs.save(run);
            activity.record(userId, ActivityEvent.Type.CODE_SUBMITTED, problem.getId(), language.id());
            progressStatus = progress.markAttempted(userId, problem.getId()).getStatus().name();
            return new RunResponse(run.getId(), "SUBMIT", r.status().name(),
                    "Saved. This problem has no test cases yet, so it could not be judged automatically. Your code ran once with your input; mark the problem solved when you are satisfied.",
                    r.stdout(), r.stderr(), r.compileOutput(), r.exitCode(), r.timeMs(), r.memoryKb(), provider.name(), 0, 0, List.of(), progressStatus);
        }

        List<CaseResult> cases = new ArrayList<>();
        int passed = 0;
        long maxTime = 0;
        ExecutionProvider.Result last = null;
        String overall = "OK";
        for (int i = 0; i < tests.size(); i++) {
            TestCase t = tests.get(i);
            ExecutionProvider.Result r = provider.execute(language, code, t.input(), limits());
            last = r;
            if (r.timeMs() != null) maxTime = Math.max(maxTime, r.timeMs());
            if (r.status() == ExecutionProvider.Status.COMPILE_ERROR || r.status() == ExecutionProvider.Status.PROVIDER_ERROR) {
                overall = r.status().name();
                break;
            }
            boolean ok = r.status() == ExecutionProvider.Status.OK && normalize(r.stdout()).equals(normalize(t.expected()));
            if (ok) passed++;
            else if ("OK".equals(overall)) overall = r.status() == ExecutionProvider.Status.OK ? "WRONG_ANSWER" : r.status().name();
            cases.add(new CaseResult(i + 1, ok, t.hidden(), t.hidden() ? null : t.input(), t.hidden() ? null : t.expected(),
                    t.hidden() ? null : r.stdout(), r.status().name()));
        }
        run.setResult(overall, null, last == null ? null : last.stderr(), last == null ? null : last.compileOutput(),
                last == null ? null : last.exitCode(), maxTime, null);
        run.setTests(passed, tests.size());
        runs.save(run);
        activity.record(userId, ActivityEvent.Type.CODE_SUBMITTED, problem.getId(), language.id());
        boolean allPassed = passed == tests.size();
        progressStatus = allPassed
                ? progress.update(userId, problem.getId(), ProblemProgress.Status.SOLVED, null, null).getStatus().name()
                : progress.markAttempted(userId, problem.getId()).getStatus().name();
        String message = allPassed ? "Accepted: all " + tests.size() + " test cases passed." : passed + " of " + tests.size() + " test cases passed.";
        return new RunResponse(run.getId(), "SUBMIT", allPassed ? "ACCEPTED" : overall, message, null, last == null ? null : last.stderr(),
                last == null ? null : last.compileOutput(), null, maxTime, null, provider.name(), passed, tests.size(), cases, progressStatus);
    }

    record TestCase(String input, String expected, boolean hidden) {}

    List<TestCase> parseTests(String testsJson) {
        if (testsJson == null || testsJson.isBlank()) return List.of();
        try {
            JsonNode arr = json.readTree(testsJson);
            List<TestCase> out = new ArrayList<>();
            for (JsonNode n : arr) {
                if (out.size() >= MAX_TESTS) break;
                out.add(new TestCase(n.path("input").asText(""), n.path("output").asText(""), n.path("hidden").asBoolean(false)));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Compares outputs ignoring trailing whitespace on each line and trailing blank lines. */
    static String normalize(String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").lines().map(String::stripTrailing).reduce((a, b) -> a + "\n" + b).orElse("").stripTrailing();
    }
}
