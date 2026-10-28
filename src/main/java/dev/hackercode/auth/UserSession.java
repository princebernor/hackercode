package dev.hackercode.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One signed-in browser/device. Lets users see their sessions and sign other devices out. */
@Entity
@Table(name = "user_session")
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    /** Random key kept in the HTTP session; unrelated to the servlet session id so it survives id rotation. */
    @Column(nullable = false)
    private String sessionKey;
    private String userAgent;
    private String ipHint;
    private boolean rememberMe;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant lastSeenAt = Instant.now();
    private Instant revokedAt;

    protected UserSession() {}

    public UserSession(Long userId, String sessionKey, String userAgent, String ipHint, boolean rememberMe) {
        this.userId = userId;
        this.sessionKey = sessionKey;
        this.userAgent = userAgent;
        this.ipHint = ipHint;
        this.rememberMe = rememberMe;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getSessionKey() { return sessionKey; }
    public String getUserAgent() { return userAgent; }
    public String getIpHint() { return ipHint; }
    public boolean isRememberMe() { return rememberMe; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void seen() { this.lastSeenAt = Instant.now(); }
    public void revoke() { if (revokedAt == null) revokedAt = Instant.now(); }
}
