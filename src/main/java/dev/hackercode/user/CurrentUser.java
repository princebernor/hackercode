package dev.hackercode.user;

import dev.hackercode.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access to the signed-in user's identity for services and controllers. */
public final class CurrentUser {
    private CurrentUser() {}

    public static HcPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HcPrincipal p) return p;
        throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Please sign in.");
    }

    public static Long id() {
        return get().id();
    }
}
