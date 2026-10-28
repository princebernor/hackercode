package dev.hackercode.progress;

import dev.hackercode.activity.ActivityEvent;
import dev.hackercode.activity.ActivityService;
import dev.hackercode.common.ApiException;
import dev.hackercode.problem.ProblemCatalog;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressService {
    public static final int MAX_NOTES = 20_000;

    private final ProblemProgressRepository progress;
    private final ProblemCatalog catalog;
    private final ActivityService activity;

    public ProgressService(ProblemProgressRepository progress, ProblemCatalog catalog, ActivityService activity) {
        this.progress = progress;
        this.catalog = catalog;
        this.activity = activity;
    }

    @Transactional(readOnly = true)
    public Map<String, ProblemProgress> byProblem(Long userId) {
        return progress.findByUserId(userId).stream().collect(Collectors.toMap(ProblemProgress::getProblemId, Function.identity()));
    }

    @Transactional
    public ProblemProgress getOrCreate(Long userId, String problemId) {
        if (!catalog.get().byId().containsKey(problemId)) throw ApiException.notFound("Problem");
        return progress.findByUserIdAndProblemId(userId, problemId)
                .orElseGet(() -> progress.save(new ProblemProgress(userId, problemId)));
    }

    @Transactional
    public ProblemProgress markViewed(Long userId, String problemId) {
        ProblemProgress p = getOrCreate(userId, problemId);
        p.viewed();
        activity.record(userId, ActivityEvent.Type.PROBLEM_VIEWED, problemId, null);
        return p;
    }

    @Transactional
    public ProblemProgress markAttempted(Long userId, String problemId) {
        ProblemProgress p = getOrCreate(userId, problemId);
        p.markAttempted();
        return p;
    }

    @Transactional
    public ProblemProgress update(Long userId, String problemId, ProblemProgress.Status status, Boolean bookmarked, String notes) {
        ProblemProgress p = getOrCreate(userId, problemId);
        if (status != null && status != p.getStatus()) {
            p.setStatus(status);
            if (status == ProblemProgress.Status.SOLVED) activity.record(userId, ActivityEvent.Type.PROBLEM_SOLVED, problemId, null);
        }
        if (bookmarked != null) p.setBookmarked(bookmarked);
        if (notes != null) {
            if (notes.length() > MAX_NOTES) throw ApiException.badRequest("notes_too_long", "Notes are limited to 20,000 characters.");
            p.setNotes(notes.isBlank() ? null : notes);
            activity.record(userId, ActivityEvent.Type.NOTE_SAVED, problemId, null);
        }
        return p;
    }
}
