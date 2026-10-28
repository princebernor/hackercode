package dev.hackercode.user;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated user as stored in the security context. It deliberately holds no password hash once
 * authentication has completed, and its role is refreshed from the database on every request
 * (see {@link dev.hackercode.auth.SessionGuardFilter}).
 */
public record HcPrincipal(Long id, String email, Role role, String passwordHash, boolean active) implements UserDetails {

    public static HcPrincipal of(User u) {
        return new HcPrincipal(u.getId(), u.getEmail(), u.getRole(), null, u.getStatus() == User.Status.ACTIVE);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return active; }
    @Override public boolean isAccountNonLocked() { return active; }
}
