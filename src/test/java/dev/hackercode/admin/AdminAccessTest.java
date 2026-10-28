package dev.hackercode.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.hackercode.IntegrationTest;
import dev.hackercode.user.Role;
import dev.hackercode.user.User;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpSession;

class AdminAccessTest extends IntegrationTest {
    @Autowired AuditRepository audit;
    @Autowired PlatformSettingsService platform;

    @Test
    void regularUsersCannotReachTheOperatorConsole() throws Exception {
        MockHttpSession user = signUp(uniqueEmail("plain"));
        getAs(user, "/api/admin/overview").andExpect(status().isForbidden());
        getAs(user, "/api/admin/users").andExpect(status().isForbidden());
        putAs(user, "/api/admin/config/maintenance.enabled", Map.of("value", "true")).andExpect(status().isForbidden());
    }

    @Test
    void roleChangesTakeEffectOnTheNextRequest() throws Exception {
        String email = uniqueEmail("promoted");
        MockHttpSession s = signUp(email);
        getAs(s, "/api/admin/overview").andExpect(status().isForbidden());
        User u = users.findByEmailIgnoreCase(email).orElseThrow();
        u.setRole(Role.MODERATOR);
        users.save(u);
        getAs(s, "/api/admin/overview").andExpect(status().isOk());
    }

    @Test
    void moderatorsCanCurateProblemsButNotManageAccountsOrConfig() throws Exception {
        MockHttpSession mod = signUpWithRole(uniqueEmail("mod"), Role.MODERATOR);
        String targetEmail = uniqueEmail("target");
        signUp(targetEmail);
        Long target = users.findByEmailIgnoreCase(targetEmail).orElseThrow().getId();

        getAs(mod, "/api/admin/overview").andExpect(status().isOk()).andExpect(jsonPath("$.library.valid").value(true));
        getAs(mod, "/api/admin/users?q=" + targetEmail).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        patchAs(mod, "/api/admin/users/" + target + "/role", Map.of("role", "ADMIN")).andExpect(status().isForbidden());
        patchAs(mod, "/api/admin/users/" + target + "/status", Map.of("status", "SUSPENDED")).andExpect(status().isForbidden());
        putAs(mod, "/api/admin/config/feature.signup", Map.of("value", "false")).andExpect(status().isForbidden());
        getAs(mod, "/api/admin/audit").andExpect(status().isForbidden());

        putAs(mod, "/api/admin/problems/cf-005", Map.of("difficulty", "Easy", "statement", "Compute x to the power n."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.difficulty").value("Easy"));
        JsonNode detail = read(getAs(mod, "/api/problems/cf-005").andReturn());
        assertThat(detail.get("added").get("statement").asText()).isEqualTo("Compute x to the power n.");
        assertThat(detail.get("source").get("text").asText()).isEqualTo("Find power of a number");
    }

    @Test
    void sourceProvenanceIsProtected() throws Exception {
        MockHttpSession admin = signUpWithRole(uniqueEmail("prov"), Role.ADMIN);
        putAs(admin, "/api/admin/problems/cf-001", Map.of("practiceUrls", "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("source_url_removed"));
        putAs(admin, "/api/admin/problems/cf-001", Map.of("testsJson", "{not json")).andExpect(status().isBadRequest());
        putAs(admin, "/api/admin/problems/cf-001", Map.of("difficulty", "Impossible")).andExpect(status().isBadRequest());
    }

    @Test
    void adminActionsAreAuditedAndGuarded() throws Exception {
        String adminEmail = uniqueEmail("admin");
        MockHttpSession admin = signUpWithRole(adminEmail, Role.ADMIN);
        Long adminId = users.findByEmailIgnoreCase(adminEmail).orElseThrow().getId();
        String targetEmail = uniqueEmail("victim");
        MockHttpSession victim = signUp(targetEmail);
        Long target = users.findByEmailIgnoreCase(targetEmail).orElseThrow().getId();

        patchAs(admin, "/api/admin/users/" + target + "/status", Map.of("status", "SUSPENDED", "reason", "spam"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        getAs(victim, "/api/dashboard").andExpect(status().isUnauthorized());
        patchAs(admin, "/api/admin/users/" + adminId + "/status", Map.of("status", "SUSPENDED"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("self_change"));

        patchAs(admin, "/api/admin/users/" + target + "/role", Map.of("role", "MODERATOR")).andExpect(status().isOk());

        var events = audit.search(null, "admin.", PageRequest.of(0, 50)).getContent();
        assertThat(events).extracting(AuditEvent::getAction).contains("admin.user_suspended", "admin.role_changed");
        assertThat(events).filteredOn(e -> e.getAction().equals("admin.user_suspended")).first()
                .satisfies(e -> {
                    assertThat(e.getActorEmail()).isEqualTo(adminEmail.toLowerCase());
                    assertThat(e.getDetail()).contains("spam");
                });
    }

    @Test
    void lastAdministratorCannotBeDemoted() throws Exception {
        long admins = users.countByRole(Role.ADMIN);
        if (admins > 0) {
            // Other tests created admins; demote them so this one is the only one.
            users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).forEach(u -> { u.setRole(Role.USER); users.save(u); });
        }
        String email = uniqueEmail("solo");
        MockHttpSession admin = signUpWithRole(email, Role.ADMIN);
        Long id = users.findByEmailIgnoreCase(email).orElseThrow().getId();
        patchAs(admin, "/api/admin/users/" + id + "/role", Map.of("role", "USER"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("last_admin"));
    }

    @Test
    void platformConfigurationIsValidatedAndAudited() throws Exception {
        MockHttpSession admin = signUpWithRole(uniqueEmail("cfg"), Role.ADMIN);
        putAs(admin, "/api/admin/config/rateLimit.runsPerMinute", Map.of("value", "abc")).andExpect(status().isBadRequest());
        putAs(admin, "/api/admin/config/does.not.exist", Map.of("value", "1")).andExpect(status().isBadRequest());
        putAs(admin, "/api/admin/config/languages.enabled", Map.of("value", "c,rust")).andExpect(status().isBadRequest());
        putAs(admin, "/api/admin/config/rateLimit.runsPerMinute", Map.of("value", "15")).andExpect(status().isOk());
        assertThat(platform.number(PlatformSettingsService.RATE_RUN)).isEqualTo(15);
        assertThat(audit.search("config.changed", null, PageRequest.of(0, 10)).getContent()).isNotEmpty();
        putAs(admin, "/api/admin/config/rateLimit.runsPerMinute", Map.of("value", "20")).andExpect(status().isOk());
    }

    @Test
    void maintenanceModeMakesTheApiReadOnlyForLearners() throws Exception {
        MockHttpSession admin = signUpWithRole(uniqueEmail("maint"), Role.ADMIN);
        MockHttpSession learner = signUp(uniqueEmail("learner"));
        putAs(admin, "/api/admin/config/maintenance.enabled", Map.of("value", "true")).andExpect(status().isOk());
        try {
            putAs(learner, "/api/problems/cf-001/progress", Map.of("bookmarked", true)).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value("maintenance"));
            getAs(learner, "/api/problems?size=1").andExpect(status().isOk());
            putAs(admin, "/api/problems/cf-001/progress", Map.of("bookmarked", true)).andExpect(status().isOk());
        } finally {
            putAs(admin, "/api/admin/config/maintenance.enabled", Map.of("value", "false")).andExpect(status().isOk());
        }
    }

    @Test
    void configResponseNeverLeaksSecrets() throws Exception {
        MockHttpSession admin = signUpWithRole(uniqueEmail("secret"), Role.ADMIN);
        String config = getAs(admin, "/api/admin/config").andReturn().getResponse().getContentAsString();
        assertThat(config).doesNotContainIgnoringCase("apiKey").doesNotContainIgnoringCase("client-secret").doesNotContain("password");
    }

    @Test
    void adminCanAddAndArchiveProblems() throws Exception {
        MockHttpSession admin = signUpWithRole(uniqueEmail("curator"), Role.ADMIN);
        JsonNode created = read(postAs(admin, "/api/admin/problems", Map.of("title", "Staff added problem", "topicSlug", "basics"))
                .andExpect(status().isOk()).andReturn());
        String id = created.get("id").asText();
        postAs(admin, "/api/admin/problems/" + id + "/archive", Map.of("archived", true)).andExpect(status().isOk());
        assertThat(created.get("provenance").asText()).isEqualTo("ADMIN");
        assertThat(created.path("number").isMissingNode()).as("staff-added problems have no source number").isTrue();
        MockHttpSession learner = signUp(uniqueEmail("viewer"));
        getAs(learner, "/api/problems/" + id).andExpect(status().isNotFound());
        assertThat(read(getAs(learner, "/api/problems?q=Staff added").andReturn()).get("total").asInt()).isZero();
        getAs(admin, "/api/admin/problems/validation").andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
    }
}
