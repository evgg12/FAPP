package com.fapp.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiJsonEndpointIsAccessible() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"))
                .andExpect(content().string(containsString("\"title\":\"FAPP API\"")))
                .andExpect(content().string(containsString("\"description\":\"Financial Analysis & Planning Platform REST API\"")))
                .andExpect(content().string(containsString("\"version\":\"0.0.1\"")));
    }

    @Test
    void swaggerUiHtmlIsAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void openApiContainsAuthenticationRequirement() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("basicAuth")))
                .andExpect(content().string(containsString("HTTP Basic authentication")));
    }

    @Test
    void openApiDocumentsUserEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/users")))
                .andExpect(content().string(containsString("Register a new user")));
    }

    @Test
    void openApiDocumentsAccountEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/accounts")))
                .andExpect(content().string(containsString("Create a new account")));
    }

    @Test
    void openApiDocumentsAuthenticationEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/auth/me")))
                .andExpect(content().string(containsString("Get authenticated user info")));
    }

    @Test
    void openApiDocumentsAnalyticsEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/users/{userId}/analytics/summary")))
                .andExpect(content().string(containsString("Get financial summary")));
    }

    @Test
    void openApiIncludesSecurityRequirementOnProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("basicAuth")));
    }

    @Test
    void swaggerUiResourcesAreAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }
}
