package com.fapp.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The analytics endpoints over HTTP.
 *
 * <p>Seeded by importing the sanitised Monzo fixture, so the expected figures are those
 * of a statement the rest of the suite already pins. Summing its Amount column by hand
 * gives 1919.86 in (1842.55 + 2.31 + 75.00) and 845.56 out across the other fifteen
 * movements, netting 1074.30, over 2026-08-03 to 2026-08-29. Pinning those here means a
 * change in either the adapter or the analytics shows up as a disagreement.
 */
class AnalyticsApiTest extends ApiTestSupport {

    private static final String AUGUST = "from=2026-08-01&to=2026-09-01";

    private String userId;
    private String accountId;

    @BeforeEach
    void importAStatement() throws Exception {
        userId = createUser("analytics@example.com");
        accountId = createAccount(userId, "monzo", "Monzo Current");
        mockMvc.perform(multipart("/api/accounts/" + accountId + "/statements")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv",
                                fixture("/monzo/statement.csv"))))
                .andExpect(status().isCreated());
    }

    @Test
    void reportsASummaryThatAgreesWithWhatWasImported() throws Exception {
        MvcResult result = mockMvc.perform(get(analytics("summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.from").value("2026-08-01"))
                .andExpect(jsonPath("$.period.to").value("2026-09-01"))
                .andExpect(jsonPath("$.transactionCount").value(18))
                .andReturn();

        JsonNode summary = body(result);
        // income - expenditure = netSavings, to the penny.
        assertThat(summary.get("income").decimalValue()
                .subtract(summary.get("expenditure").decimalValue()))
                .isEqualByComparingTo(summary.get("netSavings").decimalValue());
        assertThat(summary.get("income").decimalValue()).isEqualByComparingTo("1919.86");
        assertThat(summary.get("expenditure").decimalValue()).isEqualByComparingTo("845.56");
        assertThat(summary.get("netSavings").decimalValue()).isEqualByComparingTo("1074.30");
    }

    @Test
    void reportsCategoriesThatAddUpToTheSummary() throws Exception {
        JsonNode summary = body(mockMvc.perform(get(analytics("summary"))).andReturn());
        JsonNode categories = body(mockMvc.perform(get(analytics("categories")))
                .andExpect(status().isOk())
                .andReturn());

        var expenditure = java.math.BigDecimal.ZERO;
        var income = java.math.BigDecimal.ZERO;
        for (JsonNode category : categories) {
            assertThat(category.get("category").asText()).isNotBlank();
            expenditure = expenditure.add(category.get("expenditure").decimalValue());
            income = income.add(category.get("income").decimalValue());
        }
        assertThat(expenditure).isEqualByComparingTo(summary.get("expenditure").decimalValue());
        assertThat(income).isEqualByComparingTo(summary.get("income").decimalValue());
    }

    @Test
    void reportsEveryMonthInTheWindow() throws Exception {
        mockMvc.perform(get(byUser("monthly") + "?from=2026-07-01&to=2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].month").value("2026-07"))
                .andExpect(jsonPath("$[0].transactionCount").value(0))
                .andExpect(jsonPath("$[1].month").value("2026-08"))
                .andExpect(jsonPath("$[1].transactionCount").value(18))
                .andExpect(jsonPath("$[2].month").value("2026-09"))
                .andExpect(jsonPath("$[2].transactionCount").value(0));
    }

    @Test
    void reportsTheAccountBreakdown() throws Exception {
        mockMvc.perform(get(analytics("accounts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountId").value(accountId))
                .andExpect(jsonPath("$[0].provider").value("monzo"))
                .andExpect(jsonPath("$[0].accountName").value("Monzo Current"))
                .andExpect(jsonPath("$[0].transactionCount").value(18));
    }

    @Test
    void reportsTheLargestExpensesBiggestFirstAndHonoursTheLimit() throws Exception {
        MvcResult all = mockMvc.perform(get(analytics("largest-expenses")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode expenses = body(all);

        assertThat(expenses).isNotEmpty();
        for (int i = 1; i < expenses.size(); i++) {
            assertThat(expenses.get(i - 1).get("amount").decimalValue())
                    .isGreaterThanOrEqualTo(expenses.get(i).get("amount").decimalValue());
        }
        // Every one is a positive figure and nothing that came in appears.
        assertThat(expenses).allSatisfy(expense ->
                assertThat(expense.get("amount").decimalValue()).isPositive());

        mockMvc.perform(get(analytics("largest-expenses") + "&limit=3"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void reportsAComparisonAgainstThePreviousMonth() throws Exception {
        mockMvc.perform(get(analytics("comparison")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.from").value("2026-08-01"))
                .andExpect(jsonPath("$.previous.from").value("2026-07-01"))
                .andExpect(jsonPath("$.previous.to").value("2026-08-01"))
                // July held nothing, so there is a change but no percentage.
                .andExpect(jsonPath("$.expenditure.previous").value(0))
                .andExpect(jsonPath("$.expenditure.changePercent").doesNotExist());
    }

    @Test
    void narrowsEveryEndpointToOneAccountWhenAsked() throws Exception {
        String other = createAccount(userId, "bank_of_scotland", "BoS Current");

        mockMvc.perform(get(analytics("summary") + "&accountId=" + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionCount").value(0))
                .andExpect(jsonPath("$.income").value(0))
                .andExpect(jsonPath("$.expenditure").value(0));

        mockMvc.perform(get(analytics("summary") + "&accountId=" + accountId))
                .andExpect(jsonPath("$.transactionCount").value(18));
    }

    @Test
    void reportsZerosRatherThanFailingForAPeriodWithNothingInIt() throws Exception {
        mockMvc.perform(get(byUser("summary") + "?from=2026-01-01&to=2026-02-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income").value(0))
                .andExpect(jsonPath("$.expenditure").value(0))
                .andExpect(jsonPath("$.netSavings").value(0))
                .andExpect(jsonPath("$.transactionCount").value(0));

        mockMvc.perform(get(byUser("categories") + "?from=2026-01-01&to=2026-02-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- failures ---

    @Test
    void requiresBothDates() throws Exception {
        mockMvc.perform(get(byUser("summary") + "?to=2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("from")));

        mockMvc.perform(get(byUser("summary") + "?from=2026-08-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("to")));
    }

    @Test
    void rejectsADateThatIsNotAnIsoDate() throws Exception {
        mockMvc.perform(get(byUser("summary") + "?from=01/08/2026&to=2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("from")));
    }

    @Test
    void rejectsAWindowThatEndsBeforeItStarts() throws Exception {
        mockMvc.perform(get(byUser("summary") + "?from=2026-09-01&to=2026-08-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("must start before it ends")));
    }

    @Test
    void rejectsAUserIdThatIsNotAUuid() throws Exception {
        mockMvc.perform(get("/api/users/not-a-uuid/analytics/summary?" + AUGUST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void rejectsAnAccountIdThatIsNotAUuid() throws Exception {
        mockMvc.perform(get(analytics("summary") + "&accountId=not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void rejectsALimitOutsideWhatIsAllowed() throws Exception {
        mockMvc.perform(get(analytics("largest-expenses") + "&limit=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get(analytics("largest-expenses") + "&limit=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void refusesAnalyticsForAnyUserOtherThanTheCaller() throws Exception {
        // Refused before it is even asked whether that user exists, so the endpoint
        // cannot be used to find out which user ids are real.
        mockMvc.perform(get("/api/users/" + UUID.randomUUID() + "/analytics/summary?" + AUGUST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void answersNotFoundForAnAccountThatDoesNotExist() throws Exception {
        mockMvc.perform(get(analytics("summary") + "&accountId=" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void answersNotFoundForAnAccountBelongingToSomebodyElse() throws Exception {
        String strangerId = createUser("stranger@example.com");
        String theirAccount = createAccount(strangerId, "monzo", "Their Monzo");
        authenticateAs("analytics@example.com");

        // The same answer as an account that does not exist, so analytics cannot be used
        // to discover what other people hold.
        mockMvc.perform(get(analytics("summary") + "&accountId=" + theirAccount))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void keepsOneUsersFiguresOutOfAnothers() throws Exception {
        String strangerId = createUser("stranger@example.com");
        String theirAccount = createAccount(strangerId, "bank_of_scotland", "Their BoS");
        mockMvc.perform(multipart("/api/accounts/" + theirAccount + "/statements")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv",
                                fixture("/bankofscotland/statement.csv"))))
                .andExpect(status().isCreated());

        // Each user sees their own figures, and only while authenticated as themselves.
        mockMvc.perform(get("/api/users/" + strangerId + "/analytics/summary?" + AUGUST))
                .andExpect(jsonPath("$.transactionCount").value(10));

        authenticateAs("analytics@example.com");
        mockMvc.perform(get(analytics("summary")))
                .andExpect(jsonPath("$.transactionCount").value(18));
        // And cannot reach the other's, even knowing their id.
        mockMvc.perform(get("/api/users/" + strangerId + "/analytics/summary?" + AUGUST))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportsEveryAnalyticsFailureInTheSameJsonShape() throws Exception {
        for (String request : java.util.List.of(
                "/api/users/" + UUID.randomUUID() + "/analytics/summary?" + AUGUST,
                byUser("summary") + "?from=2026-09-01&to=2026-08-01",
                byUser("summary") + "?to=2026-09-01",
                byUser("summary") + "?from=nonsense&to=2026-09-01")) {
            MvcResult result = mockMvc.perform(get(request)).andReturn();

            assertThat(result.getResponse().getContentType()).startsWith("application/json");
            JsonNode error = body(result);
            assertThat(error.get("code").asText()).matches("^[A-Z][A-Z_]+$");
            assertThat(error.get("message").asText()).isNotBlank();
            assertThat(error.has("timestamp")).isTrue();
            assertThat(error.toString())
                    .doesNotContain("Exception", "org.springframework", "com.fapp", "SQL", "Hibernate");
        }
    }

    private String byUser(String endpoint) {
        return "/api/users/" + userId + "/analytics/" + endpoint;
    }

    private String analytics(String endpoint) {
        return byUser(endpoint) + "?" + AUGUST;
    }
}
