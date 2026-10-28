package dev.hackercode.problem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hackercode.common.ApiException;
import dev.hackercode.common.PageResponse;
import dev.hackercode.problem.ProblemViews.AddedContent;
import dev.hackercode.problem.ProblemViews.Detail;
import dev.hackercode.problem.ProblemViews.Progress;
import dev.hackercode.problem.ProblemViews.Source;
import dev.hackercode.problem.ProblemViews.SubsectionStats;
import dev.hackercode.problem.ProblemViews.Summary;
import dev.hackercode.problem.ProblemViews.TopicRef;
import dev.hackercode.problem.ProblemViews.TopicStats;
import dev.hackercode.progress.ProblemProgress;
import dev.hackercode.progress.ProgressService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class ProblemService {
    public static final int MAX_PAGE_SIZE = 100;

    public record Query(String q, String topic, String subsection, String status, Boolean bookmarked, String difficulty,
                        String sort, boolean includeArchived, int page, int size) {}

    private final ProblemCatalog catalog;
    private final ProgressService progress;
    private final ObjectMapper json;

    public ProblemService(ProblemCatalog catalog, ProgressService progress, ObjectMapper json) {
        this.catalog = catalog;
        this.progress = progress;
        this.json = json;
    }

    public PageResponse<Summary> search(Long userId, Query query) {
        ProblemCatalog.Snapshot s = catalog.get();
        Map<String, ProblemProgress> mine = userId == null ? Map.of() : progress.byProblem(userId);
        Stream<Problem> stream = s.problems().stream().filter(matcher(s, mine, query));
        if ("title".equals(query.sort())) stream = stream.sorted(Comparator.comparing(p -> p.getTitle().toLowerCase(Locale.ROOT)));
        else if ("recent".equals(query.sort())) {
            stream = stream.sorted(Comparator.comparing((Problem p) -> {
                ProblemProgress pr = mine.get(p.getId());
                return pr == null || pr.getLastViewedAt() == null ? java.time.Instant.EPOCH : pr.getLastViewedAt();
            }).reversed());
        }
        List<Problem> matched = stream.toList();
        int size = Math.max(1, Math.min(MAX_PAGE_SIZE, query.size()));
        int page = Math.max(0, query.page());
        int from = Math.min(matched.size(), page * size);
        int to = Math.min(matched.size(), from + size);
        List<Summary> items = matched.subList(from, to).stream()
                .map(p -> Summary.of(p, s.topicsById().get(p.getTopicId()), mine.get(p.getId()))).toList();
        return new PageResponse<>(items, page, size, matched.size(), (matched.size() + size - 1) / size);
    }

    private Predicate<Problem> matcher(ProblemCatalog.Snapshot s, Map<String, ProblemProgress> mine, Query query) {
        List<Predicate<Problem>> checks = new ArrayList<>();
        if (!query.includeArchived()) checks.add(p -> !p.isArchived());
        if (query.topic() != null && !query.topic().isBlank()) {
            Topic t = s.topicsBySlug().get(query.topic());
            Long topicId = t == null ? -1L : t.getId();
            checks.add(p -> p.getTopicId().equals(topicId));
        }
        if (query.subsection() != null && !query.subsection().isBlank()) checks.add(p -> query.subsection().equals(p.getSubsection()));
        if (query.difficulty() != null && !query.difficulty().isBlank()) checks.add(p -> query.difficulty().equalsIgnoreCase(p.getDifficulty()));
        if (query.status() != null && !query.status().isBlank()) {
            String st = query.status().toUpperCase(Locale.ROOT);
            checks.add(p -> {
                ProblemProgress pr = mine.get(p.getId());
                return (pr == null ? "NOT_STARTED" : pr.getStatus().name()).equals(st);
            });
        }
        if (Boolean.TRUE.equals(query.bookmarked())) checks.add(p -> {
            ProblemProgress pr = mine.get(p.getId());
            return pr != null && pr.isBookmarked();
        });
        if (query.q() != null && !query.q().isBlank()) {
            String q = query.q().trim().toLowerCase(Locale.ROOT);
            String digits = q.startsWith("#") ? q.substring(1) : q;
            Integer number = digits.matches("\\d{1,4}") ? Integer.valueOf(digits) : null;
            String[] terms = q.split("\\s+");
            checks.add(p -> {
                if (number != null && number.equals(p.getSourceNumber())) return true;
                Topic t = s.topicsById().get(p.getTopicId());
                String haystack = (p.getTitle() + " " + nz(p.getSourceText()) + " " + nz(p.getSubsection()) + " "
                        + (t == null ? "" : t.getName()) + " " + p.getId()).toLowerCase(Locale.ROOT);
                for (String term : terms) if (!haystack.contains(term)) return false;
                return true;
            });
        }
        return p -> checks.stream().allMatch(c -> c.test(p));
    }

    private static String nz(String s) { return s == null ? "" : s; }

    public Detail detail(Long userId, String idOrSlug, boolean staff) {
        ProblemCatalog.Snapshot s = catalog.get();
        Problem p = catalog.find(idOrSlug).orElseThrow(() -> ApiException.notFound("Problem"));
        if (p.isArchived() && !staff) throw ApiException.notFound("Problem");
        Map<String, ProblemProgress> mine = progress.byProblem(userId);
        ProblemProgress viewed = progress.markViewed(userId, p.getId());
        Topic topic = s.topicsById().get(p.getTopicId());
        List<Problem> visible = s.problems().stream().filter(x -> !x.isArchived()).toList();
        int idx = visible.indexOf(p);
        Summary prev = idx > 0 ? Summary.of(visible.get(idx - 1), s.topicsById().get(visible.get(idx - 1).getTopicId()), mine.get(visible.get(idx - 1).getId())) : null;
        Summary next = idx >= 0 && idx < visible.size() - 1 ? Summary.of(visible.get(idx + 1), s.topicsById().get(visible.get(idx + 1).getTopicId()), mine.get(visible.get(idx + 1).getId())) : null;

        // Related = other problems in the same subsection (same pattern), then the same topic, nearest first.
        List<Problem> related = visible.stream()
                .filter(x -> !x.getId().equals(p.getId()) && x.getTopicId().equals(p.getTopicId()))
                .sorted(Comparator.comparing((Problem x) -> !java.util.Objects.equals(x.getSubsection(), p.getSubsection()))
                        .thenComparing(x -> Math.abs(x.getPosition() - p.getPosition())))
                .limit(6).toList();

        Source source = p.getProvenance() == Problem.Provenance.SOURCE_PDF
                ? new Source("CodeSheet File.pdf", p.getSourceNumber(), p.getSourceText(), p.getSourceNote(), topic.getName(), p.getSubsection(), p.practiceUrlList())
                : null;
        AddedContent added = null;
        int tests = countTests(p.getTestsJson());
        if (p.getStatement() != null || p.getConstraintsText() != null || p.getExamplesText() != null || p.getDifficulty() != null || tests > 0
                || (p.getProvenance() == Problem.Provenance.ADMIN && !p.practiceUrlList().isEmpty())) {
            added = new AddedContent(p.getStatement(), p.getConstraintsText(), p.getExamplesText(), p.getDifficulty(), tests, p.getContentUpdatedBy());
        }
        Labels sub = Labels.of(p.getSubsection());
        return new Detail(p.getId(), p.getSourceNumber(), p.getSlug(), p.getTitle(), TopicRef.of(topic), p.getSubsection(),
                sub.label(), sub.note(), p.getProvenance().name(), p.isArchived(), source, added, Progress.of(viewed), prev, next,
                related.stream().map(x -> Summary.of(x, s.topicsById().get(x.getTopicId()), mine.get(x.getId()))).toList());
    }

    public int countTests(String testsJson) {
        if (testsJson == null || testsJson.isBlank()) return 0;
        try {
            JsonNode node = json.readTree(testsJson);
            return node.isArray() ? node.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public List<TopicStats> topics(Long userId) {
        ProblemCatalog.Snapshot s = catalog.get();
        Map<String, ProblemProgress> mine = userId == null ? Map.of() : progress.byProblem(userId);
        List<TopicStats> out = new ArrayList<>();
        for (Topic t : s.topics()) {
            List<Problem> inTopic = s.problems().stream().filter(p -> !p.isArchived() && p.getTopicId().equals(t.getId())).toList();
            Map<String, int[]> subs = new LinkedHashMap<>();
            int solved = 0;
            int attempted = 0;
            for (Problem p : inTopic) {
                ProblemProgress pr = mine.get(p.getId());
                boolean isSolved = pr != null && pr.getStatus() == ProblemProgress.Status.SOLVED;
                if (isSolved) solved++;
                if (pr != null && pr.getStatus() == ProblemProgress.Status.ATTEMPTED) attempted++;
                int[] c = subs.computeIfAbsent(p.getSubsection() == null ? "" : p.getSubsection(), k -> new int[2]);
                c[0]++;
                if (isSolved) c[1]++;
            }
            List<SubsectionStats> subStats = subs.entrySet().stream().map(e -> {
                Labels l = Labels.of(e.getKey());
                return new SubsectionStats(e.getKey(), l.label(), l.note(), e.getValue()[0], e.getValue()[1]);
            }).toList();
            Labels l = Labels.of(t.getName());
            out.add(new TopicStats(t.getSlug(), t.getName(), l.label(), l.kind(), l.note(), t.getPosition(), inTopic.size(), solved, attempted, subStats));
        }
        return out;
    }
}
