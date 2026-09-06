package com.fapp.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserAndAccountApiTest extends ApiTestSupport {

    @Test
    void createsAUser() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType("application/json")
                        .content("""
                                {"email": "Owner@Example.com", "displayName": "  Owner  ", "password": "correct-horse-battery-staple"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                // The domain lowercases the address and trims the name; the API reports
                // what was stored rather than what was sent.
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(jsonPath("$.displayName").value("Owner"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void rejectsAUserWithAnUnusableEmailOrNoName() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType("application/json")
                        .content("""
                                {"email": "not-an-address", "displayName": "", "password": "short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.email").isNotEmpty())
                .andExpect(jsonPath("$.fields.displayName").isNotEmpty())
                .andExpect(jsonPath("$.fields.password").isNotEmpty())
                .andReturn();

        assertThat(body(result).has("timestamp")).isTrue();
    }

    @Test
    void refusesASecondUserOnTheSameEmailAddress() throws Exception {
        createUser("taken@example.com");

        mockMvc.perform(post("/api/users")
                        .contentType("application/json")
                        .content("""
                                {"email": "TAKEN@example.com", "displayName": "Impostor", "password": "correct-horse-battery-staple"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void rejectsARequestBodyThatIsNotJson() throws Exception {
        mockMvc.perform(post("/api/users").contentType("application/json").content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"));
    }

    @Test
    void createsAnAccountForAUser() throws Exception {
        String userId = createUser("accounts@example.com");

        MvcResult result = mockMvc.perform(post("/api/accounts")
                        .contentType("application/json")
                        .content("""
                                {"userId": "%s", "provider": "monzo", "displayName": "Monzo Current",
                                 "accountType": "CURRENT", "currency": "GBP"}
                                """.formatted(userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.provider").value("monzo"))
                .andExpect(jsonPath("$.accountType").value("CURRENT"))
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andReturn();

        JsonNode account = body(result);
        assertThat(UUID.fromString(account.get("id").asText())).isNotNull();
        // Nothing that identifies the account at the bank is held, so nothing is exposed.
        assertThat(account.fieldNames()).toIterable()
                .doesNotContain("accountNumber", "sortCode", "iban", "contentHash");
    }

    @Test
    void createsAnAccountForEitherSupportedBank() throws Exception {
        String userId = createUser("multibank@example.com");

        assertThat(createAccount(userId, "monzo", "Monzo Current")).isNotBlank();
        assertThat(createAccount(userId, "bank_of_scotland", "BoS Current")).isNotBlank();
    }

    @Test
    void refusesAnAccountOpenedForSomebodyElse() throws Exception {
        createUser("owner-of-nothing@example.com");

        // An account may only be opened for oneself, so naming another user is refused
        // before it is even asked whether that user exists.
        mockMvc.perform(post("/api/accounts")
                        .contentType("application/json")
                        .content("""
                                {"userId": "%s", "provider": "monzo", "displayName": "Ghost",
                                 "accountType": "CURRENT", "currency": "GBP"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void removesAnAccountAndTheHistoryImportedIntoIt() throws Exception {
        String userId = createUser("closing-down@example.com");
        String accountId = createAccount(userId, "monzo", "Monzo Current");
        mockMvc.perform(multipart("/api/accounts/" + accountId + "/statements")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv",
                                fixture("/monzo/statement.csv"))))
                .andExpect(status().isCreated());
        assertThat(jdbc().queryForObject("SELECT count(*) FROM transactions", Integer.class))
                .isEqualTo(18);

        mockMvc.perform(delete("/api/accounts/" + accountId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/accounts/" + accountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        // The cascade is the schema's, not the application's: nothing is left behind.
        assertThat(jdbc().queryForObject("SELECT count(*) FROM transactions", Integer.class))
                .isZero();
        assertThat(jdbc().queryForObject("SELECT count(*) FROM statement_imports", Integer.class))
                .isZero();
        // The user survives their account.
        mockMvc.perform(get("/api/users/" + userId)).andExpect(status().isOk());
    }

    @Test
    void refusesToRemoveSomebodyElsesAccount() throws Exception {
        String ownerId = createUser("keeps-their-account@example.com");
        String accountId = createAccount(ownerId, "monzo", "Monzo Current");

        createUser("wants-it-gone@example.com");
        authenticateAs("wants-it-gone@example.com");
        // Not-found rather than forbidden: 403 would confirm the id is real.
        mockMvc.perform(delete("/api/accounts/" + accountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        authenticateAs("keeps-their-account@example.com");
        mockMvc.perform(get("/api/accounts/" + accountId)).andExpect(status().isOk());
    }

    @Test
    void answersNotFoundWhenRemovingAnAccountThatIsNotThere() throws Exception {
        createUser("nothing-to-remove@example.com");

        mockMvc.perform(delete("/api/accounts/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void rejectsAProviderThatIsNotASlugAndACurrencyThatIsNotAnIsoCode() throws Exception {
        String userId = createUser("bad-input@example.com");

        mockMvc.perform(post("/api/accounts")
                        .contentType("application/json")
                        .content("""
                                {"userId": "%s", "provider": "Bank Of Scotland", "displayName": "X",
                                 "accountType": "CURRENT", "currency": "pounds"}
                                """.formatted(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.provider").isNotEmpty())
                .andExpect(jsonPath("$.fields.currency").isNotEmpty());
    }

    @Test
    void rejectsAnAccountTypeThatIsNotOneOfTheKnownOnes() throws Exception {
        String userId = createUser("bad-type@example.com");

        mockMvc.perform(post("/api/accounts")
                        .contentType("application/json")
                        .content("""
                                {"userId": "%s", "provider": "monzo", "displayName": "X",
                                 "accountType": "CRYPTO_WALLET", "currency": "GBP"}
                                """.formatted(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"));
    }

    @Test
    void rejectsAUserIdThatIsNotAUuid() throws Exception {
        createUser("bad-uuid@example.com");

        mockMvc.perform(post("/api/accounts")
                        .contentType("application/json")
                        .content("""
                                {"userId": "not-a-uuid", "provider": "monzo", "displayName": "X",
                                 "accountType": "CURRENT", "currency": "GBP"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"));
    }

    @Test
    void rejectsAnAccountIdInAPathThatIsNotAUuid() throws Exception {
        createUser("bad-path@example.com");

        mockMvc.perform(get("/api/accounts/not-a-uuid/transactions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("UUID")));
    }
}
