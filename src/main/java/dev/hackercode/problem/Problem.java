package dev.hackercode.problem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * A practice problem. Records imported from the source PDF keep their {@code sourceNumber}, {@code sourceText}
 * and {@code sourceNote} unchanged forever; anything added later (statement, constraints, examples, tests,
 * difficulty) is tracked separately via {@code contentUpdatedBy} so the UI can label it as non-source content.
 */
@Entity
@Table(name = "problem")
public class Problem {
    public enum Provenance { SOURCE_PDF, ADMIN }

    @Id
    private String id;
    private Integer sourceNumber;
    private int position;
    @Column(nullable = false)
    private String slug;
    @Column(nullable = false)
    private String title;
    private String sourceText;
    private String sourceNote;
    @Column(nullable = false)
    private Long topicId;
    private String subsection;
    private int subsectionPosition;
    private String practiceUrls;
    private String difficulty;
    private String statement;
    private String constraintsText;
    private String examplesText;
    private String testsJson;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provenance provenance;
    private String contentUpdatedBy;
    private boolean archived;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected Problem() {}

    public Problem(String id, Integer sourceNumber, int position, String slug, String title, Long topicId, Provenance provenance) {
        this.id = id;
        this.sourceNumber = sourceNumber;
        this.position = position;
        this.slug = slug;
        this.title = title;
        this.topicId = topicId;
        this.provenance = provenance;
    }

    public List<String> practiceUrlList() {
        if (practiceUrls == null || practiceUrls.isBlank()) return List.of();
        return Arrays.stream(practiceUrls.split("\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public String getId() { return id; }
    public Integer getSourceNumber() { return sourceNumber; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSourceText() { return sourceText; }
    public void setSourceText(String sourceText) { this.sourceText = sourceText; }
    public String getSourceNote() { return sourceNote; }
    public void setSourceNote(String sourceNote) { this.sourceNote = sourceNote; }
    public Long getTopicId() { return topicId; }
    public void setTopicId(Long topicId) { this.topicId = topicId; }
    public String getSubsection() { return subsection; }
    public void setSubsection(String subsection) { this.subsection = subsection; }
    public int getSubsectionPosition() { return subsectionPosition; }
    public void setSubsectionPosition(int subsectionPosition) { this.subsectionPosition = subsectionPosition; }
    public String getPracticeUrls() { return practiceUrls; }
    public void setPracticeUrls(String practiceUrls) { this.practiceUrls = practiceUrls; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }
    public String getConstraintsText() { return constraintsText; }
    public void setConstraintsText(String constraintsText) { this.constraintsText = constraintsText; }
    public String getExamplesText() { return examplesText; }
    public void setExamplesText(String examplesText) { this.examplesText = examplesText; }
    public String getTestsJson() { return testsJson; }
    public void setTestsJson(String testsJson) { this.testsJson = testsJson; }
    public Provenance getProvenance() { return provenance; }
    public String getContentUpdatedBy() { return contentUpdatedBy; }
    public void setContentUpdatedBy(String contentUpdatedBy) { this.contentUpdatedBy = contentUpdatedBy; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = Instant.now(); }
}
