package dev.hackercode.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A single-use emailed token (verification or password reset). Only the SHA-256 hash is stored. */
@Entity
@Table(name = "auth_token")
public class AuthToken {
    public enum Type { VERIFY_EMAIL, RESET_PASSWORD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;
    @Column(nullable = false)
    private String tokenHash;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant usedAt;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AuthToken() {}

    public AuthToken(Long userId, Type type, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.type = type;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getUserId() { return userId; }
    public Type getType() { return type; }
    public boolean isUsable() { return usedAt == null && Instant.now().isBefore(expiresAt); }
    public void markUsed() { this.usedAt = Instant.now(); }
}
