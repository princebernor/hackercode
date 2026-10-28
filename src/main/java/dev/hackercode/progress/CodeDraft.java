package dev.hackercode.progress;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The latest code a user typed for one problem (or the free-form review workspace) in one language. */
@Entity
@Table(name = "code_draft")
public class CodeDraft {
    /** Draft key used for the AI review workspace, which is not tied to a problem. */
    public static final String WORKSPACE = "workspace";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String draftKey;
    @Column(nullable = false)
    private String language;
    @Column(nullable = false)
    private String code;
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected CodeDraft() {}

    public CodeDraft(Long userId, String draftKey, String language, String code) {
        this.userId = userId;
        this.draftKey = draftKey;
        this.language = language;
        this.code = code;
    }

    public String getDraftKey() { return draftKey; }
    public String getLanguage() { return language; }
    public String getCode() { return code; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String code) {
        this.code = code;
        this.updatedAt = Instant.now();
    }
}
