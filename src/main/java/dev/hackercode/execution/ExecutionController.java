package dev.hackercode.execution;

import dev.hackercode.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.hackercode.admin.PlatformSettingsService;

@RestController
@RequestMapping("/api")
public class ExecutionController {

    public record RunRequest(@NotBlank String language,
                             @NotNull @Size(max = ExecutionService.MAX_CODE, message = "Code is limited to 100,000 characters") String code,
                             @Size(max = ExecutionService.MAX_STDIN, message = "Input is limited to 64,000 characters") String stdin,
                             @Size(max = 16) String problemId) {}

    public record LanguageView(String id, String label, String fileName, String monacoId, boolean compiled, boolean enabled, String starter) {}

    public record SubmissionView(Long id, String language, String status, Integer testsPassed, Integer testsTotal, Long timeMs, String code, Instant createdAt) {}

    private final ExecutionService execution;
    private final ExecutionRunRepository runs;
    private final PlatformSettingsService platform;

    public ExecutionController(ExecutionService execution, ExecutionRunRepository runs, PlatformSettingsService platform) {
        this.execution = execution;
        this.runs = runs;
        this.platform = platform;
    }

    @GetMapping("/languages")
    public Map<String, Object> languages() {
        var enabled = platform.enabledLanguages();
        List<LanguageView> langs = Arrays.stream(Language.values())
                .map(l -> new LanguageView(l.id(), l.label(), l.fileName(), l.monacoId(), l.compiled(), enabled.contains(l.id()), l.starter()))
                .toList();
        return Map.of("languages", langs, "execution", execution.status());
    }

    @PostMapping("/code/run")
    public ExecutionService.RunResponse run(@Valid @RequestBody RunRequest req) {
        return execution.run(CurrentUser.id(), blankToNull(req.problemId()), Language.require(req.language()), req.code(), req.stdin());
    }

    @PostMapping("/problems/{id}/submit")
    public ExecutionService.RunResponse submit(@PathVariable String id, @Valid @RequestBody RunRequest req) {
        return execution.submit(CurrentUser.id(), id, Language.require(req.language()), req.code(), req.stdin());
    }

    @GetMapping("/problems/{id}/submissions")
    public List<SubmissionView> submissions(@PathVariable String id) {
        return runs.findTop20ByUserIdAndProblemIdAndKindOrderByCreatedAtDesc(CurrentUser.id(), id, "SUBMIT").stream()
                .map(r -> new SubmissionView(r.getId(), r.getLanguage(), r.getStatus(), r.getTestsPassed(), r.getTestsTotal(), r.getTimeMs(), r.getCode(), r.getCreatedAt()))
                .toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
