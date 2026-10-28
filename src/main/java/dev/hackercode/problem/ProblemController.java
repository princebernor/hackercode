package dev.hackercode.problem;

import dev.hackercode.common.PageResponse;
import dev.hackercode.problem.ProblemViews.Detail;
import dev.hackercode.problem.ProblemViews.Progress;
import dev.hackercode.problem.ProblemViews.Summary;
import dev.hackercode.problem.ProblemViews.TopicStats;
import dev.hackercode.progress.ProblemProgress;
import dev.hackercode.progress.ProgressService;
import dev.hackercode.user.CurrentUser;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProblemController {

    public record ProgressUpdate(ProblemProgress.Status status, Boolean bookmarked, @Size(max = 20000) String notes) {}

    private final ProblemService problems;
    private final ProgressService progress;

    public ProblemController(ProblemService problems, ProgressService progress) {
        this.problems = problems;
        this.progress = progress;
    }

    @GetMapping("/topics")
    public List<TopicStats> topics() {
        return problems.topics(CurrentUser.id());
    }

    @GetMapping("/problems")
    public PageResponse<Summary> list(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) String topic,
                                      @RequestParam(required = false) String subsection,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) Boolean bookmarked,
                                      @RequestParam(required = false) String difficulty,
                                      @RequestParam(defaultValue = "number") String sort,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        String query = q == null ? null : q.length() > 100 ? q.substring(0, 100) : q;
        return problems.search(CurrentUser.id(), new ProblemService.Query(query, topic, subsection, status, bookmarked, difficulty, sort, false, page, size));
    }

    @GetMapping("/problems/{idOrSlug}")
    public Detail detail(@PathVariable String idOrSlug) {
        return problems.detail(CurrentUser.id(), idOrSlug, CurrentUser.get().role().isStaff());
    }

    @PutMapping("/problems/{id}/progress")
    public Progress updateProgress(@PathVariable String id, @RequestBody ProgressUpdate body) {
        return Progress.of(progress.update(CurrentUser.id(), id, body.status(), body.bookmarked(), body.notes()));
    }
}
