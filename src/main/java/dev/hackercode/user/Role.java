package dev.hackercode.user;

/**
 * Server-side roles. The frontend only ever reads these; every permission check happens in the backend.
 * <ul>
 *   <li>USER – ordinary learner</li>
 *   <li>MODERATOR – can curate the problem library and view (not change) user accounts</li>
 *   <li>ADMIN – full operator console: users, roles, platform configuration, audit log</li>
 * </ul>
 */
public enum Role {
    USER, MODERATOR, ADMIN;

    public boolean isStaff() {
        return this == MODERATOR || this == ADMIN;
    }
}
