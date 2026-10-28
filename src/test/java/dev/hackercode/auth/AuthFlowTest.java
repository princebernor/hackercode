package dev.hackercode.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hackercode.IntegrationTest;
import dev.hackercode.user.User;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class AuthFlowTest extends IntegrationTest {

    private MockHttpSession login(String email, String password, boolean expectOk) throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", password))))
                .andExpect(expectOk ? status().isOk() : status().is4xxClientError());
        return s;
    }

    private String lastLinkToken(String path) {
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(anyString(), anyString(), bodyCaptor.capture());
        String body = bodyCaptor.getAllValues().get(bodyCaptor.getAllValues().size() - 1);
        Matcher m = Pattern.compile(Pattern.quote(path) + "\\?token=([A-Za-z0-9_-]+)").matcher(body);
        assertThat(m.find()).as("email body contains %s link", path).isTrue();
        return m.group(1);
    }

    @Test
    void signupValidatesInputOnTheServer() throws Exception {
        postAs(new MockHttpSession(), "/api/auth/signup", Map.of("email", "not-an-email", "password", PASSWORD, "acceptTerms", true))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.email").exists());
        postAs(new MockHttpSession(), "/api/auth/signup", Map.of("email", uniqueEmail("t"), "password", PASSWORD, "acceptTerms", false))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.acceptTerms").exists());
        postAs(new MockHttpSession(), "/api/auth/signup", Map.of("email", uniqueEmail("t"), "password", "short", "acceptTerms", true))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("weak_password"));
        postAs(new MockHttpSession(), "/api/auth/signup", Map.of("email", uniqueEmail("t"), "password", "password123", "acceptTerms", true))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("weak_password"));
    }

    @Test
    void signupCreatesAHashedAccountAndSession() throws Exception {
        String email = uniqueEmail("New.User");
        MockHttpSession s = signUp(email);
        getAs(s, "/api/auth/me").andExpect(jsonPath("$.user.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.preferences.editor.defaultLanguage").value("java"));
        User u = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(u.getPasswordHash()).startsWith("$2").doesNotContain(PASSWORD);
        verify(mail).send(eq(email.toLowerCase()), eq("Verify your HackerCode email"), anyString());
        // Duplicate registration is refused (case-insensitively).
        postAs(new MockHttpSession(), "/api/auth/signup", Map.of("email", email.toUpperCase(), "password", PASSWORD, "acceptTerms", true))
                .andExpect(status().isConflict());
    }

    @Test
    void stateChangingRequestsRequireCsrfToken() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", "a@b.co", "password", "x")))).andExpect(status().isForbidden());
    }

    @Test
    void loginRejectsBadCredentialsGenerically() throws Exception {
        String email = uniqueEmail("login");
        signUp(email);
        postAs(new MockHttpSession(), "/api/auth/login", Map.of("email", email, "password", "Wrong-pass1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("invalid_credentials"));
        postAs(new MockHttpSession(), "/api/auth/login", Map.of("email", uniqueEmail("ghost"), "password", "Wrong-pass1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.message").value("Incorrect email or password."));
        MockHttpSession s = login(email, PASSWORD, true);
        getAs(s, "/api/problems?size=1").andExpect(status().isOk());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession s = signUp(uniqueEmail("logout"));
        getAs(s, "/api/dashboard").andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(s).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard").session(s)).andExpect(status().isUnauthorized());
    }

    @Test
    void emailVerificationTokenWorksOnce() throws Exception {
        String email = uniqueEmail("verify");
        signUp(email);
        String token = lastLinkToken("/verify-email");
        postAs(new MockHttpSession(), "/api/auth/verify-email", Map.of("token", token)).andExpect(status().isOk());
        assertThat(users.findByEmailIgnoreCase(email).orElseThrow().isEmailVerified()).isTrue();
        postAs(new MockHttpSession(), "/api/auth/verify-email", Map.of("token", token)).andExpect(status().isBadRequest());
        postAs(new MockHttpSession(), "/api/auth/verify-email", Map.of("token", "forged")).andExpect(status().isBadRequest());
    }

    @Test
    void passwordResetChangesPasswordAndSignsOutOtherSessions() throws Exception {
        String email = uniqueEmail("reset");
        MockHttpSession existing = signUp(email);
        getAs(existing, "/api/dashboard").andExpect(status().isOk());

        // Unknown emails get the same response, so accounts cannot be enumerated.
        postAs(new MockHttpSession(), "/api/auth/forgot-password", Map.of("email", uniqueEmail("nobody"))).andExpect(status().isOk());
        postAs(new MockHttpSession(), "/api/auth/forgot-password", Map.of("email", email)).andExpect(status().isOk());
        String token = lastLinkToken("/reset-password");

        postAs(new MockHttpSession(), "/api/auth/reset-password", Map.of("token", token, "password", "weak"))
                .andExpect(status().isBadRequest());
        postAs(new MockHttpSession(), "/api/auth/reset-password", Map.of("token", token, "password", "Brand-new-pass9"))
                .andExpect(status().isOk());
        postAs(new MockHttpSession(), "/api/auth/reset-password", Map.of("token", token, "password", "Another-pass9"))
                .andExpect(status().isBadRequest());

        getAs(existing, "/api/dashboard").andExpect(status().isUnauthorized());
        login(email, PASSWORD, false);
        login(email, "Brand-new-pass9", true);
    }

    @Test
    void suspendedAccountsAreBlockedImmediately() throws Exception {
        String email = uniqueEmail("suspend");
        MockHttpSession s = signUp(email);
        User u = users.findByEmailIgnoreCase(email).orElseThrow();
        u.setStatus(User.Status.SUSPENDED);
        users.save(u);
        getAs(s, "/api/dashboard").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("account_unavailable"));
        postAs(new MockHttpSession(), "/api/auth/login", Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("account_suspended"));
    }

    @Test
    void usersCanSignOutTheirOtherDevices() throws Exception {
        String email = uniqueEmail("devices");
        MockHttpSession first = signUp(email);
        MockHttpSession second = login(email, PASSWORD, true);
        getAs(first, "/api/dashboard").andExpect(status().isOk());
        getAs(second, "/api/dashboard").andExpect(status().isOk());
        assertThat(read(getAs(second, "/api/account/sessions").andReturn())).hasSize(2);
        postAs(second, "/api/account/sessions/revoke-others", null).andExpect(jsonPath("$.revoked").value(1));
        getAs(first, "/api/dashboard").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("session_ended"));
        getAs(second, "/api/dashboard").andExpect(status().isOk());
    }

    @Test
    void passwordChangeRequiresCurrentPassword() throws Exception {
        String email = uniqueEmail("change");
        MockHttpSession s = signUp(email);
        postAs(s, "/api/account/password", Map.of("currentPassword", "nope-nope1", "newPassword", "Changed-pass7"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("wrong_password"));
        postAs(s, "/api/account/password", Map.of("currentPassword", PASSWORD, "newPassword", "Changed-pass7")).andExpect(status().isOk());
        getAs(s, "/api/dashboard").andExpect(status().isOk());
        login(email, "Changed-pass7", true);
    }

    @Test
    void authEndpointsAreRateLimited() throws Exception {
        int last = 0;
        for (int i = 0; i < 12; i++) {
            last = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("email", "flood@example.com", "password", "x")))).andReturn().getResponse().getStatus();
        }
        assertThat(last).isEqualTo(429);
    }

    @Test
    void googleSignInReportsMissingConfiguration() throws Exception {
        mvc.perform(get("/api/auth/config")).andExpect(jsonPath("$.googleEnabled").value(false))
                .andExpect(jsonPath("$.googleSetupHint").exists());
        mvc.perform(get("/api/auth/google/start?next=//evil.example")).andExpect(status().is3xxRedirection());
    }

    @Test
    void safeRedirectRejectsOpenRedirects() {
        assertThat(SafeRedirect.path("/problems?q=1", "/d")).isEqualTo("/problems?q=1");
        assertThat(SafeRedirect.path("//evil.com", "/d")).isEqualTo("/d");
        assertThat(SafeRedirect.path("https://evil.com", "/d")).isEqualTo("/d");
        assertThat(SafeRedirect.path("/\\evil.com", "/d")).isEqualTo("/d");
        assertThat(SafeRedirect.path(null, "/d")).isEqualTo("/d");
    }

    @Test
    void accountDeletionRequiresConfirmationAndPassword() throws Exception {
        String email = uniqueEmail("delete");
        MockHttpSession s = signUp(email);
        postAs(s, "/api/account/delete", Map.of("password", PASSWORD, "confirm", "yes")).andExpect(status().isBadRequest());
        postAs(s, "/api/account/delete", Map.of("password", "wrong-pass1", "confirm", "DELETE")).andExpect(status().isBadRequest());
        assertThat(users.findByEmailIgnoreCase(email)).isPresent();
        postAs(s, "/api/account/delete", Map.of("password", PASSWORD, "confirm", "DELETE")).andExpect(status().isOk());
        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
    }
}
