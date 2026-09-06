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
 * The simulator, the recategorisation backfill and the user/account read endpoints, over
 * HTTP against a real database.
 *
 * <p>The baseline throughout is one imported month: the sanitised Monzo statement, whose
 * August figures are 1919.86 in and 845.56 out, netting 1074.30 over a single month.
 */
class SimulatorAndMaintenanceApiTest extends ApiTestSupport {

    private static final String BOS_HEADER = "Transaction Date,Transaction Type,Sort Code,"
            + "Account Number,Transaction Description,Debit Amount,Credit Amount,Balance";

    private String userId;
    private String accountId;

    @BeforeEach
    void anImportedMonth() throws Exception {
        userId = createUser("simulator@example.com");
        accountId = createAccount(userId, "monzo", "Monzo Current");
        mockMvc.perform(multipart("/api/accounts/" + accountId + "/statements")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv",
                                fixture("/monzo/statement.csv"))))
                .andExpect(status().isCreated());
    }

    // --- simulator ---

    @Test
    void reportsTheBaselineAndTheScenarioSeparately() throws Exception {
        JsonNode result = simulate("""
                {"from": "2026-08-01", "to": "2026-09-01", "horizonMonths": 12}
                """);

        assertThat(result.get("monthsOfHistory").asInt()).isEqualTo(1);
        assertThat(result.get("baseline").get("income").decimalValue()).isEqualByComparingTo("1919.86");
        assertThat(result.get("baseline").get("expenditure").decimalValue()).isEqualByComparingTo("845.56");
        assertThat(result.get("baseline").get("net").decimalValue()).isEqualByComparingTo("1074.30");
        // Nothing changed, so the scenario matches the baseline exactly.
        assertThat(result.get("scenario").get("net").decimalValue()).isEqualByComparingTo("1074.30");
        assertThat(result.get("monthlyNetChange").decimalValue()).isEqualByComparingTo("0");
        assertThat(result.get("baselineHorizonNet").decimalValue()).isEqualByComparingTo("12891.60");
    }

    @Test
    void answersAHypotheticalPurchase() throws Exception {
        JsonNode result = simulate("""
                {"from": "2026-08-01", "to": "2026-09-01", "horizonMonths": 12,
                 "oneOffPurchase": 1200.00}
                """);

        // Taken off once: 12891.60 less 1200.00.
        assertThat(result.get("scenarioHorizonNet").decimalValue()).isEqualByComparingTo("11691.60");
        assertThat(result.get("horizonNetChange").decimalValue()).isEqualByComparingTo("-1200.00");
        assertThat(result.get("monthlyNetChange").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void answersARecurringChange() throws Exception {
        JsonNode result = simulate("""
                {"from": "2026-08-01", "to": "2026-09-01", "horizonMonths": 6,
                 "monthlyExpenditureChange": -100.00}
                """);

        assertThat(result.get("scenario").get("net").decimalValue()).isEqualByComparingTo("1174.30");
        assertThat(result.get("monthlyNetChange").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(result.get("horizonNetChange").decimalValue()).isEqualByComparingTo("600.00");
    }

    @Test
    void changesNothingStoredWhenAnsweringAScenario() throws Exception {
        int before = body(mockMvc.perform(get("/api/accounts/" + accountId + "/transactions"))
                .andReturn()).size();

        simulate("""
                {"from": "2026-08-01", "to": "2026-09-01", "horizonMonths": 12,
                 "oneOffPurchase": 5000.00, "monthlyExpenditureChange": 900.00}
                """);

        // A hypothetical purchase is not a transaction.
        assertThat(body(mockMvc.perform(get("/api/accounts/" + accountId + "/transactions")).andReturn())
                .size()).isEqualTo(before);
        assertThat(jdbc().queryForObject("SELECT count(*) FROM transactions", Integer.class))
                .isEqualTo(before);
    }

    @Test
    void reportsTheEffectOnASavingsGoal() throws Exception {
        String goalId = body(mockMvc.perform(post("/api/users/" + userId + "/goals")
                        .contentType("application/json").content("""
                                {"name": "Car Fund", "targetAmount": 8000.00, "currency": "GBP",
                                 "targetDate": "2030-06-01"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        JsonNode outlook = simulate("""
                {"from": "2026-08-01", "to": "2026-09-01", "horizonMonths": 24,
                 "oneOffPurchase": 1200.00, "goalId": "%s"}
                """.formatted(goalId)).get("goalOutlook");

        assertThat(outlook.get("goalName").asText()).isEqualTo("Car Fund");
        assertThat(outlook.get("remaining").decimalValue()).isEqualByComparingTo("8000.00");
        // 8000 at 1074.30 a month is 8 months; 9200 is 9.
        assertThat(outlook.get("baselineMonthsToTarget").asInt()).isEqualTo(8);
        assertThat(outlook.get("scenarioMonthsToTarget").asInt()).isEqualTo(9);
        assertThat(outlook.get("onTrackBefore").asBoolean()).isTrue();
    }

    @Test
    void rejectsAScenarioWithNoPeriodOrHorizon() throws Exception {
        mockMvc.perform(post(simulations()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.from").isNotEmpty())
                .andExpect(jsonPath("$.fields.to").isNotEmpty())
                .andExpect(jsonPath("$.fields.horizonMonths").isNotEmpty());
    }

    @Test
    void rejectsAHorizonAndAPurchaseOutsideWhatIsAllowed() throws Exception {
        mockMvc.perform(post(simulations()).contentType("application/json").content("""
                        {"from": "2026-08-01", "to": "2026-09-01", "horizonMonths": 999}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.horizonMonths").isNotEmpty());

        mockMvc.perform(post(simulations()).contentType("application/json").content("""
                        {"from": "2026-08-01", "to": "2026-09-01", "horizonMonths": 12,
                         "oneOffPurchase": -50.00}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.oneOffPurchase").isNotEmpty());
    }

    @Test
    void rejectsAPeriodThatEndsBeforeItStarts() throws Exception {
        mockMvc.perform(post(simulations()).contentType("application/json").content("""
                        {"from": "2026-09-01", "to": "2026-08-01", "horizonMonths": 12}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void answersNotFoundForAGoalThatIsNotTheCallers() throws Exception {
        mockMvc.perform(post(simulations()).contentType("application/json").content("""
                        {"from": "2026-08-01", "to": "2026-09-01", "horizonMonths": 12,
                         "goalId": "%s"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOAL_NOT_FOUND"));
    }

    // --- recategorisation backfill ---

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
                .andExpect(jsonPath("$.email").value("simulator@example.com"))
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
