package dev.hackercode.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

/**
 * Persistent-token remember-me (series + rotating token, stored server side) driven by the JSON login
 * endpoint rather than a form parameter.
 */
public class HcRememberMeServices extends PersistentTokenBasedRememberMeServices {
    public static final String REQUESTED_ATTR = "hc.rememberMeRequested";

    public HcRememberMeServices(String key, UserDetailsService userDetailsService, PersistentTokenRepository tokenRepository) {
        super(key, userDetailsService, tokenRepository);
    }

    @Override
    protected boolean rememberMeRequested(HttpServletRequest request, String parameter) {
        return Boolean.TRUE.equals(request.getAttribute(REQUESTED_ATTR));
    }
}
