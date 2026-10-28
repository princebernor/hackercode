package dev.hackercode.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hackercode.common.ApiException;
import dev.hackercode.execution.Language;
import dev.hackercode.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    public record ReviewRequest(@NotBlank String language,
                                @NotNull @Size(max = ReviewService.MAX_CODE, message = "Reviews are limited to 50,000 characters") String code,
                                @Size(max = 16) String problemId) {}

    public record HistoryItem(Long id, String language, String problemId, String provider, String model, boolean aiGenerated,
                              String summary, Integer score, int findingCount, Instant createdAt) {}

    private final ReviewService service;
    private final CodeReviewRepository reviews;
    private final ObjectMapper json;

    public ReviewController(ReviewService service, CodeReviewRepository reviews, ObjectMapper json) {
        this.service = service;
        this.reviews = reviews;
        this.json = json;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return service.status();
    }

    @PostMapping
    public ReviewService.Response review(@Valid @RequestBody ReviewRequest req) {
        String problemId = req.problemId() == null || req.problemId().isBlank() ? null : req.problemId();
        return service.review(CurrentUser.id(), Language.require(req.language()), req.code(), problemId);
    }

    @GetMapping
    public List<HistoryItem> history() {
        return reviews.findTop20ByUserIdOrderByCreatedAtDesc(CurrentUser.id()).stream()
                .map(r -> new HistoryItem(r.getId(), r.getLanguage(), r.getProblemId(), r.getProvider(), r.getModel(), r.isAiGenerated(),
                        r.getSummary(), r.getScore(), r.getFindingCount(), r.getCreatedAt()))
                .toList();
    }

    /** One saved review, including the reviewed code. Users can only read their own reviews. */
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) throws Exception {
        CodeReview r = reviews.findByIdAndUserId(id, CurrentUser.id()).orElseThrow(() -> ApiException.notFound("Review"));
        JsonNode result = json.readTree(r.getResultJson());
        return Map.of("id", r.getId(), "language", r.getLanguage(), "code", r.getCode(), "createdAt", r.getCreatedAt(),
                "ai", result.path("ai"), "staticChecks", result.path("staticChecks"),
                "disclaimer", "This is a review, not an execution: no code was run and no tests were checked.");
    }
}
