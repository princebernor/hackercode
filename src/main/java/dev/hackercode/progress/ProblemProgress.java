package dev.hackercode.progress;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "problem_progress")
public class ProblemProgress {
    public enum Status { NOT_STARTED, ATTEMPTED, SOLVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String problemId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.NOT_STARTED;
    private boolean bookmarked;
    private String notes;
    private Instant lastViewedAt;
    private Instant solvedAt;
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected ProblemProgress() {}

    public ProblemProgress(Long userId, String problemId) {
        this.userId = userId;
        this.problemId = problemId;
    }

    public Long getUserId() { return userId; }
    public String getProblemId() { return problemId; }
    public Status getStatus() { return status; }
    public boolean isBookmarked() { return bookmarked; }
    public String getNotes() { return notes; }
    public Instant getLastViewedAt() { return lastViewedAt; }
    public Instant getSolvedAt() { return solvedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(Status status) {
        this.status = status;
        this.solvedAt = status == Status.SOLVED ? (solvedAt == null ? Instant.now() : solvedAt) : null;
        touch();
    }

    /** Moves NOT_STARTED to ATTEMPTED; never downgrades a solved problem. */
    public void markAttempted() {
        if (status == Status.NOT_STARTED) setStatus(Status.ATTEMPTED);
    }

    public void setBookmarked(boolean bookmarked) { this.bookmarked = bookmarked; touch(); }
    public void setNotes(String notes) { this.notes = notes; touch(); }
    public void viewed() { this.lastViewedAt = Instant.now(); }
    private void touch() { this.updatedAt = Instant.now(); }
}
