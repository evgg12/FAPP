package com.fapp.api;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may reach what.
 *
 * <p>The cases that matter most are the negative ones: an unauthenticated request must
 * reach nothing, and an authenticated one must reach nothing belonging to anybody else.
 * Both are asserted against every kind of user-owned data FAPP holds — accounts,
 * transactions, imports, analytics and goals — because an authorisation gap only has to
 * exist in one place to matter.
 */
class SecurityApiTest extends ApiTestSupport {

    @Test
    void servesTheLivenessProbeWithoutACredential() throws Exception {
        unauthenticated.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void acceptsRegistrationWithoutACredentialBecauseThereIsNoOtherWayIn() throws Exception {
        unauthenticated.perform(post("/api/users")
                        .contentType("application/json")
                        .content("""
                                {"email": "new@example.com", "displayName": "New",
                                 "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated());
    }

    @Test
    void refusesEveryOtherEndpointWithoutACredential() throws Exception {
        String userId = createUser("owner@example.com");
        String accountId = createAccount(userId, "monzo", "Monzo Current");

        for (var request : java.util.List.of(
                get("/api/auth/me"),
                get("/api/users/" + userId),
                get("/api/users/" + userId + "/accounts"),
                get("/api/users/" + userId + "/goals"),
                get("/api/users/" + userId + "/analytics/summary?from=2026-08-01&to=2026-09-01"),
                get("/api/accounts/" + accountId),
                get("/api/accounts/" + accountId + "/transactions"),
                get("/api/imports/" + UUID.randomUUID()),
                post("/api/users/" + userId + "/transactions/recategorise"))) {
            unauthenticated.perform(request).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void refusesAWrongPasswordAndAnUnknownAddress() throws Exception {
        createUser("owner@example.com");

        unauthenticated.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, basic("owner@example.com", "not-the-password")))
                .andExpect(status().isUnauthorized());
        unauthenticated.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, basic("nobody@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void identifiesTheCallerAndNeverReturnsTheirPassword() throws Exception {
        String userId = createUser("owner@example.com");

        var me = body(mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andReturn());

        // Nothing resembling a credential is in any user-shaped response.
        assertThat(me.toString()).doesNotContain("password", "Password", "$2a$", "bcrypt");
        assertThat(me.has("passwordHash")).isFalse();
        assertThat(me.has("password")).isFalse();
    }

    @Test
    void doesNotReturnAPasswordFromRegistrationEither() throws Exception {
        var created = body(unauthenticated.perform(post("/api/users")
                        .contentType("application/json")
                        .content("""
                                {"email": "new@example.com", "displayName": "New", "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(created.toString()).doesNotContain("password", "$2a$");
    }

    @Test
    void storesOnlyAHashOfThePassword() throws Exception {
        createUser("owner@example.com");

        String stored = jdbc().queryForObject(
                "SELECT password_hash FROM users WHERE email = 'owner@example.com'", String.class);

        assertThat(stored).isNotNull().doesNotContain(PASSWORD).startsWith("{bcrypt}");
    }

    @Test
    void refusesOneUsersUserScopedEndpointsToAnother() throws Exception {
        String ownerId = createUser("owner@example.com");
        createUser("stranger@example.com");

        // Authenticated as the stranger, every path naming the owner is forbidden.
        for (var request : java.util.List.of(
                get("/api/users/" + ownerId),
                get("/api/users/" + ownerId + "/accounts"),
                get("/api/users/" + ownerId + "/goals"),
                get("/api/users/" + ownerId + "/analytics/summary?from=2026-08-01&to=2026-09-01"),
                get("/api/users/" + ownerId + "/analytics/categories?from=2026-08-01&to=2026-09-01"),
                get("/api/users/" + ownerId + "/analytics/monthly?from=2026-08-01&to=2026-09-01"),
                get("/api/users/" + ownerId + "/analytics/accounts?from=2026-08-01&to=2026-09-01"),
                get("/api/users/" + ownerId + "/analytics/largest-expenses?from=2026-08-01&to=2026-09-01"),
                get("/api/users/" + ownerId + "/analytics/comparison?from=2026-08-01&to=2026-09-01"),
                post("/api/users/" + ownerId + "/transactions/recategorise"))) {
            mockMvc.perform(request)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    void hidesOneUsersAccountTransactionsAndImportsFromAnother() throws Exception {
        String ownerId = createUser("owner@example.com");
        String accountId = createAccount(ownerId, "monzo", "Monzo Current");
        String importId = body(mockMvc.perform(multipart("/api/accounts/" + accountId + "/statements")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv",
                                fixture("/monzo/statement.csv"))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        createUser("stranger@example.com");

        // Resources reached by their own id report not-found rather than forbidden, so
        // the id cannot be used to confirm that they exist.
        mockMvc.perform(get("/api/accounts/" + accountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        mockMvc.perform(get("/api/accounts/" + accountId + "/transactions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        mockMvc.perform(get("/api/imports/" + importId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMPORT_NOT_FOUND"));
    }

    @Test
    void refusesToLetOneUserImportIntoAnothersAccount() throws Exception {
        String ownerId = createUser("owner@example.com");
        String accountId = createAccount(ownerId, "monzo", "Monzo Current");
        createUser("stranger@example.com");

        mockMvc.perform(multipart("/api/accounts/" + accountId + "/statements")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv",
                                fixture("/monzo/statement.csv"))))
                .andExpect(status().isNotFound());

        // And nothing was imported.
        authenticateAs("owner@example.com");
        mockMvc.perform(get("/api/accounts/" + accountId + "/transactions"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void refusesToLetOneUserOpenAnAccountForAnother() throws Exception {
        String ownerId = createUser("owner@example.com");
        createUser("stranger@example.com");

        mockMvc.perform(post("/api/accounts")
                        .contentType("application/json")
                        .content("""
                                {"userId": "%s", "provider": "monzo", "displayName": "Hijacked",
                                 "accountType": "CURRENT", "currency": "GBP"}
                                """.formatted(ownerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void refusesToLetOneUserReachAnothersGoal() throws Exception {
        String ownerId = createUser("owner@example.com");
        String goalId = body(mockMvc.perform(post("/api/users/" + ownerId + "/goals")
                        .contentType("application/json")
                        .content("""
                                {"name": "Car Fund", "targetAmount": 8000.00, "currency": "GBP",
                                 "targetDate": "2030-06-01"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        String strangerId = createUser("stranger@example.com");

        // Not reachable under the owner's path, which is forbidden outright...
        mockMvc.perform(get("/api/users/" + ownerId + "/goals/" + goalId))
                .andExpect(status().isForbidden());
        // ...nor under the stranger's own path, where it simply is not found.
        mockMvc.perform(get("/api/users/" + strangerId + "/goals/" + goalId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOAL_NOT_FOUND"));
    }

    @Test
    void reportsAnUnauthenticatedRequestWithoutABodyToLeakFrom() throws Exception {
        var response = unauthenticated.perform(get("/api/auth/me")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        // No WWW-Authenticate challenge, so a browser does not raise its own dialog in
        // front of the application.
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
        assertThat(response.getContentAsString()).doesNotContain("Exception", "com.fapp");
    }

    private static String basic(String email, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((email + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
