package dev.hackercode.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.hackercode.IntegrationTest;
import dev.hackercode.review.AiReviewer;
import dev.hackercode.review.ReviewReport;
import dev.hackercode.user.Role;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;

/**
 * Exercises run/submit/review end to end with deterministic fake providers. The fakes stand in for the external
 * sandbox and AI service only; every other layer (security, validation, persistence, progress) is real.
 */
@TestPropertySource(properties = {"hackercode.execution.provider=fake", "hackercode.review.provider=fake"})
class ExecutionAndReviewTest extends IntegrationTest {

    static final AtomicReference<AiReviewer.Outcome> NEXT_REVIEW = new AtomicReference<>();

    /** Echoes "odd"/"even" for an integer on stdin; "COMPILE_ERROR" in the code simulates a compiler failure. */
    static class FakeSandbox implements ExecutionProvider {
        @Override public String name() { return "fake"; }
        @Override public boolean configured() { return true; }
        @Override public String setupHint() { return ""; }
        @Override
        public Result execute(Language language, String code, String stdin, Limits limits) {
            if (code.contains("COMPILE_ERROR")) return new Result(Status.COMPILE_ERROR, null, null, "main.c:1: error", 1, null, null, "Compilation failed");
            if (code.contains("LOOP")) return new Result(Status.TIME_LIMIT, "", "", null, 137, (long) limits.timeoutSeconds() * 1000, null, "Time limit exceeded");
            String in = stdin == null ? "" : stdin.trim();
            String out = in.isEmpty() ? "hello\n" : (Integer.parseInt(in) % 2 == 0 ? "even\n" : "odd\n");
            if (code.contains("WRONG")) out = "wrong\n";
            return new Result(Status.OK, out, "", null, 0, 3L, 1024L, null);
        }
    }

    static class FakeReviewer implements AiReviewer {
        @Override public String name() { return "fake"; }
        @Override public boolean configured() { return true; }
        @Override public String model() { return "fake-model"; }
        @Override public String setupHint() { return ""; }
        @Override public Outcome review(Request request) { return NEXT_REVIEW.get(); }
    }

    @TestConfiguration
    static class Fakes {
        @Bean FakeSandbox fakeSandbox() { return new FakeSandbox(); }
        @Bean FakeReviewer fakeReviewer() { return new FakeReviewer(); }
    }

    private static Map<String, Object> run(String language, String code, String stdin) {
        return Map.of("language", language, "code", code, "stdin", stdin);
    }

    @Test
    void allSixLanguagesAreOfferedWithCorrectFileNames() throws Exception {
        MockHttpSession s = signUp(uniqueEmail("langs"));
        JsonNode langs = read(getAs(s, "/api/languages").andExpect(status().isOk()).andReturn()).get("languages");
        assertThat(langs).hasSize(6);
        Map<String, String> files = new java.util.HashMap<>();
        langs.forEach(l -> files.put(l.get("id").asText(), l.get("fileName").asText()));
        assertThat(files).containsExactlyInAnyOrderEntriesOf(Map.of(
                "c", "main.c", "cpp", "main.cpp", "java", "Main.java", "python", "main.py", "javascript", "main.js", "go", "main.go"));
        langs.forEach(l -> assertThat(l.get("starter").asText()).isNotBlank());
    }

    @Test
    void unverifiedUsersCannotRunCode() throws Exception {
        MockHttpSession s = signUp(uniqueEmail("unverified"));
        postAs(s, "/api/code/run", run("c", "int main(){}", "")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("email_unverified"));
    }

    @Test
    void runReturnsSandboxOutputForCAndGo() throws Exception {
        MockHttpSession s = signUpVerified(uniqueEmail("run"));
        postAs(s, "/api/code/run", run("c", "int main(){}", "7")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK")).andExpect(jsonPath("$.stdout").value("odd\n"))
                .andExpect(jsonPath("$.provider").value("fake"));
        postAs(s, "/api/code/run", run("go", "package main", "4")).andExpect(status().isOk()).andExpect(jsonPath("$.stdout").value("even\n"));
        postAs(s, "/api/code/run", run("c", "COMPILE_ERROR", "")).andExpect(jsonPath("$.status").value("COMPILE_ERROR"))
                .andExpect(jsonPath("$.compileOutput").value("main.c:1: error"));
        postAs(s, "/api/code/run", run("go", "LOOP", "")).andExpect(jsonPath("$.status").value("TIME_LIMIT"));
        postAs(s, "/api/code/run", run("rust", "fn main(){}", "")).andExpect(status().isBadRequest());
        postAs(s, "/api/code/run", run("c", " ", "")).andExpect(status().isBadRequest());
        postAs(s, "/api/code/run", run("c", "x".repeat(100_001), "")).andExpect(status().isBadRequest());
    }

    @Test
    void runningCodeForAProblemMarksItAttempted() throws Exception {
        MockHttpSession s = signUpVerified(uniqueEmail("attempt"));
        postAs(s, "/api/code/run", Map.of("language", "python", "code", "print(1)", "stdin", "", "problemId", "cf-001"))
                .andExpect(jsonPath("$.progressStatus").value("ATTEMPTED"));
    }

    @Test
    void submitWithoutTestsSavesHonestlyWithoutJudging() throws Exception {
        MockHttpSession s = signUpVerified(uniqueEmail("notests"));
        postAs(s, "/api/problems/cf-002/submit", run("java", "class Main{}", "3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.testsTotal").value(0))
                .andExpect(jsonPath("$.progressStatus").value("ATTEMPTED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no test cases")));
        JsonNode subs = read(getAs(s, "/api/problems/cf-002/submissions").andReturn());
        assertThat(subs).hasSize(1);
        assertThat(subs.get(0).get("code").asText()).isEqualTo("class Main{}");
    }

    @Test
    void submitJudgesAgainstOperatorTests() throws Exception {
        MockHttpSession admin = signUpWithRole(uniqueEmail("tests-admin"), Role.ADMIN);
        String tests = "[{\"input\":\"3\",\"output\":\"odd\"},{\"input\":\"10\",\"output\":\"even\",\"hidden\":true}]";
        putAs(admin, "/api/admin/problems/cf-001", Map.of("testsJson", tests)).andExpect(status().isOk());

        MockHttpSession s = signUpVerified(uniqueEmail("judge"));
        postAs(s, "/api/problems/cf-001/submit", run("cpp", "WRONG", "")).andExpect(jsonPath("$.status").value("WRONG_ANSWER"))
                .andExpect(jsonPath("$.testsPassed").value(0)).andExpect(jsonPath("$.cases[1].input").doesNotExist());
        postAs(s, "/api/problems/cf-001/submit", run("cpp", "int main(){}", "")).andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.testsPassed").value(2)).andExpect(jsonPath("$.progressStatus").value("SOLVED"));
        putAs(admin, "/api/admin/problems/cf-001", Map.of("testsJson", "")).andExpect(status().isOk());
    }

    @Test
    void executionIsRateLimitedPerUser() throws Exception {
        MockHttpSession s = signUpVerified(uniqueEmail("burst"));
        int last = 0;
        for (int i = 0; i < 22; i++) {
            last = postAs(s, "/api/code/run", run("c", "int main(){}", "1")).andReturn().getResponse().getStatus();
        }
        assertThat(last).isEqualTo(429);
    }

    @Test
    void aiReviewIsLabelledAndCombinedWithStaticChecks() throws Exception {
        NEXT_REVIEW.set(new AiReviewer.Outcome(new ReviewReport("Readable but quadratic.", 72,
                new ReviewReport.Complexity("O(n^2)", "O(1)", "Two nested loops.", "O(n)"),
                List.of(new ReviewReport.Finding(ReviewReport.Category.performance, ReviewReport.Severity.high, "Nested loops", 2,
                        "Loops over all pairs.", "Times out for n=10^5.", "Use a hash map.")),
                List.of("Clear names"), List.of("Use a hash map"), ""), "fake-model", null, null));
        MockHttpSession s = signUpVerified(uniqueEmail("review"));
        JsonNode r = read(postAs(s, "/api/reviews", Map.of("language", "python", "code", "for i in a:\n    for j in a:\n        pass\n"))
                .andExpect(status().isOk()).andReturn());
        assertThat(r.get("ai").get("generated").asBoolean()).isTrue();
        assertThat(r.get("ai").get("notice").asText()).contains("AI-generated");
        assertThat(r.get("ai").get("report").get("findings").get(0).get("line").asInt()).isEqualTo(2);
        assertThat(r.get("staticChecks").get("maxLoopDepth").asInt()).isEqualTo(2);
        assertThat(r.get("disclaimer").asText()).contains("no code was run");
        long id = r.get("id").asLong();
        getAs(s, "/api/reviews/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.code").exists());
        MockHttpSession stranger = signUp(uniqueEmail("stranger"));
        getAs(stranger, "/api/reviews/" + id).andExpect(status().isNotFound());
    }

    @Test
    void aiFailuresAreReportedNotHidden() throws Exception {
        NEXT_REVIEW.set(new AiReviewer.Outcome(null, null, "ai_timeout", "The AI provider could not be reached or timed out."));
        MockHttpSession s = signUpVerified(uniqueEmail("aifail"));
        postAs(s, "/api/reviews", Map.of("language", "c", "code", "#include <stdio.h>\nint main(){char b[8]; gets(b);}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ai.generated").value(false))
                .andExpect(jsonPath("$.ai.errorCode").value("ai_timeout"))
                .andExpect(jsonPath("$.staticChecks.findings[0].rule").value("c-gets"));
    }

    @Test
    void reviewRequestsAreValidated() throws Exception {
        MockHttpSession s = signUpVerified(uniqueEmail("rv"));
        postAs(s, "/api/reviews", Map.of("language", "python", "code", "")).andExpect(status().isBadRequest());
        postAs(s, "/api/reviews", Map.of("language", "python", "code", "x".repeat(50_001))).andExpect(status().isBadRequest());
        postAs(s, "/api/reviews", Map.of("language", "cobol", "code", "x")).andExpect(status().isBadRequest());
    }
}
