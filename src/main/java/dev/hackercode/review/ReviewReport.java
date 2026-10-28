package dev.hackercode.review;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * The structured review the AI model must return. The Anthropic SDK derives a JSON schema from these records,
 * so responses are validated against it rather than parsed from free text.
 */
public record ReviewReport(
        @JsonPropertyDescription("Two to four sentence overview of the code's quality and its most important problems.")
        String summary,
        @JsonPropertyDescription("Overall quality score from 0 (unusable) to 100 (excellent).")
        int score,
        Complexity complexity,
        @JsonPropertyDescription("Concrete findings, most severe first. Empty if nothing worth reporting.")
        List<Finding> findings,
        @JsonPropertyDescription("Specific things the code does well.")
        List<String> positives,
        @JsonPropertyDescription("Ordered, actionable next steps for the author.")
        List<String> nextSteps,
        @JsonPropertyDescription("An improved version of the full program when it meaningfully helps; otherwise an empty string.")
        String refactoredCode) {

    public enum Severity { critical, high, medium, low, info }

    public enum Category { correctness, security, performance, complexity, readability, best_practices }

    public record Complexity(
            @JsonPropertyDescription("Big-O time complexity of the main algorithm, e.g. O(n log n). Use 'unknown' if it cannot be determined.")
            String time,
            @JsonPropertyDescription("Big-O auxiliary space complexity, e.g. O(n). Use 'unknown' if it cannot be determined.")
            String space,
            @JsonPropertyDescription("One or two sentences justifying the complexity estimate.")
            String explanation,
            @JsonPropertyDescription("A better achievable complexity if one exists, otherwise an empty string.")
            String improvedTime) {}

    public record Finding(
            Category category,
            Severity severity,
            @JsonPropertyDescription("Short title of the issue.")
            String title,
            @JsonPropertyDescription("1-based line number the finding refers to, or 0 if it applies to the whole program.")
            int line,
            @JsonPropertyDescription("What is wrong, referring to the specific code.")
            String explanation,
            @JsonPropertyDescription("Why it matters: the consequence for correctness, security, performance or maintainability.")
            String impact,
            @JsonPropertyDescription("How to fix it, with a short code snippet when useful.")
            String suggestion) {}
}
