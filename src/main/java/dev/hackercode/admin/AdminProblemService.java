package dev.hackercode.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hackercode.common.ApiException;
import dev.hackercode.problem.Problem;
import dev.hackercode.problem.ProblemCatalog;
import dev.hackercode.problem.ProblemImporter;
import dev.hackercode.problem.ProblemRepository;
import dev.hackercode.problem.Topic;
import dev.hackercode.problem.TopicRepository;
import dev.hackercode.user.CurrentUser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operator edits to the problem library. Source-provided fields (number, source text, source note) are
 * immutable; edits only touch presentation metadata and operator-added content, and every change is audited.
 */
@Service
public class AdminProblemService {
    private static final Set<String> DIFFICULTIES = Set.of("Easy", "Medium", "Hard");

    public record ProblemEdit(String title, String topicSlug, String subsection, String difficulty, String statement,
                              String constraints, String examples, String testsJson, String practiceUrls) {}

    private final ProblemRepository problems;
    private final TopicRepository topics;
    private final ProblemCatalog catalog;
    private final AuditService audit;
    private final ObjectMapper json;
    private final ProblemImporter importer;
    private volatile Map<String, List<String>> seedUrls;

    public AdminProblemService(ProblemRepository problems, TopicRepository topics, ProblemCatalog catalog, AuditService audit, ObjectMapper json,
                               ProblemImporter importer) {
        this.problems = problems;
        this.topics = topics;
        this.catalog = catalog;
        this.audit = audit;
        this.json = json;
        this.importer = importer;
    }

    @Transactional
    public Problem update(String id, ProblemEdit edit) {
        Problem p = problems.findById(id).orElseThrow(() -> ApiException.notFound("Problem"));
        List<String> changed = apply(p, edit);
        if (!changed.isEmpty()) {
            p.touch();
            catalog.invalidate();
            audit.record("problem.updated", "problem", id, "fields=" + String.join(",", changed));
        }
        return p;
    }

    @Transactional
    public Problem create(ProblemEdit edit) {
        if (edit.title() == null || edit.title().isBlank()) throw ApiException.badRequest("title_required", "Title is required.");
        Topic topic = topic(edit.topicSlug());
        String base = ProblemImporter.slugify(edit.title());
        String slug = base;
        for (int i = 2; problems.existsBySlug(slug); i++) slug = base + "-" + i;
        String id;
        do {
            id = "hc-" + Long.toString(System.nanoTime() % 1_000_000_000L, 36);
        } while (problems.existsById(id));
        Problem p = new Problem(id, null, problems.maxPosition() + 1, slug, edit.title().trim(), topic.getId(), Problem.Provenance.ADMIN);
        apply(p, new ProblemEdit(null, null, edit.subsection(), edit.difficulty(), edit.statement(), edit.constraints(), edit.examples(),
                edit.testsJson(), edit.practiceUrls()));
        problems.save(p);
        catalog.invalidate();
        audit.record("problem.created", "problem", id, edit.title());
        return p;
    }

    @Transactional
    public Problem setArchived(String id, boolean archived) {
        Problem p = problems.findById(id).orElseThrow(() -> ApiException.notFound("Problem"));
        if (p.isArchived() != archived) {
            p.setArchived(archived);
            p.touch();
            catalog.invalidate();
            audit.record(archived ? "problem.archived" : "problem.restored", "problem", id, null);
        }
        return p;
    }

    private Topic topic(String slug) {
        return topics.findBySlug(slug == null ? "" : slug).orElseThrow(() -> ApiException.badRequest("unknown_topic", "Choose an existing topic."));
    }

    private List<String> apply(Problem p, ProblemEdit e) {
        List<String> changed = new ArrayList<>();
        String editor = CurrentUser.get().email();
        if (e.title() != null && !e.title().isBlank() && !e.title().trim().equals(p.getTitle())) {
            if (e.title().length() > 300) throw ApiException.badRequest("title_too_long", "Title is limited to 300 characters.");
            p.setTitle(e.title().trim());
            changed.add("title");
        }
        if (e.topicSlug() != null && !e.topicSlug().isBlank()) {
            Topic t = topic(e.topicSlug());
            if (!t.getId().equals(p.getTopicId())) {
                p.setTopicId(t.getId());
                changed.add("topic");
            }
        }
        if (e.subsection() != null && !Objects.equals(blank(e.subsection()), p.getSubsection())) {
            p.setSubsection(blank(e.subsection()));
            changed.add("subsection");
        }
        if (e.difficulty() != null) {
            String d = blank(e.difficulty());
            if (d != null && !DIFFICULTIES.contains(d)) throw ApiException.badRequest("invalid_difficulty", "Difficulty must be Easy, Medium or Hard.");
            if (!Objects.equals(d, p.getDifficulty())) {
                p.setDifficulty(d);
                changed.add("difficulty");
            }
        }
        if (e.statement() != null && !Objects.equals(limit(e.statement(), 20000, "Statement"), p.getStatement())) {
            p.setStatement(limit(e.statement(), 20000, "Statement"));
            changed.add("statement");
        }
        if (e.constraints() != null && !Objects.equals(limit(e.constraints(), 5000, "Constraints"), p.getConstraintsText())) {
            p.setConstraintsText(limit(e.constraints(), 5000, "Constraints"));
            changed.add("constraints");
        }
        if (e.examples() != null && !Objects.equals(limit(e.examples(), 10000, "Examples"), p.getExamplesText())) {
            p.setExamplesText(limit(e.examples(), 10000, "Examples"));
            changed.add("examples");
        }
        if (e.testsJson() != null && !Objects.equals(blank(e.testsJson()), p.getTestsJson())) {
            p.setTestsJson(validateTests(blank(e.testsJson())));
            changed.add("tests");
        }
        if (e.practiceUrls() != null) {
            List<String> urls = Arrays.stream(e.practiceUrls().split("\\s*\n\\s*")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            for (String u : urls) {
                if (!u.startsWith("https://") || u.length() > 500) throw ApiException.badRequest("invalid_url", "Practice links must be https:// URLs.");
            }
            // Links that came from the PDF can be supplemented but never removed.
            if (!urls.containsAll(sourceUrls(p))) {
                throw ApiException.badRequest("source_url_removed", "Links that came from the source PDF cannot be removed.");
            }
            String joined = String.join("\n", urls);
            if (!joined.equals(p.getPracticeUrls() == null ? "" : p.getPracticeUrls())) {
                p.setPracticeUrls(joined);
                changed.add("practiceUrls");
            }
        }
        if (changed.stream().anyMatch(c -> !c.equals("title") && !c.equals("topic") && !c.equals("subsection"))) {
            p.setContentUpdatedBy(editor);
        }
        return changed;
    }

    private List<String> sourceUrls(Problem p) {
        if (p.getProvenance() != Problem.Provenance.SOURCE_PDF) return List.of();
        Map<String, List<String>> seed = seedUrls;
        if (seed == null) {
            seed = importer.loadSeed().problems().stream()
                    .collect(Collectors.toMap(ProblemImporter.SeedProblem::id, ProblemImporter.SeedProblem::practiceUrls));
            seedUrls = seed;
        }
        return seed.getOrDefault(p.getId(), List.of());
    }

    private String validateTests(String testsJson) {
        if (testsJson == null) return null;
        try {
            JsonNode node = json.readTree(testsJson);
            if (!node.isArray() || node.size() > 20) throw ApiException.badRequest("invalid_tests", "Tests must be a JSON array of at most 20 cases.");
            for (JsonNode t : node) {
                if (!t.isObject() || !t.has("input") || !t.has("output")) {
                    throw ApiException.badRequest("invalid_tests", "Each test needs \"input\" and \"output\" (and optional \"hidden\").");
                }
            }
            return json.writeValueAsString(node);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("invalid_tests", "Tests are not valid JSON.");
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String limit(String s, int max, String field) {
        String v = blank(s);
        if (v != null && v.length() > max) throw ApiException.badRequest("too_long", field + " is limited to " + max + " characters.");
        return v;
    }

    static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
