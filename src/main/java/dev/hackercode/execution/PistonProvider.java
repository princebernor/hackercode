package dev.hackercode.execution;

import com.fasterxml.jackson.databind.JsonNode;
import dev.hackercode.config.AppProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** A self-hosted Piston instance (https://github.com/engineer-man/piston), which sandboxes runs with isolate. */
@Component
public class PistonProvider implements ExecutionProvider {
    private static final Logger log = LoggerFactory.getLogger(PistonProvider.class);

    private final AppProperties.Execution cfg;
    private final RestClient http;

    public PistonProvider(AppProperties props, RestClient.Builder builder) {
        this.cfg = props.execution();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.http = builder.clone().requestFactory(factory).build();
    }

    @Override public String name() { return "piston"; }

    @Override
    public boolean configured() {
        return cfg.pistonUrl() != null && !cfg.pistonUrl().isBlank();
    }

    @Override
    public String setupHint() {
        return "Set EXECUTION_PROVIDER=piston and PISTON_URL to your Piston instance, with the c, c++, java, python, javascript (node) and go runtimes installed.";
    }

    @Override
    public Result execute(Language language, String code, String stdin, Limits limits) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("language", language.pistonName());
        body.put("version", "*");
        body.put("files", List.of(Map.of("name", language.fileName(), "content", code)));
        body.put("stdin", stdin == null ? "" : stdin);
        body.put("compile_timeout", 15_000);
        body.put("run_timeout", limits.timeoutSeconds() * 1000);
        body.put("compile_memory_limit", -1);
        body.put("run_memory_limit", (long) limits.memoryMb() * 1024 * 1024);
        try {
            JsonNode res = http.post().uri(cfg.pistonUrl().replaceAll("/+$", "") + "/api/v2/execute")
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            return parse(res, limits);
        } catch (RestClientResponseException e) {
            log.warn("Piston returned HTTP {}", e.getStatusCode().value());
            String msg = e.getResponseBodyAsString();
            if (msg.contains("runtime is unknown")) return Result.providerError(language.label() + " is not installed on the Piston server.");
            return Result.providerError("The execution service returned an error (HTTP " + e.getStatusCode().value() + ").");
        } catch (Exception e) {
            log.warn("Piston request failed: {}", e.getMessage());
            return Result.providerError("The execution service could not be reached.");
        }
    }

    static Result parse(JsonNode res, Limits limits) {
        if (res == null) return Result.providerError("Empty response from the execution service.");
        JsonNode compile = res.path("compile");
        if (!compile.isMissingNode() && compile.path("code").asInt(0) != 0) {
            String out = OutputLimits.truncate(compile.path("stderr").asText("") + compile.path("stdout").asText(""), limits.maxOutputBytes());
            return new Result(Status.COMPILE_ERROR, null, null, out, compile.path("code").asInt(), null, null, "Compilation failed");
        }
        JsonNode run = res.path("run");
        String signal = run.path("signal").isNull() ? null : run.path("signal").asText(null);
        Integer code = run.path("code").isNumber() ? run.path("code").asInt() : null;
        String stdout = OutputLimits.truncate(run.path("stdout").asText(""), limits.maxOutputBytes());
        String stderr = OutputLimits.truncate(run.path("stderr").asText(""), limits.maxOutputBytes());
        Long timeMs = run.path("wall_time").isNumber() ? run.path("wall_time").asLong() : null;
        Long memKb = run.path("memory").isNumber() ? run.path("memory").asLong() / 1024 : null;
        Status status;
        String message = null;
        if ("SIGKILL".equals(signal)) {
            status = "TIMEOUT".equalsIgnoreCase(run.path("status").asText("")) || timeMs == null || timeMs >= limits.timeoutSeconds() * 1000L
                    ? Status.TIME_LIMIT : Status.MEMORY_LIMIT;
        } else if (signal != null || (code != null && code != 0)) {
            status = Status.RUNTIME_ERROR;
            message = signal != null ? "Terminated by " + signal : "Exited with code " + code;
        } else {
            status = Status.OK;
        }
        return new Result(status, stdout, stderr, null, code, timeMs, memKb, message);
    }
}
