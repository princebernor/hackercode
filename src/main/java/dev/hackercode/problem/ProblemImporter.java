package dev.hackercode.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reproducible seeding of the placement-training library from {@code data/problems.json}, which is generated
 * from the source PDF by {@code scripts/extract_problems.py} and committed to version control.
 * <p>
 * Importing only inserts records that are missing. It never overwrites a record, so corrections made in the
 * operator console survive restarts, and source fields stay exactly as extracted.
 */
@Service
@Order(1)
public class ProblemImporter implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ProblemImporter.class);
    public static final String SEED_RESOURCE = "data/problems.json";
    public static final int EXPECTED_SOURCE_PROBLEMS = 442;

    public record SeedTopic(String name, List<String> subsections) {}

    public record SeedProblem(String id, int number, int order, String slug, String title, String sourceText,
                              String sourceNote, String topic, String subsection, List<String> practiceUrls) {}

    public record SeedFile(String source, int expectedCount, List<SeedTopic> topics, List<SeedProblem> problems) {}

    public record Report(boolean ok, int expected, int found, List<String> issues, Map<String, Object> stats) {}

    public record ImportResult(int topicsCreated, int problemsCreated, int problemsAlreadyPresent) {}

    private final ProblemRepository problems;
    private final TopicRepository topics;
    private final ProblemCatalog catalog;
    private final ObjectMapper json;

    public ProblemImporter(ProblemRepository problems, TopicRepository topics, ProblemCatalog catalog, ObjectMapper json) {
        this.problems = problems;
        this.topics = topics;
        this.catalog = catalog;
        this.json = json;
    }

    public SeedFile loadSeed() {
        try (InputStream in = new ClassPathResource(SEED_RESOURCE).getInputStream()) {
            return json.readValue(in, SeedFile.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + SEED_RESOURCE, e);
        }
    }

    /** Structural validation of the seed dataset itself (count, numbering, order, IDs, grouping). */
    public Report validateSeed(SeedFile seed) {
        List<String> issues = new ArrayList<>();
        List<SeedProblem> list = seed.problems();
        if (seed.expectedCount() != EXPECTED_SOURCE_PROBLEMS) {
            issues.add("Seed declares " + seed.expectedCount() + " problems; expected " + EXPECTED_SOURCE_PROBLEMS);
        }
        if (list.size() != EXPECTED_SOURCE_PROBLEMS) {
            issues.add("Seed contains " + list.size() + " problems; expected " + EXPECTED_SOURCE_PROBLEMS);
        }
        Map<String, Set<String>> subsectionsByTopic = new HashMap<>();
        for (SeedTopic t : seed.topics()) {
            if (subsectionsByTopic.put(t.name(), new HashSet<>(t.subsections())) != null) issues.add("Duplicate topic: " + t.name());
        }
        Set<String> ids = new HashSet<>();
        Set<String> slugs = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            SeedProblem p = list.get(i);
            int expectedNumber = i + 1;
            if (p.number() != expectedNumber) issues.add("Entry at position " + expectedNumber + " has number " + p.number());
            if (p.order() != expectedNumber) issues.add("Entry #" + p.number() + " has order " + p.order());
            if (!Objects.equals(p.id(), String.format("cf-%03d", p.number()))) issues.add("Entry #" + p.number() + " has unstable id " + p.id());
            if (!ids.add(p.id())) issues.add("Duplicate id " + p.id());
            if (!slugs.add(p.slug())) issues.add("Duplicate slug " + p.slug());
            if (p.title() == null || p.title().isBlank()) issues.add("Entry #" + p.number() + " has no title");
            if (p.sourceText() == null || p.sourceText().isBlank()) issues.add("Entry #" + p.number() + " has no source text");
            Set<String> subs = subsectionsByTopic.get(p.topic());
            if (subs == null) issues.add("Entry #" + p.number() + " references unknown topic " + p.topic());
            else if (!subs.contains(p.subsection())) issues.add("Entry #" + p.number() + " references unknown subsection " + p.subsection());
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("topics", seed.topics().size());
        stats.put("subsections", seed.topics().stream().mapToInt(t -> t.subsections().size()).sum());
        stats.put("withPracticeUrl", list.stream().filter(p -> !p.practiceUrls().isEmpty()).count());
        stats.put("withoutPracticeUrl", list.stream().filter(p -> p.practiceUrls().isEmpty()).count());
        stats.put("withSourceNote", list.stream().filter(p -> p.sourceNote() != null).count());
        return new Report(issues.isEmpty(), EXPECTED_SOURCE_PROBLEMS, list.size(), issues, stats);
    }

    @Override
    public void run(ApplicationArguments args) {
        ImportResult r = importMissing();
        log.info("Problem library: {} created, {} already present, {} topics created", r.problemsCreated(), r.problemsAlreadyPresent(), r.topicsCreated());
    }

    @Transactional
    public ImportResult importMissing() {
        SeedFile seed = loadSeed();
        Report report = validateSeed(seed);
        if (!report.ok()) {
            throw new IllegalStateException("Refusing to import an invalid problem dataset: " + report.issues());
        }
        int topicsCreated = 0;
        Map<String, Topic> byName = new HashMap<>();
        for (Topic t : topics.findAll()) byName.put(t.getName(), t);
        int pos = 0;
        Map<String, List<String>> subsectionOrder = new HashMap<>();
        for (SeedTopic st : seed.topics()) {
            pos++;
            subsectionOrder.put(st.name(), st.subsections());
            if (!byName.containsKey(st.name())) {
                Topic t = topics.save(new Topic(uniqueTopicSlug(slugify(st.name())), st.name(), pos));
                byName.put(st.name(), t);
                topicsCreated++;
            }
        }
        int created = 0;
        int present = 0;
        Set<String> existing = new HashSet<>();
        problems.findAll().forEach(p -> existing.add(p.getId()));
        for (SeedProblem sp : seed.problems()) {
            if (existing.contains(sp.id())) {
                present++;
                continue;
            }
            String slug = problems.existsBySlug(sp.slug()) ? sp.slug() + "-" + sp.number() : sp.slug();
            Problem p = new Problem(sp.id(), sp.number(), sp.order(), slug, sp.title(), byName.get(sp.topic()).getId(), Problem.Provenance.SOURCE_PDF);
            p.setSourceText(sp.sourceText());
            p.setSourceNote(sp.sourceNote());
            p.setSubsection(sp.subsection());
            p.setSubsectionPosition(subsectionOrder.get(sp.topic()).indexOf(sp.subsection()) + 1);
            p.setPracticeUrls(String.join("\n", sp.practiceUrls()));
            problems.save(p);
            created++;
        }
        catalog.invalidate();
        return new ImportResult(topicsCreated, created, present);
    }

    /** Compares the database against the source dataset: missing entries, altered source text, duplicates. */
    @Transactional(readOnly = true)
    public Report validateDatabase() {
        SeedFile seed = loadSeed();
        Report seedReport = validateSeed(seed);
        List<String> issues = new ArrayList<>(seedReport.issues());
        Map<String, Problem> byId = new HashMap<>();
        List<Problem> all = problems.findAll();
        all.forEach(p -> byId.put(p.getId(), p));
        int found = 0;
        List<Integer> missing = new ArrayList<>();
        for (SeedProblem sp : seed.problems()) {
            Problem p = byId.get(sp.id());
            if (p == null) {
                missing.add(sp.number());
                continue;
            }
            found++;
            if (!Objects.equals(p.getSourceNumber(), sp.number())) issues.add(sp.id() + ": source number changed to " + p.getSourceNumber());
            if (!Objects.equals(p.getSourceText(), sp.sourceText())) issues.add(sp.id() + ": source text differs from the PDF extract");
        }
        if (!missing.isEmpty()) issues.add("Missing source entries: " + missing);
        Map<String, List<String>> titles = new HashMap<>();
        for (Problem p : all) {
            if (p.isArchived()) continue;
            titles.computeIfAbsent(p.getTitle().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""), k -> new ArrayList<>()).add(p.getId());
        }
        List<String> duplicates = titles.values().stream().filter(v -> v.size() > 1).map(Object::toString).toList();
        Map<String, Object> stats = new LinkedHashMap<>(seedReport.stats());
        stats.put("databaseTotal", all.size());
        stats.put("sourceEntriesPresent", found);
        stats.put("adminAdded", all.stream().filter(p -> p.getProvenance() == Problem.Provenance.ADMIN).count());
        stats.put("archived", all.stream().filter(Problem::isArchived).count());
        stats.put("withStatement", all.stream().filter(p -> p.getStatement() != null).count());
        stats.put("withTests", all.stream().filter(p -> p.getTestsJson() != null).count());
        stats.put("possibleDuplicateTitles", duplicates);
        return new Report(issues.isEmpty(), EXPECTED_SOURCE_PROBLEMS, found, issues, stats);
    }

    private String uniqueTopicSlug(String base) {
        String slug = base;
        int i = 2;
        while (topics.findBySlug(slug).isPresent()) slug = base + "-" + i++;
        return slug;
    }

    public static String slugify(String text) {
        String s = text.toLowerCase(Locale.ROOT).replaceAll("\\(.*", "").replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.length() > 80) s = s.substring(0, 80).replaceAll("-+$", "");
        return s.isEmpty() ? "item" : s;
    }
}
