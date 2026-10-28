package dev.hackercode.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.hackercode.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

class ProblemApiTest extends IntegrationTest {
    @Autowired ProblemImporter importer;
    @Autowired ProblemRepository problems;

    @Test
    void libraryIsSeededAndValidatedOnStartup() {
        assertThat(problems.count()).isGreaterThanOrEqualTo(442);
        ProblemImporter.Report report = importer.validateDatabase();
        assertThat(report.ok()).as(String.valueOf(report.issues())).isTrue();
        assertThat(report.found()).isEqualTo(442);
    }

    @Test
    void reimportIsIdempotent() {
        ProblemImporter.ImportResult r = importer.importMissing();
        assertThat(r.problemsCreated()).isZero();
        assertThat(r.problemsAlreadyPresent()).isEqualTo(442);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/problems")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }

    @Test
    void listsInSheetOrderWithPagination() throws Exception {
        MockHttpSession s = signUp(uniqueEmail("list"));
        JsonNode page = read(getAs(s, "/api/problems?size=25&page=1").andExpect(status().isOk()).andReturn());
        assertThat(page.get("total").asInt()).isEqualTo(442);
        assertThat(page.get("items")).hasSize(25);
        assertThat(page.get("items").get(0).get("number").asInt()).isEqualTo(26);
        // Page size is capped so the whole catalog is never sent at once.
        JsonNode capped = read(getAs(s, "/api/problems?size=5000").andReturn());
        assertThat(capped.get("items")).hasSize(ProblemService.MAX_PAGE_SIZE);
    }

    @Test
    void searchesByTitleNumberTopicAndKeyword() throws Exception {
        MockHttpSession s = signUp(uniqueEmail("search"));
        assertThat(titles(s, "/api/problems?q=kadane")).containsExactly("Kadane's algo(super imp)");
        JsonNode byHash = read(mvc.perform(get("/api/problems").param("q", "#442").session(s)).andReturn());
        assertThat(byHash.get("items").get(0).get("number").asInt()).isEqualTo(442);
        assertThat(read(getAs(s, "/api/problems?q=38").andReturn()).get("items").get(0).get("number").asInt()).isEqualTo(38);
        JsonNode graphs = read(getAs(s, "/api/problems?topic=graphs-data-structure&size=100").andReturn());
        assertThat(graphs.get("total").asInt()).isGreaterThan(30);
        graphs.get("items").forEach(p -> assertThat(p.get("topic").get("label").asText()).isEqualTo("Graphs"));
        // Keyword search spans topics: the sheet lists this problem under Arrays (#36) and Stacks (#134).
        JsonNode trapping = read(mvc.perform(get("/api/problems").param("q", "trapping rain").session(s)).andReturn());
        assertThat(trapping.get("total").asInt()).isEqualTo(2);
        assertThat(trapping.get("items").get(1).get("number").asInt()).isEqualTo(134);
        assertThat(read(getAs(s, "/api/problems?q=zzzz-not-a-problem").andReturn()).get("total").asInt()).isZero();
    }

    @Test
    void topicsExposeSubsectionsAndCounts() throws Exception {
        MockHttpSession s = signUp(uniqueEmail("topics"));
        JsonNode topics = read(getAs(s, "/api/topics").andExpect(status().isOk()).andReturn());
        assertThat(topics).hasSize(18);
        int total = 0;
        for (JsonNode t : topics) total += t.get("total").asInt();
        assertThat(total).isEqualTo(442);
        assertThat(topics.get(1).get("subsections").get(1).get("label").asText()).isEqualTo("Two pointer");
    }

    @Test
    void detailSeparatesSourceDataFromAddedContent() throws Exception {
        MockHttpSession s = signUp(uniqueEmail("detail"));
        JsonNode d = read(getAs(s, "/api/problems/cf-038").andExpect(status().isOk()).andReturn());
        assertThat(d.get("source").get("number").asInt()).isEqualTo(38);
        assertThat(d.get("source").get("document").asText()).isEqualTo("CodeSheet File.pdf");
        assertThat(d.has("added")).as("no fabricated statement for title-only entries").isFalse();
        assertThat(d.get("previous").get("number").asInt()).isEqualTo(37);
        assertThat(d.get("next").get("number").asInt()).isEqualTo(39);
        assertThat(d.get("related").get(0).get("subsection").asText()).isEqualTo("Must Solve");
        getAs(s, "/api/problems/does-not-exist").andExpect(status().isNotFound());
    }

    @Test
    void bookmarksStatusAndNotesPersist() throws Exception {
        MockHttpSession s = signUp(uniqueEmail("progress"));
        putAs(s, "/api/problems/cf-010/progress", Map.of("bookmarked", true, "status", "SOLVED", "notes", "two pointers"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SOLVED"));
        JsonNode bm = read(getAs(s, "/api/problems?bookmarked=true").andReturn());
        assertThat(bm.get("total").asInt()).isEqualTo(1);
        assertThat(read(getAs(s, "/api/problems?status=SOLVED").andReturn()).get("items").get(0).get("id").asText()).isEqualTo("cf-010");
        JsonNode detail = read(getAs(s, "/api/problems/cf-010").andReturn());
        assertThat(detail.get("progress").get("notes").asText()).isEqualTo("two pointers");
        assertThat(detail.get("progress").get("bookmarked").asBoolean()).isTrue();
        putAs(s, "/api/problems/cf-010/progress", Map.of("notes", "x".repeat(20_001))).andExpect(status().isBadRequest());
        putAs(s, "/api/problems/cf-010/progress", Map.of("status", "NOPE")).andExpect(status().isBadRequest());

        // Progress is per user.
        MockHttpSession other = signUp(uniqueEmail("other"));
        assertThat(read(getAs(other, "/api/problems?bookmarked=true").andReturn()).get("total").asInt()).isZero();
    }

    @Test
    void draftsAreSavedPerProblemAndLanguage() throws Exception {
        MockHttpSession s = signUp(uniqueEmail("drafts"));
        putAs(s, "/api/drafts", Map.of("draftKey", "cf-001", "language", "go", "code", "package main")).andExpect(status().isOk());
        putAs(s, "/api/drafts", Map.of("draftKey", "cf-001", "language", "c", "code", "int main(){}")).andExpect(status().isOk());
        JsonNode drafts = read(getAs(s, "/api/drafts?draftKey=cf-001").andReturn());
        assertThat(drafts).hasSize(2);
        putAs(s, "/api/drafts", Map.of("draftKey", "cf-001", "language", "rust", "code", "fn main(){}")).andExpect(status().isBadRequest());
        putAs(s, "/api/drafts", Map.of("draftKey", "nope", "language", "go", "code", "x")).andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/drafts?draftKey=cf-001&language=go")
                .session(s).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
        assertThat(read(getAs(s, "/api/drafts?draftKey=cf-001").andReturn())).hasSize(1);
    }

    @Test
    void dashboardReflectsRealActivityOnly() throws Exception {
        MockHttpSession s = signUp(uniqueEmail("dash"));
        JsonNode fresh = read(getAs(s, "/api/dashboard").andExpect(status().isOk()).andReturn());
        assertThat(fresh.get("isNew").asBoolean()).isTrue();
        assertThat(fresh.get("totals").get("solved").asInt()).isZero();
        assertThat(fresh.get("streak").get("current").asInt()).isZero();
        putAs(s, "/api/problems/cf-002/progress", Map.of("status", "SOLVED")).andExpect(status().isOk());
        JsonNode after = read(getAs(s, "/api/dashboard").andReturn());
        assertThat(after.get("totals").get("solved").asInt()).isEqualTo(1);
        assertThat(after.get("streak").get("current").asInt()).isEqualTo(1);
        assertThat(after.get("goals").get("solvedToday").asInt()).isEqualTo(1);
    }

    private java.util.List<String> titles(MockHttpSession s, String url) throws Exception {
        java.util.List<String> out = new java.util.ArrayList<>();
        read(getAs(s, url).andReturn()).get("items").forEach(p -> out.add(p.get("title").asText()));
        return out;
    }
}
