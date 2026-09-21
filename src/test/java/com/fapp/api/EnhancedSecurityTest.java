package com.fapp.api;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Additional security scenarios beyond what SecurityApiTest covers.
 *
 * <p>Focuses on authentication edge cases, malformed credentials, write operation
 * protection, and comprehensive cross-user access control testing.
 */
class EnhancedSecurityTest extends ApiTestSupport {

    // ============ Malformed Authentication Headers ============

    @Test
    void refusesRequestsWithMalformedBasicAuthHeaders() throws Exception {
        createUser("owner@example.com");

        // Invalid base64 encoding
        unauthenticated.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Basic !!!invalid-base64!!!"))
                .andExpect(status().isUnauthorized());

        // Missing scheme (credentials without "Basic " prefix)
        unauthenticated.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "dmlzaWJsZTppbnZpc2libGU="))
                .andExpect(status().isUnauthorized());

        // Wrong scheme
        unauthenticated.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer dmlzaWJsZTppbnZpc2libGU="))
                .andExpect(status().isUnauthorized());

        // Completely empty value
        unauthenticated.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, ""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesRequestsWithEmptyOrNullCredentials() throws Exception {
        createUser("owner@example.com");

        // Empty username
        unauthenticated.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, basic("", PASSWORD)))
                .andExpect(status().isUnauthorized());

        // Empty password
        unauthenticated.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, basic("owner@example.com", "")))
                .andExpect(status().isUnauthorized());

        // Both empty
        unauthenticated.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, basic("", "")))
                .andExpect(status().isUnauthorized());

        // Whitespace-only username
        unauthenticated.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, basic("   ", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    // ============ Write Operation Protection ============

    @Test
    void refusesUnauthenticatedPostRequests() throws Exception {
        String userId = createUser("owner@example.com");

        // POST to create account
        unauthenticated.perform(post("/api/accounts")
                        .contentType("application/json")
                        .content("""
                                {"userId": "%s", "provider": "monzo", "displayName": "Test",
                                 "accountType": "CURRENT", "currency": "GBP"}
                                """.formatted(userId)))
                .andExpect(status().isUnauthorized());

        // POST to create goal
        unauthenticated.perform(post("/api/users/" + userId + "/goals")
                        .contentType("application/json")
                        .content("""
                                {"name": "Car Fund", "targetAmount": 8000.00, "currency": "GBP",
                                 "targetDate": "2030-06-01"}
                                """))
                .andExpect(status().isUnauthorized());

        // POST to create pinned group
        unauthenticated.perform(post("/api/users/" + userId + "/pinned-groups")
                        .contentType("application/json")
                        .content("""
                                {"name": "Subscriptions", "notes": "Monthly expenses"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesUnauthenticatedPutAndDeleteRequests() throws Exception {
        String userId = createUser("owner@example.com");
        String goalId = body(mockMvc.perform(post("/api/users/" + userId + "/goals")
                        .contentType("application/json")
                        .content("""
                                {"name": "Car Fund", "targetAmount": 8000.00, "currency": "GBP",
                                 "targetDate": "2030-06-01"}
                                """))
                .andReturn()).get("id").asText();

        String groupId = body(mockMvc.perform(post("/api/users/" + userId + "/pinned-groups")
                        .contentType("application/json")
                        .content("""
                                {"name": "Subscriptions", "notes": ""}
                                """))
                .andReturn()).get("id").asText();

        // PUT to update goal
        unauthenticated.perform(put("/api/users/" + userId + "/goals/" + goalId)
                        .contentType("application/json")
                        .content("""
                                {"name": "Updated", "targetAmount": 10000.00, "currency": "GBP",
                                 "targetDate": "2030-06-01"}
                                """))
                .andExpect(status().isUnauthorized());

        // PUT to update pinned group
        unauthenticated.perform(put("/api/users/" + userId + "/pinned-groups/" + groupId)
                        .contentType("application/json")
                        .content("""
                                {"name": "Updated"}
                                """))
                .andExpect(status().isUnauthorized());

        // DELETE pinned group
        unauthenticated.perform(delete("/api/users/" + userId + "/pinned-groups/" + groupId))
                .andExpect(status().isUnauthorized());
    }

    // ============ Cross-User Write Operation Protection ============

    @Test
    void refusesCrossUserAccountCreation() throws Exception {
        String ownerId = createUser("owner@example.com");
        createUser("attacker@example.com");

        // Attacker tries to create account for the owner
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
    void refusesCrossUserGoalCreation() throws Exception {
        String ownerId = createUser("owner@example.com");
        createUser("attacker@example.com");

        // Attacker tries to create goal in owner's namespace
        mockMvc.perform(post("/api/users/" + ownerId + "/goals")
                        .contentType("application/json")
                        .content("""
                                {"name": "Hijacked", "targetAmount": 5000.00, "currency": "GBP",
                                 "targetDate": "2030-06-01"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void refusesCrossUserGoalUpdate() throws Exception {
        String ownerId = createUser("owner@example.com");
        String goalId = body(mockMvc.perform(post("/api/users/" + ownerId + "/goals")
                        .contentType("application/json")
                        .content("""
                                {"name": "Car Fund", "targetAmount": 8000.00, "currency": "GBP",
                                 "targetDate": "2030-06-01"}
                                """))
                .andReturn()).get("id").asText();

        createUser("attacker@example.com");

        // Attacker tries to update owner's goal
        mockMvc.perform(put("/api/users/" + ownerId + "/goals/" + goalId)
                        .contentType("application/json")
                        .content("""
                                {"name": "Updated", "targetAmount": 20000.00, "currency": "GBP",
                                 "targetDate": "2030-06-01"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void refusesCrossUserGoalDeletion() throws Exception {
        String ownerId = createUser("owner@example.com");
        String goalId = body(mockMvc.perform(post("/api/users/" + ownerId + "/goals")
                        .contentType("application/json")
                        .content("""
                                {"name": "Car Fund", "targetAmount": 8000.00, "currency": "GBP",
                                 "targetDate": "2030-06-01"}
                                """))
                .andReturn()).get("id").asText();

        createUser("attacker@example.com");

        // Attacker tries to delete owner's goal
        mockMvc.perform(delete("/api/users/" + ownerId + "/goals/" + goalId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void refusesCrossUserPinnedGroupOperations() throws Exception {
        String ownerId = createUser("owner@example.com");
        String groupId = body(mockMvc.perform(post("/api/users/" + ownerId + "/pinned-groups")
                        .contentType("application/json")
                        .content("""
                                {"name": "Subscriptions", "notes": ""}
                                """))
                .andReturn()).get("id").asText();

        createUser("attacker@example.com");

        // Try to read
        mockMvc.perform(get("/api/users/" + ownerId + "/pinned-groups/" + groupId))
                .andExpect(status().isForbidden());

        // Try to update
        mockMvc.perform(put("/api/users/" + ownerId + "/pinned-groups/" + groupId)
                        .contentType("application/json")
                        .content("""
                                {"name": "Updated"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Try to delete
        mockMvc.perform(delete("/api/users/" + ownerId + "/pinned-groups/" + groupId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void refusesAddingTransactionsToCrossUserPinnedGroup() throws Exception {
        String ownerId = createUser("owner@example.com");
        String accountId = createAccount(ownerId, "monzo", "Monzo Current");
        body(mockMvc.perform(multipart("/api/accounts/" + accountId + "/statements")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv",
                                fixture("/monzo/statement.csv"))))
                .andExpect(status().isCreated())
                .andReturn());

        // Get first transaction
        var transactions = body(mockMvc.perform(get("/api/accounts/" + accountId + "/transactions"))
                .andReturn());
        String transactionId = transactions.get(0).get("id").asText();

        String groupId = body(mockMvc.perform(post("/api/users/" + ownerId + "/pinned-groups")
                        .contentType("application/json")
                        .content("""
                                {"name": "Subscriptions", "notes": ""}
                                """))
                .andReturn()).get("id").asText();

        createUser("attacker@example.com");

        // Attacker tries to add transaction to owner's pinned group
        mockMvc.perform(post("/api/users/" + ownerId + "/pinned-groups/" + groupId + "/transactions")
                        .contentType("application/json")
                        .content("""
                                {"transactionId": "%s"}
                                """.formatted(transactionId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ============ Account Access Control ============

    @Test
    void refusesDeleteAccountForAnotherUser() throws Exception {
        String ownerId = createUser("owner@example.com");
        String accountId = createAccount(ownerId, "monzo", "Monzo Current");

        createUser("attacker@example.com");

        // Attacker tries to delete owner's account
        mockMvc.perform(delete("/api/accounts/" + accountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void refusesCrossUserStatementImport() throws Exception {
        String ownerId = createUser("owner@example.com");
        String accountId = createAccount(ownerId, "monzo", "Monzo Current");

        createUser("attacker@example.com");

        // Attacker tries to import statement for owner's account
        mockMvc.perform(multipart("/api/accounts/" + accountId + "/statements")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv",
                                fixture("/monzo/statement.csv"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void refusesGetStatementsForAnotherUserAccount() throws Exception {
        String ownerId = createUser("owner@example.com");
        String accountId = createAccount(ownerId, "monzo", "Monzo Current");

        createUser("attacker@example.com");

        mockMvc.perform(get("/api/accounts/" + accountId + "/statements"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    // ============ Recategorization Access Control ============

    @Test
    void refusesRecategorizationForAnotherUser() throws Exception {
        String ownerId = createUser("owner@example.com");
        String accountId = createAccount(ownerId, "monzo", "Monzo Current");
        body(mockMvc.perform(multipart("/api/accounts/" + accountId + "/statements")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv",
                                fixture("/monzo/statement.csv"))))
                .andExpect(status().isCreated())
                .andReturn());

        createUser("attacker@example.com");

        // Attacker tries to recategorize transactions in owner's account
        mockMvc.perform(post("/api/users/" + ownerId + "/transactions/recategorise")
                        .contentType("application/json")
                        .content("""
                                {"updates": []}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ============ Analytics Access Control ============

    @Test
    void refusesAnalyticsForAnotherUser() throws Exception {
        String ownerId = createUser("owner@example.com");
        createUser("attacker@example.com");

        // Try each analytics endpoint
        mockMvc.perform(get("/api/users/" + ownerId + "/analytics/summary?from=2026-08-01&to=2026-09-01"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/" + ownerId + "/analytics/categories?from=2026-08-01&to=2026-09-01"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/" + ownerId + "/analytics/monthly?from=2026-08-01&to=2026-09-01"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/" + ownerId + "/analytics/accounts?from=2026-08-01&to=2026-09-01"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/" + ownerId + "/analytics/largest-expenses?from=2026-08-01&to=2026-09-01"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/" + ownerId + "/analytics/comparison?from=2026-08-01&to=2026-09-01"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/" + ownerId + "/analytics/savings-pot?from=2026-08-01&to=2026-09-01"))
                .andExpect(status().isForbidden());
    }

    // ============ Error Response Validation ============

    @Test
    void malformedAuthDoesNotLeakStackTracesOrInternalPathsInError() throws Exception {
        var response = unauthenticated.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Basic !!!"))
                .andReturn().getResponse();

        String body = response.getContentAsString();
        assertThat(response.getStatus()).isEqualTo(401);
        // Security best practice: 401 from auth entry point returns empty body, no stack traces
        assertThat(body).doesNotContain("Exception", "StackTrace", "at com.", ".java", "Caused by");
    }

    @Test
    void forbiddenResponseIsConsistent() throws Exception {
        String ownerId = createUser("owner@example.com");
        createUser("attacker@example.com");

        var response = mockMvc.perform(get("/api/users/" + ownerId + "/goals"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(403);
        // Should be a valid error response
        String body = response.getContentAsString();
        assertThat(body).contains("code").contains("FORBIDDEN");
        assertThat(body).doesNotContain("Exception", "StackTrace", "at com.");
    }

    // ============ Ownership Verification ============

    @Test
    void cannotBypassOwnershipCheckWithMalformedUserIdInPath() throws Exception {
        String ownerId = createUser("owner@example.com");
        createUser("attacker@example.com");

        // Try with malformed UUID - should be bad request, not forbidden or success
        mockMvc.perform(get("/api/users/not-a-uuid/goals"))
                .andExpect(status().isBadRequest());

        // Try with different UUID format
        mockMvc.perform(get("/api/users/" + UUID.randomUUID() + "/goals"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotAccessPinnedTransactionsForAnotherUser() throws Exception {
        String ownerId = createUser("owner@example.com");
        String accountId = createAccount(ownerId, "monzo", "Monzo Current");
        body(mockMvc.perform(multipart("/api/accounts/" + accountId + "/statements")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv",
                                fixture("/monzo/statement.csv"))))
                .andExpect(status().isCreated())
                .andReturn());

        // Get first transaction
        var transactions = body(mockMvc.perform(get("/api/accounts/" + accountId + "/transactions"))
                .andReturn());
        String transactionId = transactions.get(0).get("id").asText();

        // Pin as owner (returns 200, not 201)
        mockMvc.perform(post("/api/users/" + ownerId + "/pinned-transactions")
                        .contentType("application/json")
                        .content("""
                                {"transactionId": "%s"}
                                """.formatted(transactionId)))
                .andExpect(status().isOk());

        createUser("attacker@example.com");

        // Attacker tries to access owner's pinned transactions
        mockMvc.perform(get("/api/users/" + ownerId + "/pinned-transactions"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/" + ownerId + "/pinned-transactions/ids"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotAddTransactionToOwnersAccount() throws Exception {
        String ownerId = createUser("owner@example.com");
        String accountId = createAccount(ownerId, "monzo", "Monzo Current");

        createUser("attacker@example.com");

        // Attacker tries to recategorize using owner's account
        mockMvc.perform(post("/api/users/" + ownerId + "/transactions/recategorise")
                        .contentType("application/json")
                        .content("""
                                {"updates": []}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static String basic(String email, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((email + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
