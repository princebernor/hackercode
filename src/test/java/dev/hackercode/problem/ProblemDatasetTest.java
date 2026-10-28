package dev.hackercode.problem;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hackercode.problem.ProblemImporter.SeedFile;
import dev.hackercode.problem.ProblemImporter.SeedProblem;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Verifies the committed dataset extracted from "CodeSheet File.pdf" without starting Spring. */
class ProblemDatasetTest {
    static SeedFile seed;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = new ClassPathResource(ProblemImporter.SEED_RESOURCE).getInputStream()) {
            seed = new ObjectMapper().readValue(in, SeedFile.class);
        }
    }

    @Test
    void containsExactly442EntriesNumberedInSourceOrder() {
        assertThat(seed.problems()).hasSize(442);
        for (int i = 0; i < seed.problems().size(); i++) {
            SeedProblem p = seed.problems().get(i);
            assertThat(p.number()).as("number at index %d", i).isEqualTo(i + 1);
            assertThat(p.order()).isEqualTo(i + 1);
        }
    }

    @Test
    void idsAndSlugsAreStableAndUnique() {
        Set<String> ids = new HashSet<>();
        Set<String> slugs = new HashSet<>();
        for (SeedProblem p : seed.problems()) {
            assertThat(p.id()).isEqualTo(String.format("cf-%03d", p.number()));
            assertThat(ids.add(p.id())).isTrue();
            assertThat(slugs.add(p.slug())).as("slug %s", p.slug()).isTrue();
            assertThat(p.title()).isNotBlank();
            assertThat(p.sourceText()).isNotBlank().startsWith(p.title());
        }
    }

    @Test
    void preservesTheEighteenSourceTopicsInOrder() {
        List<String> labels = seed.topics().stream().map(t -> Labels.of(t.name()).label()).toList();
        assertThat(labels).containsExactly(
                "Basics", "Array", "Matrix", "Recursion", "Binary Search", "Sorting", "LinkedList", "Stack", "Queue",
                "Sliding Window", "Trees", "Backtracking & Recursion advanced", "Graphs", "Greedy", "Dynamic Programming",
                "Heaps", "Trie", "String");
    }

    @Test
    void everyEntryBelongsToADeclaredTopicAndSubsection() {
        for (SeedProblem p : seed.problems()) {
            var topic = seed.topics().stream().filter(t -> t.name().equals(p.topic())).findFirst();
            assertThat(topic).as("topic of #%d", p.number()).isPresent();
            assertThat(topic.get().subsections()).as("subsection of #%d", p.number()).contains(p.subsection());
        }
    }

    @Test
    void knownEntriesMatchThePdf() {
        assertThat(entry(1).title()).isEqualTo("Find even or odd");
        assertThat(entry(1).topic()).isEqualTo("Basics");
        assertThat(entry(38).sourceText()).isEqualTo("Kadane's algo(super imp)");
        assertThat(entry(38).subsection()).isEqualTo("Must Solve");
        assertThat(entry(442).title()).isEqualTo("Roman numerals to decimals");
        assertThat(entry(442).topic()).isEqualTo("String");
        // Explanatory notes from the sheet are retained.
        assertThat(entry(3).sourceText()).contains("Solving above last digit prob");
        assertThat(entry(297).sourceNote()).isEqualTo("Super imp, dont skip");
    }

    @Test
    void practiceUrlsComeOnlyFromThePdf() {
        long withUrl = seed.problems().stream().filter(p -> !p.practiceUrls().isEmpty()).count();
        assertThat(withUrl).isEqualTo(424);
        assertThat(entry(13).practiceUrls()).as("'What is an array?' has no link in the PDF").isEmpty();
        seed.problems().forEach(p -> p.practiceUrls().forEach(u -> assertThat(u).startsWith("https://")));
    }

    @Test
    void importerValidationAcceptsTheDataset() {
        ProblemImporter importer = new ProblemImporter(null, null, null, new ObjectMapper());
        ProblemImporter.Report report = importer.validateSeed(seed);
        assertThat(report.issues()).isEmpty();
        assertThat(report.ok()).isTrue();
        assertThat(report.stats()).containsEntry("topics", 18);
    }

    private static SeedProblem entry(int number) {
        return seed.problems().get(number - 1);
    }
}
