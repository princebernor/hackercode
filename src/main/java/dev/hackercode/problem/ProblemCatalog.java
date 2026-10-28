package dev.hackercode.problem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Read-optimised, in-memory snapshot of the problem library (a few hundred rows). Rebuilt lazily after any
 * import or edit, so list/search requests never hit the database for catalog data.
 */
@Component
public class ProblemCatalog {

    public record Snapshot(List<Problem> problems, Map<String, Problem> byId, Map<String, Problem> bySlug,
                           List<Topic> topics, Map<Long, Topic> topicsById, Map<String, Topic> topicsBySlug) {}

    private final ProblemRepository problems;
    private final TopicRepository topics;
    private volatile Snapshot snapshot;

    public ProblemCatalog(ProblemRepository problems, TopicRepository topics) {
        this.problems = problems;
        this.topics = topics;
    }

    public Snapshot get() {
        Snapshot s = snapshot;
        if (s == null) {
            synchronized (this) {
                s = snapshot;
                if (s == null) {
                    s = build();
                    snapshot = s;
                }
            }
        }
        return s;
    }

    public void invalidate() {
        snapshot = null;
    }

    public Optional<Problem> find(String idOrSlug) {
        Snapshot s = get();
        Problem p = s.byId().get(idOrSlug);
        return Optional.ofNullable(p != null ? p : s.bySlug().get(idOrSlug));
    }

    private Snapshot build() {
        List<Problem> list = problems.findAllByOrderByPositionAsc();
        List<Topic> ts = topics.findAllByOrderByPositionAsc();
        return new Snapshot(
                Collections.unmodifiableList(list),
                list.stream().collect(Collectors.toMap(Problem::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new)),
                list.stream().collect(Collectors.toMap(Problem::getSlug, Function.identity(), (a, b) -> a, LinkedHashMap::new)),
                Collections.unmodifiableList(ts),
                ts.stream().collect(Collectors.toMap(Topic::getId, Function.identity())),
                ts.stream().collect(Collectors.toMap(Topic::getSlug, Function.identity())));
    }
}
