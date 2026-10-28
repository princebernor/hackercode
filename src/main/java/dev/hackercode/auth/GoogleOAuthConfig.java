package dev.hackercode.auth;

import dev.hackercode.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Registers Google as an OAuth 2.0 / OpenID Connect provider only when both GOOGLE_CLIENT_ID and
 * GOOGLE_CLIENT_SECRET are set. Without them the "Continue with Google" button explains what is missing.
 */
@Configuration
@ConditionalOnExpression("!'${hackercode.google.client-id:}'.isBlank() and !'${hackercode.google.client-secret:}'.isBlank()")
public class GoogleOAuthConfig {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(AppProperties props) {
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(props.google().clientId())
                .clientSecret(props.google().clientSecret())
                .scope("openid", "profile", "email")
                .build());
    }
}
