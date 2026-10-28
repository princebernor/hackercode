package dev.hackercode.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code hackercode.*} settings in application.yml (all sourced from environment variables). */
@ConfigurationProperties("hackercode")
public record AppProperties(
        String baseUrl,
        String mailFrom,
        String bootstrapAdminEmail,
        String rememberMeKey,
        Google google,
        Execution execution,
        Review review) {

    public record Google(String clientId, String clientSecret) {
        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    public record Execution(
            String provider,
            int timeoutSeconds,
            int memoryMb,
            int maxOutputBytes,
            String judge0Url,
            String judge0AuthHeader,
            String judge0AuthToken,
            String judge0RapidapiHost,
            String pistonUrl,
            String dockerCommand) {
    }

    public record Review(
            String provider,
            String anthropicApiKey,
            String anthropicModel,
            String openaiBaseUrl,
            String openaiApiKey,
            String openaiModel,
            int timeoutSeconds) {
    }
}
