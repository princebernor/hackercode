package dev.hackercode.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_event")
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long actorId;
    private String actorEmail;
    @Column(nullable = false)
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private String ipHint;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AuditEvent() {}

    public AuditEvent(Long actorId, String actorEmail, String action, String targetType, String targetId, String detail, String ipHint) {
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.ipHint = ipHint;
    }

    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getDetail() { return detail; }
    public String getIpHint() { return ipHint; }
    public Instant getCreatedAt() { return createdAt; }
}
