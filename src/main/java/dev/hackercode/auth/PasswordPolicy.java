package dev.hackercode.auth;

import dev.hackercode.common.ApiException;
import java.util.Locale;
import java.util.Set;

/** Server-side password rules. The client shows the same rules, but only this check is authoritative. */
public final class PasswordPolicy {
    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password123", "12345678", "123456789", "1234567890", "qwerty123",
            "iloveyou1", "admin123", "welcome1", "letmein1", "abc12345", "passw0rd", "hackercode1");

    private PasswordPolicy() {}

    public static void check(String password, String email) {
        if (password == null || password.length() < 8) {
            throw ApiException.badRequest("weak_password", "Password must be at least 8 characters.");
        }
        if (password.length() > 128) {
            throw ApiException.badRequest("weak_password", "Password must be at most 128 characters.");
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digitOrSymbol = password.chars().anyMatch(c -> !Character.isLetter(c));
        if (!letter || !digitOrSymbol) {
            throw ApiException.badRequest("weak_password", "Use letters plus at least one number or symbol.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) {
            throw ApiException.badRequest("weak_password", "That password is too common. Choose something less guessable.");
        }
        if (email != null) {
            String local = email.toLowerCase(Locale.ROOT).split("@")[0];
            if (local.length() >= 4 && lower.contains(local)) {
                throw ApiException.badRequest("weak_password", "Password must not contain your email name.");
            }
        }
    }
}
