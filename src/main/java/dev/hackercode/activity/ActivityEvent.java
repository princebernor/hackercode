package dev.hackercode.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** A learning event used for streaks, the activity heatmap and "recent activity". Kept for one year. */
@Entity
@Table(name = "activity_event")
public class ActivityEvent {
    public enum Type { PROBLEM_VIEWED, CODE_RUN, CODE_SUBMITTED, PROBLEM_SOLVED, REVIEW_REQUESTED, NOTE_SAVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;
    private String problemId;
    private String language;
    @Column(nullable = false)
    private LocalDate occurredOn = LocalDate.now(ZoneOffset.UTC);
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ActivityEvent() {}

    public ActivityEvent(Long userId, Type type, String problemId, String language) {
        this.userId = userId;
        this.type = type;
        this.problemId = problemId;
        this.language = language;
    }

    public Long getUserId() { return userId; }
    public Type getType() { return type; }
    public String getProblemId() { return problemId; }
    public String getLanguage() { return language; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public Instant getCreatedAt() { return createdAt; }
}
