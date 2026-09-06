package com.fapp.api;

import com.fapp.persistence.AbstractPostgresTest;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the API over HTTP against the real PostgreSQL the rest of the suite uses, so
 * every test exercises serialisation, validation, status codes and the database
 * together rather than any of them in isolation.
 */
@AutoConfigureMockMvc
abstract class ApiTestSupport extends AbstractPostgresTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyTheDatabase() {
        jdbc.execute("TRUNCATE users CASCADE");
    }

    /** Creates a user through the API and returns its id. */
    protected String createUser(String email) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/users")
                        .contentType("application/json")
                        .content("""
                                {"email": "%s", "displayName": "Test User"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asText();
    }

    /** Creates an account through the API and returns its id. */
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
}
