package dev.hackercode.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.hackercode.IntegrationTest;
import dev.hackercode.auth.PasswordPolicy;
import dev.hackercode.common.ApiException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class SettingsTest extends IntegrationTest {

    @Test
    void preferencesPersistAndAreRestoredAfterSignIn() throws Exception {
        String email = uniqueEmail("prefs");
        MockHttpSession s = signUp(email);
        ObjectNode prefs = (ObjectNode) read(getAs(s, "/api/account/preferences").andReturn());
        ((ObjectNode) prefs.get("appearance")).put("theme", "dark");
        ((ObjectNode) prefs.get("editor")).put("fontSize", 18).put("defaultLanguage", "go").put("tabSize", 2);
        ((ObjectNode) prefs.get("privacy")).put("trackActivity", false);
        putAs(s, "/api/account/preferences", prefs).andExpect(status().isOk()).andExpect(jsonPath("$.editor.fontSize").value(18));

        MockHttpSession again = new MockHttpSession();
        postAs(again, "/api/auth/login", Map.of("email", email, "password", PASSWORD)).andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.appearance.theme").value("dark"))
                .andExpect(jsonPath("$.preferences.editor.defaultLanguage").value("go"))
                .andExpect(jsonPath("$.preferences.editor.tabSize").value(2));
    }

    @Test
    void invalidPreferenceValuesAreRejected() throws Exception {
        MockHttpSession s = signUp(uniqueEmail("badprefs"));
        JsonNode base = read(getAs(s, "/api/account/preferences").andReturn());
        ObjectNode bad = base.deepCopy();
        ((ObjectNode) bad.get("appearance")).put("theme", "neon");
        putAs(s, "/api/account/preferences", bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("invalid_setting"));
        bad = base.deepCopy();
        ((ObjectNode) bad.get("editor")).put("fontSize", 99);
        putAs(s, "/api/account/preferences", bad).andExpect(status().isBadRequest());
        bad = base.deepCopy();
        ((ObjectNode) bad.get("editor")).put("defaultLanguage", "rust");
        putAs(s, "/api/account/preferences", bad).andExpect(status().isBadRequest());
        bad = base.deepCopy();
        ((ObjectNode) bad.get("review")).putArray("categories");
        putAs(s, "/api/account/preferences", bad).andExpect(status().isBadRequest());
    }

    @Test
    void resetRestoresDefaults() throws Exception {
        MockHttpSession s = signUp(uniqueEmail("reset-prefs"));
        ObjectNode prefs = (ObjectNode) read(getAs(s, "/api/account/preferences").andReturn());
        ((ObjectNode) prefs.get("editor")).put("fontSize", 22);
        putAs(s, "/api/account/preferences", prefs).andExpect(status().isOk());
        postAs(s, "/api/account/preferences/reset", null).andExpect(jsonPath("$.editor.fontSize").value(14));
    }

    @Test
    void activityTrackingOptOutIsHonoured() throws Exception {
        MockHttpSession s = signUp(uniqueEmail("optout"));
        ObjectNode prefs = (ObjectNode) read(getAs(s, "/api/account/preferences").andReturn());
        ((ObjectNode) prefs.get("privacy")).put("trackActivity", false);
        putAs(s, "/api/account/preferences", prefs).andExpect(status().isOk());
        getAs(s, "/api/problems/cf-003").andExpect(status().isOk());
        JsonNode dash = read(getAs(s, "/api/dashboard").andReturn());
        assertThat(dash.get("recentActivity")).isEmpty();
    }

    @Test
    void profileAndOnboardingUpdates() throws Exception {
        MockHttpSession s = signUp(uniqueEmail("profile"));
        patchAs(s, "/api/account/profile", Map.of("displayName", "Asha", "avatarUrl", "javascript:alert(1)")).andExpect(status().isBadRequest());
        patchAs(s, "/api/account/profile", Map.of("displayName", "Asha", "avatarUrl", "")).andExpect(jsonPath("$.displayName").value("Asha"));
        postAs(s, "/api/account/onboarding", null).andExpect(jsonPath("$.onboardingCompleted").value(true));
    }

    @Test
    void dataExportContainsUserDataButNoSecrets() throws Exception {
        MockHttpSession s = signUp(uniqueEmail("export"));
        putAs(s, "/api/problems/cf-004/progress", Map.of("notes", "reverse digits")).andExpect(status().isOk());
        String export = getAs(s, "/api/account/export").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(export).contains("reverse digits").doesNotContain("passwordHash").doesNotContain("$2a$").doesNotContain("$2b$");
    }

    @Test
    void passwordPolicy() {
        PasswordPolicy.check("Str0ng-pass!", "someone@example.com");
        assertThatThrownBy(() -> PasswordPolicy.check("short1", null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("onlyletters", null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("Password123", null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("prince2026!", "prince@example.com")).isInstanceOf(ApiException.class);
    }
}
