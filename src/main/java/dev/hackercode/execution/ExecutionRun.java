package dev.hackercode.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One run or submission. Code is stored only for submissions (the user's saved solutions). */
@Entity
@Table(name = "execution_run")
public class ExecutionRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    private String problemId;
    @Column(nullable = false)
    private String language;
    @Column(nullable = false)
    private String kind;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private String provider;
    private String stdout;
    private String stderr;
    private String compileOutput;
    private Integer exitCode;
    private Long timeMs;
    private Long memoryKb;
    private Integer testsPassed;
    private Integer testsTotal;
    private String code;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ExecutionRun() {}

    public ExecutionRun(Long userId, String problemId, String language, String kind, String provider) {
        this.userId = userId;
        this.problemId = problemId;
        this.language = language;
        this.kind = kind;
        this.provider = provider;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getProblemId() { return problemId; }
    public String getLanguage() { return language; }
    public String getKind() { return kind; }
    public String getStatus() { return status; }
    public String getProvider() { return provider; }
    public String getStdout() { return stdout; }
    public String getStderr() { return stderr; }
    public String getCompileOutput() { return compileOutput; }
    public Integer getExitCode() { return exitCode; }
    public Long getTimeMs() { return timeMs; }
    public Long getMemoryKb() { return memoryKb; }
    public Integer getTestsPassed() { return testsPassed; }
    public Integer getTestsTotal() { return testsTotal; }
    public String getCode() { return code; }
    public Instant getCreatedAt() { return createdAt; }

    public void setResult(String status, String stdout, String stderr, String compileOutput, Integer exitCode, Long timeMs, Long memoryKb) {
        this.status = status;
        this.stdout = cap(stdout);
        this.stderr = cap(stderr);
        this.compileOutput = cap(compileOutput);
        this.exitCode = exitCode;
        this.timeMs = timeMs;
        this.memoryKb = memoryKb;
    }

    public void setTests(Integer passed, Integer total) {
        this.testsPassed = passed;
        this.testsTotal = total;
    }

    public void setCode(String code) { this.code = code; }

    private static String cap(String s) {
        return s == null || s.length() <= 20_000 ? s : s.substring(0, 20_000);
    }
}
