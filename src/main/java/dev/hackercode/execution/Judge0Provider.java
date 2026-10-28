package dev.hackercode.execution;

import com.fasterxml.jackson.databind.JsonNode;
import dev.hackercode.config.AppProperties;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Judge0 CE (self-hosted, or the hosted RapidAPI edition). Judge0 runs each submission in an isolate sandbox
 * with CPU, memory and network limits.
 */
@Component
public class Judge0Provider implements ExecutionProvider {
    private static final Logger log = LoggerFactory.getLogger(Judge0Provider.class);

    private final AppProperties.Execution cfg;
    private final RestClient http;

    public Judge0Provider(AppProperties props, RestClient.Builder builder) {
        this.cfg = props.execution();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.http = builder.clone().requestFactory(factory).build();
    }

    @Override public String name() { return "judge0"; }

    @Override
    public boolean configured() {
        return cfg.judge0Url() != null && !cfg.judge0Url().isBlank();
    }

    @Override
    public String setupHint() {
        return "Set EXECUTION_PROVIDER=judge0 and JUDGE0_URL (plus JUDGE0_AUTH_TOKEN, or JUDGE0_RAPIDAPI_HOST + JUDGE0_AUTH_TOKEN for RapidAPI).";
    }

    @Override
    public Result execute(Language language, String code, String stdin, Limits limits) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("language_id", language.judge0Id());
        body.put("source_code", b64(code));
        body.put("stdin", b64(stdin == null ? "" : stdin));
        body.put("cpu_time_limit", limits.timeoutSeconds());
        body.put("wall_time_limit", limits.timeoutSeconds() * 2);
        body.put("memory_limit", limits.memoryMb() * 1024);
        body.put("enable_network", false);
        String url = cfg.judge0Url().replaceAll("/+$", "") + "/submissions?base64_encoded=true&wait=true";
        try {
            JsonNode res = http.post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (cfg.judge0AuthToken() != null && !cfg.judge0AuthToken().isBlank()) {
                            if (cfg.judge0RapidapiHost() != null && !cfg.judge0RapidapiHost().isBlank()) {
                                h.set("X-RapidAPI-Key", cfg.judge0AuthToken());
                                h.set("X-RapidAPI-Host", cfg.judge0RapidapiHost());
                            } else {
                                h.set(cfg.judge0AuthHeader(), cfg.judge0AuthToken());
                            }
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return parse(res, limits);
        } catch (RestClientResponseException e) {
            log.warn("Judge0 returned HTTP {}", e.getStatusCode().value());
            if (e.getStatusCode().value() == 429) return Result.providerError("The execution service is rate limiting requests. Try again shortly.");
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) return Result.providerError("The execution service rejected our credentials. An operator must check JUDGE0_AUTH_TOKEN.");
            return Result.providerError("The execution service returned an error (HTTP " + e.getStatusCode().value() + ").");
        } catch (Exception e) {
            log.warn("Judge0 request failed: {}", e.getMessage());
            return Result.providerError("The execution service could not be reached.");
        }
    }

    static Result parse(JsonNode res, Limits limits) {
        if (res == null) return Result.providerError("Empty response from the execution service.");
        int statusId = res.path("status").path("id").asInt(-1);
        String description = res.path("status").path("description").asText("");
        String stdout = unb64(res.path("stdout"), limits.maxOutputBytes());
        String stderr = unb64(res.path("stderr"), limits.maxOutputBytes());
        String compile = unb64(res.path("compile_output"), limits.maxOutputBytes());
        Integer exit = res.path("exit_code").isNumber() ? res.path("exit_code").asInt() : null;
        Long timeMs = res.path("time").isMissingNode() || res.path("time").isNull() ? null : Math.round(res.path("time").asDouble() * 1000);
        Long memKb = res.path("memory").isNumber() ? res.path("memory").asLong() : null;
        Status status = switch (statusId) {
            case 3 -> Status.OK;
            case 4 -> Status.OK; // "Wrong Answer" only applies to expected_output, which we do not send
            case 5 -> Status.TIME_LIMIT;
            case 6 -> Status.COMPILE_ERROR;
            case 7, 8, 9, 10, 11, 12 -> memKb != null && memKb >= limits.memoryMb() * 1024L ? Status.MEMORY_LIMIT : Status.RUNTIME_ERROR;
            case 1, 2 -> Status.PROVIDER_ERROR; // still queued: wait=true timed out
            default -> Status.PROVIDER_ERROR;
        };
        String message = status == Status.PROVIDER_ERROR ? "Execution service status: " + description : description;
        return new Result(status, stdout, stderr, compile, exit, timeMs, memKb, message);
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(JsonNode node, int max) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        String decoded = new String(Base64.getMimeDecoder().decode(node.asText()), StandardCharsets.UTF_8);
        return OutputLimits.truncate(decoded, max);
    }
}
