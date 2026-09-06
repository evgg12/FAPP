package com.fapp.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The recategorisation backfill and the user/account read endpoints, over HTTP against a
 * real database.
 *
 * <p>The baseline throughout is one imported month: the sanitised Monzo statement, whose
 * August figures are 1919.86 in and 845.56 out, netting 1074.30 over a single month.
 */
class MaintenanceApiTest extends ApiTestSupport {

    private static final String BOS_HEADER = "Transaction Date,Transaction Type,Sort Code,"
            + "Account Number,Transaction Description,Debit Amount,Credit Amount,Balance";

    private String userId;
    private String accountId;

    @BeforeEach
    void anImportedMonth() throws Exception {
        userId = createUser("maintenance@example.com");
        accountId = createAccount(userId, "monzo", "Monzo Current");
        mockMvc.perform(multipart("/api/accounts/" + accountId + "/statements")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv",
                                fixture("/monzo/statement.csv"))))
                .andExpect(status().isCreated());
    }

    @Test
    void reappliesMerchantRulesToTransactionsImportedBeforeThem() throws Exception {
        String bos = createAccount(userId, "bank_of_scotland", "BoS Current");
        mockMvc.perform(multipart("/api/accounts/" + bos + "/statements")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv", bankOfScotland(
                                "03/08/2026,DEB,00-00-00,00000000,SAMPLE SHOP NO RULE,10.00,,100.00"))))
                .andExpect(status().isCreated());
        // Rules already ran at import, so anything recognisable is categorised already.
        // What is left is the four Monzo rows whose own category mapped to nothing usable
        // plus the unrecognised Bank of Scotland shop -- five considered, none matched.
        mockMvc.perform(post(recategorise()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examined").value(5))
                .andExpect(jsonPath("$.recategorised").value(0));
    }

    @Test
    void categorisesAMerchantARuleRecognises() throws Exception {
        // Seeded directly as uncategorised, standing in for rows imported before the Lidl
        // rule existed. The fixture holds two with this description -- the pair that
        // tests duplicate-looking transactions -- so both are reset and both are matched.
        jdbc().update("UPDATE transactions SET category = 'UNCATEGORISED',"
                + " category_source = 'DEFAULT', merchant = 'LIDL GB SAMPLETON' WHERE description = ?",
                "GREENFIELD GROCERS 4821");

        mockMvc.perform(post(recategorise()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recategorised").value(2));

        assertThat(jdbc().queryForList(
                "SELECT category || '/' || category_source FROM transactions WHERE merchant = ?",
                String.class, "LIDL GB SAMPLETON"))
                .hasSize(2)
                .containsOnly("GROCERIES/RULE");
    }

    @Test
    void isSafeToRunTheBackfillRepeatedly() throws Exception {
        mockMvc.perform(post(recategorise())).andExpect(status().isOk());
        mockMvc.perform(post(recategorise()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recategorised").value(0));
    }

    // --- user and account reads ---

    @Test
    void retrievesTheUserWithoutTheirCredential() throws Exception {
        JsonNode user = body(mockMvc.perform(get("/api/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("maintenance@example.com"))
                .andReturn());

        assertThat(user.toString()).doesNotContain("password", "$2a$");
    }

    @Test
    void listsTheUsersAccountsWithoutNeedingADateRange() throws Exception {
        createAccount(userId, "bank_of_scotland", "BoS Current");

        mockMvc.perform(get("/api/users/" + userId + "/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].provider").value("bank_of_scotland"))
                .andExpect(jsonPath("$[1].provider").value("monzo"))
                .andExpect(jsonPath("$[0].userId").value(userId));
    }

    @Test
    void retrievesOneAccount() throws Exception {
        mockMvc.perform(get("/api/accounts/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.displayName").value("Monzo Current"))
                .andExpect(jsonPath("$.currency").value("GBP"));
    }

    @Test
    void answersNotFoundForAnAccountThatDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/accounts/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    // --- the mixed-currency guard ---

    @Test
    void refusesAUserWideTotalThatWouldAddUnlikeCurrencies() throws Exception {
        mockMvc.perform(post("/api/accounts").contentType("application/json").content("""
                        {"userId": "%s", "provider": "monzo", "displayName": "Euro Pot",
                         "accountType": "SAVINGS", "currency": "EUR"}
                        """.formatted(userId)))
                .andExpect(status().isCreated());

        // 100 GBP plus 100 EUR is not 200 of anything, so no figure is offered.
        mockMvc.perform(get("/api/users/" + userId + "/analytics/summary?from=2026-08-01&to=2026-09-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MIXED_CURRENCY_ACCOUNTS"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("EUR")));

        // Narrowed to one account it is coherent again, and still correct.
        mockMvc.perform(get("/api/users/" + userId
                        + "/analytics/summary?from=2026-08-01&to=2026-09-01&accountId=" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionCount").value(18));
    }

    // --- helpers ---

    private JsonNode simulate(String body) throws Exception {
        return body(mockMvc.perform(post(simulations()).contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andReturn());
    }

    private String simulations() {
        return "/api/users/" + userId + "/simulations";
    }

    private String recategorise() {
        return "/api/users/" + userId + "/transactions/recategorise";
    }

    private static byte[] bankOfScotland(String... rows) {
        return (BOS_HEADER + "\r\n" + String.join("\r\n", rows) + "\r\n").getBytes(StandardCharsets.UTF_8);
    }
}
