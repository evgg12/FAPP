package com.fapp.api;

import com.fapp.persistence.AbstractPostgresTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the API over HTTP against the real PostgreSQL the rest of the suite uses, with
 * the real security filter chain in place — so every test exercises authentication,
 * authorisation, serialisation, validation and the database together.
 *
 * <p>{@link #mockMvc} carries the credentials of the most recently created user as a
 * default header, so an ordinary test does not have to mention authentication at all.
 * A test that needs to act as somebody else calls {@link #authenticateAs} or
 * {@link #unauthenticated}, which is exactly the point where a cross-user check belongs.
 */
abstract class ApiTestSupport extends AbstractPostgresTest {

    /** Long enough to satisfy the registration rule, and not a real password anywhere. */
    protected static final String PASSWORD = "correct-horse-battery-staple";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    /** For assertions about what actually reached the database. */
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    /** Authenticated as whichever user was created or selected most recently. */
    protected MockMvc mockMvc;

    /** Deliberately carries no credentials, for testing what happens without them. */
    protected MockMvc unauthenticated;

    private final Map<String, String> passwordsByEmail = new HashMap<>();

    @BeforeEach
    void emptyTheDatabaseAndResetCredentials() {
        jdbc.execute("TRUNCATE users CASCADE");
        passwordsByEmail.clear();
        unauthenticated = buildMockMvc(null);
        mockMvc = unauthenticated;
    }

    /**
     * Registers a user through the API and authenticates as them.
     *
     * @return the new user's id
     */
    protected String createUser(String email) throws Exception {
        MvcResult result = unauthenticated.perform(MockMvcRequestBuilders.post("/api/users")
                        .contentType("application/json")
                        .content("""
                                {"email": "%s", "displayName": "Test User", "password": "%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        passwordsByEmail.put(email.toLowerCase(), PASSWORD);
        authenticateAs(email);
        return body(result).get("id").asText();
    }

    /** Sends every subsequent {@link #mockMvc} request as this already-registered user. */
    protected void authenticateAs(String email) {
        mockMvc = buildMockMvc(email);
    }

    protected String createAccount(String userId, String provider, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/accounts")
                        .contentType("application/json")
                        .content("""
                                {"userId": "%s", "provider": "%s", "displayName": "%s",
                                 "accountType": "CURRENT", "currency": "GBP"}
                                """.formatted(userId, provider, displayName)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asText();
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected static byte[] fixture(String resource) {
        try (InputStream stream = ApiTestSupport.class.getResourceAsStream(resource)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The real filter chain is added explicitly rather than relying on
     * {@code @AutoConfigureMockMvc}, because the default request that carries the
     * credentials has to be set when the credentials are known, which is per test.
     */
    private MockMvc buildMockMvc(String email) {
        var builder = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain);
        if (email != null) {
            builder = builder.defaultRequest(MockMvcRequestBuilders.get("/")
                    .header(HttpHeaders.AUTHORIZATION, basic(email, passwordsByEmail.get(email.toLowerCase()))));
        }
        return builder.build();
    }

    private static String basic(String email, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((email + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
