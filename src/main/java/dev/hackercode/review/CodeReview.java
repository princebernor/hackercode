package dev.hackercode.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "code_review")
public class CodeReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    private String problemId;
    @Column(nullable = false)
    private String language;
    @Column(nullable = false)
    private String provider;
    private String model;
    private boolean aiGenerated;
    private String summary;
    private Integer score;
    private int findingCount;
    @Column(nullable = false)
    private String resultJson;
    @Column(nullable = false)
    private String code;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected CodeReview() {}

    public CodeReview(Long userId, String problemId, String language, String provider, String model, boolean aiGenerated,
                      String summary, Integer score, int findingCount, String resultJson, String code) {
        this.userId = userId;
        this.problemId = problemId;
        this.language = language;
        this.provider = provider;
        this.model = model;
        this.aiGenerated = aiGenerated;
        this.summary = summary != null && summary.length() > 4000 ? summary.substring(0, 4000) : summary;
        this.score = score;
        this.findingCount = findingCount;
        this.resultJson = resultJson;
        this.code = code;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getProblemId() { return problemId; }
    public String getLanguage() { return language; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public boolean isAiGenerated() { return aiGenerated; }
    public String getSummary() { return summary; }
    public Integer getScore() { return score; }
    public int getFindingCount() { return findingCount; }
    public String getResultJson() { return resultJson; }
    public String getCode() { return code; }
    public Instant getCreatedAt() { return createdAt; }
}
