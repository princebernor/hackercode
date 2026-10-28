package dev.hackercode.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hackercode.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for provider response parsing and the Docker sandbox command. No external services are called. */
class ProviderUnitTest {
    static final ExecutionProvider.Limits LIMITS = new ExecutionProvider.Limits(5, 256, 65536);
    final ObjectMapper json = new ObjectMapper();

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void judge0StatusesMapToResults() throws Exception {
        JsonNode ok = json.readTree("{\"status\":{\"id\":3,\"description\":\"Accepted\"},\"stdout\":\"" + b64("42\n") + "\",\"time\":\"0.012\",\"memory\":2048,\"exit_code\":0}");
        ExecutionProvider.Result r = Judge0Provider.parse(ok, LIMITS);
        assertThat(r.status()).isEqualTo(ExecutionProvider.Status.OK);
        assertThat(r.stdout()).isEqualTo("42\n");
        assertThat(r.timeMs()).isEqualTo(12L);

        JsonNode ce = json.readTree("{\"status\":{\"id\":6,\"description\":\"Compilation Error\"},\"compile_output\":\"" + b64("error: expected ';'") + "\"}");
        assertThat(Judge0Provider.parse(ce, LIMITS).status()).isEqualTo(ExecutionProvider.Status.COMPILE_ERROR);
        assertThat(Judge0Provider.parse(ce, LIMITS).compileOutput()).contains("expected ';'");

        JsonNode tle = json.readTree("{\"status\":{\"id\":5,\"description\":\"Time Limit Exceeded\"}}");
        assertThat(Judge0Provider.parse(tle, LIMITS).status()).isEqualTo(ExecutionProvider.Status.TIME_LIMIT);

        JsonNode rte = json.readTree("{\"status\":{\"id\":11,\"description\":\"Runtime Error (NZEC)\"},\"memory\":1000}");
        assertThat(Judge0Provider.parse(rte, LIMITS).status()).isEqualTo(ExecutionProvider.Status.RUNTIME_ERROR);

        JsonNode queued = json.readTree("{\"status\":{\"id\":1,\"description\":\"In Queue\"}}");
        assertThat(Judge0Provider.parse(queued, LIMITS).status()).isEqualTo(ExecutionProvider.Status.PROVIDER_ERROR);
    }

    @Test
    void judge0OutputIsTruncated() throws Exception {
        String big = "x".repeat(70_000);
        JsonNode ok = json.readTree("{\"status\":{\"id\":3},\"stdout\":\"" + b64(big) + "\"}");
        assertThat(Judge0Provider.parse(ok, LIMITS).stdout()).hasSizeLessThan(66_000).contains("output truncated");
    }

    @Test
    void pistonResultsMapToResults() throws Exception {
        JsonNode compileFail = json.readTree("{\"compile\":{\"code\":1,\"stderr\":\"main.go:3: syntax error\",\"stdout\":\"\"},\"run\":{}}");
        assertThat(PistonProvider.parse(compileFail, LIMITS).status()).isEqualTo(ExecutionProvider.Status.COMPILE_ERROR);
        JsonNode ok = json.readTree("{\"run\":{\"code\":0,\"signal\":null,\"stdout\":\"hi\\n\",\"stderr\":\"\"}}");
        assertThat(PistonProvider.parse(ok, LIMITS).stdout()).isEqualTo("hi\n");
        JsonNode killed = json.readTree("{\"run\":{\"code\":null,\"signal\":\"SIGKILL\",\"status\":\"TIMEOUT\",\"stdout\":\"\",\"stderr\":\"\"}}");
        assertThat(PistonProvider.parse(killed, LIMITS).status()).isEqualTo(ExecutionProvider.Status.TIME_LIMIT);
        JsonNode crash = json.readTree("{\"run\":{\"code\":139,\"signal\":\"SIGSEGV\",\"stdout\":\"\",\"stderr\":\"\"}}");
        assertThat(PistonProvider.parse(crash, LIMITS).status()).isEqualTo(ExecutionProvider.Status.RUNTIME_ERROR);
    }

    @Test
    void dockerSandboxIsLockedDown() {
        AppProperties props = new AppProperties("http://localhost", "x", null, null, null,
                new AppProperties.Execution("docker", 5, 256, 65536, null, null, null, null, null, "docker"), null);
        DockerProvider docker = new DockerProvider(props);
        for (Language lang : List.of(Language.C, Language.GO, Language.JAVA, Language.PYTHON)) {
            List<String> cmd = docker.command(lang, Path.of("work"), "hc-run-test", LIMITS);
            String joined = String.join(" ", cmd);
            assertThat(joined).contains("--network none", "--read-only", "--cap-drop ALL", "--security-opt no-new-privileges",
                    "--memory 256m", "--pids-limit 128", "--user 65534:65534", ":/code:ro", lang.dockerImage(),
                    "timeout -s KILL 5");
        }
        String c = String.join(" ", docker.command(Language.C, Path.of("work"), "n", LIMITS));
        assertThat(c).contains("gcc -O2 -std=c17").contains("exit 97");
        String go = String.join(" ", docker.command(Language.GO, Path.of("work"), "n", LIMITS));
        assertThat(go).contains("go build").contains("GOCACHE=/tmp/.gocache");
    }

    @Test
    void outputComparisonIgnoresTrailingWhitespace() {
        assertThat(ExecutionService.normalize("1 2 \r\n3\n\n")).isEqualTo(ExecutionService.normalize("1 2\n3"));
        assertThat(ExecutionService.normalize("1\n2")).isNotEqualTo(ExecutionService.normalize("1 2"));
    }
}
