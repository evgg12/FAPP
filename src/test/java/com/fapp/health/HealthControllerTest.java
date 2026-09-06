package com.fapp.health;

import com.fapp.persistence.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs against the whole application rather than a web slice, because the point of a
 * liveness probe is that it answers on the real filter chain — including the security
 * one, which must let it through without a credential.
 */
@AutoConfigureMockMvc
class HealthControllerTest extends AbstractPostgresTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsUpStatusAsJsonWithoutACredential() {
        try {
            mockMvc.perform(get("/api/health"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.application").value("fapp"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void rejectsUnsupportedMethod() {
        try {
            mockMvc.perform(post("/api/health")).andExpect(status().isMethodNotAllowed());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
