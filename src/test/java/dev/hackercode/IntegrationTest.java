package dev.hackercode;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hackercode.auth.MailService;
import dev.hackercode.config.RateLimiter;
import dev.hackercode.user.Role;
import dev.hackercode.user.User;
import dev.hackercode.user.UserRepository;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Base for full-stack tests: real Spring context, Flyway schema, the imported 442 problems and MockMvc. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {
    protected static final String PASSWORD = "Str0ng-pass!";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UserRepository users;
    @Autowired protected RateLimiter rateLimiter;
    @MockitoBean protected MailService mail;

    @BeforeEach
    void resetLimits() {
        rateLimiter.reset();
        org.mockito.Mockito.when(mail.mode()).thenReturn(MailService.Mode.DEV_CONSOLE);
        org.mockito.Mockito.when(mail.send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        org.mockito.Mockito.when(mail.link(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> "http://localhost:8080" + inv.getArgument(0));
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@example.com";
    }

    protected String body(Object o) throws Exception {
        return json.writeValueAsString(o);
    }

    protected JsonNode read(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    /** Registers a new account and returns its authenticated session. */
    protected MockHttpSession signUp(String email) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/signup").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", PASSWORD, "acceptTerms", true))))
                .andExpect(status().isOk());
        return session;
    }

    protected MockHttpSession signUpVerified(String email) throws Exception {
        MockHttpSession s = signUp(email);
        User u = users.findByEmailIgnoreCase(email).orElseThrow();
        u.setEmailVerified(true);
        users.save(u);
        return s;
    }

    protected MockHttpSession signUpWithRole(String email, Role role) throws Exception {
        MockHttpSession s = signUpVerified(email);
        User u = users.findByEmailIgnoreCase(email).orElseThrow();
        u.setRole(role);
        users.save(u);
        return s;
    }

    protected ResultActions getAs(MockHttpSession s, String url) throws Exception {
        return mvc.perform(get(url).session(s));
    }

    protected ResultActions postAs(MockHttpSession s, String url, Object payload) throws Exception {
        return mvc.perform(withJson(post(url), payload).session(s).with(csrf()));
    }

    protected ResultActions putAs(MockHttpSession s, String url, Object payload) throws Exception {
        return mvc.perform(withJson(put(url), payload).session(s).with(csrf()));
    }

    protected ResultActions patchAs(MockHttpSession s, String url, Object payload) throws Exception {
        return mvc.perform(withJson(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url), payload).session(s).with(csrf()));
    }

    private MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder b, Object payload) throws Exception {
        return b.contentType(MediaType.APPLICATION_JSON).content(payload == null ? "{}" : body(payload));
    }
}
