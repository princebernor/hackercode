package dev.hackercode.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notification")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String title;
    private String body;
    private Instant readAt;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Notification() {}

    public Notification(Long userId, String title, String body) {
        this.userId = userId;
        this.title = title;
        this.body = body;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void markRead() { if (readAt == null) readAt = Instant.now(); }
}
