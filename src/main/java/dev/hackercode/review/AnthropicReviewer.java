package dev.hackercode.review;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessage;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import dev.hackercode.config.AppProperties;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Code review with Claude via the official Anthropic Java SDK.
 * <ul>
 *   <li>Structured output: the response is constrained to {@link ReviewReport}'s JSON schema.</li>
 *   <li>Effort follows the user's "review detail level" preference.</li>
 *   <li>Server-side refusal fallbacks are enabled, so a request declined by a safety classifier
 *       (security-heavy code can trigger one) is retried on a suitable model instead of failing.</li>
 * </ul>
 */
@Component
public class AnthropicReviewer implements AiReviewer {
    private static final Logger log = LoggerFactory.getLogger(AnthropicReviewer.class);
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AppProperties.Review cfg;
    private volatile AnthropicClient client;

    public AnthropicReviewer(AppProperties props) {
        this.cfg = props.review();
    }

    @Override public String name() { return "anthropic"; }

    @Override
    public boolean configured() {
        return cfg.anthropicApiKey() != null && !cfg.anthropicApiKey().isBlank();
    }

    @Override
    public String model() {
        return cfg.anthropicModel() == null || cfg.anthropicModel().isBlank() ? "claude-opus-5-5" : cfg.anthropicModel();
    }

    @Override
    public String setupHint() {
        return "Set AI_PROVIDER=anthropic and ANTHROPIC_API_KEY on the server (optionally ANTHROPIC_MODEL; default claude-opus-5-5).";
    }

    private AnthropicClient client() {
        AnthropicClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder()
                            .apiKey(cfg.anthropicApiKey())
                            .timeout(Duration.ofSeconds(Math.max(15, cfg.timeoutSeconds())))
                            .maxRetries(2)
                            .build();
                }
                c = client;
            }
        }
        return c;
    }

    static BetaOutputConfig.Effort effortFor(String detailLevel) {
        return switch (detailLevel == null ? "standard" : detailLevel) {
            case "brief" -> BetaOutputConfig.Effort.LOW;
            case "thorough" -> BetaOutputConfig.Effort.HIGH;
            default -> BetaOutputConfig.Effort.MEDIUM;
        };
    }

    @Override
    public Outcome review(Request request) {
        StructuredMessageCreateParams<ReviewReport> params = MessageCreateParams.builder()
                .model(model())
                .maxTokens(16000L)
                .system(ReviewPrompt.system(request))
                .addUserMessage(ReviewPrompt.user(request))
                .addBeta(FALLBACK_BETA)
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .outputConfig(ReviewReport.class, effortFor(request.detailLevel()))
                .build();
        try {
            StructuredMessage<ReviewReport> message = client().beta().messages().create(params);
            Optional<BetaStopReason> stop = message.stopReason();
            if (stop.isPresent() && stop.get().equals(BetaStopReason.REFUSAL)) {
                return Outcome.failed("ai_declined", "The AI reviewer declined to review this code. Static checks are still shown.");
            }
            if (stop.isPresent() && stop.get().equals(BetaStopReason.MAX_TOKENS)) {
                return Outcome.failed("ai_truncated", "The AI review was cut off before it finished. Try the 'brief' detail level or a shorter file.");
            }
            Optional<ReviewReport> report = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst();
            return report.map(r -> Outcome.ok(r, message.model().asString()))
                    .orElseGet(() -> Outcome.failed("ai_invalid_response", "The AI reviewer returned no usable result. Please try again."));
        } catch (UnauthorizedException | PermissionDeniedException e) {
            log.error("Anthropic rejected the configured API key ({})", e.statusCode());
            return Outcome.failed("ai_auth", "The AI provider rejected the server's credentials. An operator must check ANTHROPIC_API_KEY.");
        } catch (NotFoundException e) {
            log.error("Anthropic model not found: {}", model());
            return Outcome.failed("ai_model", "The configured AI model is not available. An operator must check ANTHROPIC_MODEL.");
        } catch (RateLimitException e) {
            return Outcome.failed("ai_rate_limited", "The AI provider is rate limiting requests. Please try again in a minute.");
        } catch (BadRequestException e) {
            log.warn("Anthropic rejected the review request: {}", e.getMessage());
            return Outcome.failed("ai_bad_request", "The AI provider could not process this request.");
        } catch (InternalServerException e) {
            return Outcome.failed("ai_unavailable", "The AI provider is having problems right now. Please try again shortly.");
        } catch (AnthropicServiceException e) {
            log.warn("Anthropic API error {}: {}", e.statusCode(), e.getMessage());
            return Outcome.failed("ai_error", "The AI provider returned an error. Please try again.");
        } catch (AnthropicIoException e) {
            log.warn("Could not reach Anthropic: {}", e.getMessage());
            return Outcome.failed("ai_timeout", "The AI provider could not be reached or timed out. Please try again.");
        } catch (RuntimeException e) {
            log.warn("AI review failed: {}", e.toString());
            return Outcome.failed("ai_invalid_response", "The AI reviewer returned a response we could not read. Please try again.");
        }
    }
}
