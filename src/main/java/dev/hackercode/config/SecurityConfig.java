package dev.hackercode.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hackercode.admin.PlatformSettingsService;
import dev.hackercode.auth.GoogleLoginHandler;
import dev.hackercode.auth.HcRememberMeServices;
import dev.hackercode.auth.SessionGuardFilter;
import dev.hackercode.auth.UserSessionRepository;
import dev.hackercode.common.GlobalExceptionHandler;
import dev.hackercode.common.Tokens;
import dev.hackercode.user.HcPrincipal;
import dev.hackercode.user.User;
import dev.hackercode.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.util.StringUtils;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Content Security Policy: everything is served from our own origin; Monaco needs inline styles and blob workers. */
    static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: https://lh3.googleusercontent.com",
            "font-src 'self' data:",
            "worker-src 'self' blob:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Looks up an account for password authentication (includes the hash). */
    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCase(email)
                .map(u -> new HcPrincipal(u.getId(), u.getEmail(), u.getRole(), u.getPasswordHash(), u.getStatus() == User.Status.ACTIVE))
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService uds, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(uds);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl repo = new JdbcTokenRepositoryImpl();
        repo.setDataSource(dataSource);
        return repo;
    }

    @Bean
    HcRememberMeServices rememberMeServices(UserRepository users, PersistentTokenRepository tokens, AppProperties props,
                                            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
        // Remember-me lookups never load the password hash into the security context.
        UserDetailsService principalLookup = email -> users.findByEmailIgnoreCase(email).map(HcPrincipal::of)
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
        String key = StringUtils.hasText(props.rememberMeKey()) ? props.rememberMeKey() : Tokens.random(32);
        HcRememberMeServices services = new HcRememberMeServices(key, principalLookup, tokens);
        services.setTokenValiditySeconds(30 * 24 * 3600);
        services.setUseSecureCookie(secureCookie);
        services.setCookieName("HC_REMEMBER");
        return services;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ObjectProvider<ClientRegistrationRepository> googleRegistrations,
                                            GoogleLoginHandler googleLoginHandler,
                                            HcRememberMeServices rememberMe,
                                            SecurityContextRepository contextRepository,
                                            UserRepository users,
                                            UserSessionRepository sessions,
                                            RateLimiter rateLimiter,
                                            PlatformSettingsService platform,
                                            ObjectMapper json) throws Exception {
        http
                .securityContext(c -> c.securityContextRepository(contextRepository))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/public/**", "/actuator/health").permitAll()
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .defaultAuthenticationEntryPointFor((req, res, ex) -> writeError(res, json, HttpStatus.UNAUTHORIZED, "unauthenticated", "Please sign in."),
                                new AntPathRequestMatcher("/api/**"))
                        .defaultAccessDeniedHandlerFor((req, res, ex) -> writeError(res, json, HttpStatus.FORBIDDEN, "forbidden",
                                        "You do not have permission to do that, or your session security token expired. Refresh and try again."),
                                new AntPathRequestMatcher("/api/**")))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .rememberMe(r -> r.rememberMeServices(rememberMe))
                .logout(l -> l
                        .logoutUrl("/api/auth/logout")
                        .addLogoutHandler((req, res, auth) -> {
                            var session = req.getSession(false);
                            Object key = session == null ? null : session.getAttribute(SessionGuardFilter.SESSION_KEY_ATTR);
                            if (key != null) sessions.findBySessionKey((String) key).ifPresent(s -> { s.revoke(); sessions.save(s); });
                        })
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK))
                        .deleteCookies("HC_SESSION", "HC_REMEMBER"))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .frameOptions(f -> f.deny())
                        .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=(), payment=()")))
                .addFilterBefore(new AuthRateLimitFilter(rateLimiter, platform, json), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new SessionGuardFilter(users, sessions, rememberMe, contextRepository, json), AnonymousAuthenticationFilter.class)
                .addFilterAfter(new MaintenanceFilter(platform, json), SessionGuardFilter.class);

        if (googleRegistrations.getIfAvailable() != null) {
            http.oauth2Login(o -> o
                    .loginPage("/signin")
                    .successHandler(googleLoginHandler)
                    .failureHandler((req, res, ex) -> res.sendRedirect("/signin?error=oauth")));
        }
        return http.build();
    }

    private static void writeError(HttpServletResponse res, ObjectMapper json, HttpStatus status, String code, String message)
            throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(res.getOutputStream(), GlobalExceptionHandler.body(code, message, null));
    }

    /**
     * CSRF for a single-page app: the token lives in the readable XSRF-TOKEN cookie and is echoed back in the
     * X-XSRF-TOKEN header. BREACH protection (XOR masking) still applies to tokens rendered by the server.
     */
    static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            xor.handle(request, response, csrfToken);
            csrfToken.get(); // render the cookie on every response so the SPA always has a token
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String header = request.getHeader(csrfToken.getHeaderName());
            return (StringUtils.hasText(header) ? plain : xor).resolveCsrfTokenValue(request, csrfToken);
        }
    }
}
