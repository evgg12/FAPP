package com.fapp.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionApiTest extends ApiTestSupport {

    @Test
    void returnsAnAccountsTransactionsOldestFirst() throws Exception {
        String accountId = importedMonzoAccount();

        MvcResult result = mockMvc.perform(get("/api/accounts/" + accountId + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(18))
                .andReturn();

        JsonNode transactions = body(result);
        assertThat(transactions.get(0).get("bookingDate").asText()).isEqualTo("2026-08-03");
        assertThat(transactions.get(17).get("bookingDate").asText()).isEqualTo("2026-08-29");
    }

    @Test
    void describesATransactionWithoutLeakingHowItIsDeduplicated() throws Exception {
        String accountId = importedMonzoAccount();

        MvcResult result = mockMvc.perform(get("/api/accounts/" + accountId + "/transactions"))
                .andReturn();
        JsonNode groceries = find(body(result), "GREENFIELD GROCERS 4821");

        assertThat(groceries.get("amount").decimalValue()).isEqualByComparingTo("-24.15");
        assertThat(groceries.get("currency").asText()).isEqualTo("GBP");
        assertThat(groceries.get("merchant").asText()).isEqualTo("Greenfield Grocers");
        assertThat(groceries.get("category").asText()).isEqualTo("GROCERIES");
        assertThat(groceries.get("categorySource").asText()).isEqualTo("ADAPTER");
        assertThat(groceries.get("transactionType").asText()).isEqualTo("CARD_PAYMENT");
        assertThat(groceries.get("externalId").asText()).isEqualTo("tx_sample000000000000002");

        // Fingerprint and occurrence are import machinery, not part of the API.
        assertThat(groceries.has("fingerprint")).isFalse();
        assertThat(groceries.has("fingerprintVersion")).isFalse();
        assertThat(groceries.has("occurrence")).isFalse();
        assertThat(groceries.has("statementImport")).isFalse();
        assertThat(groceries.has("account")).isFalse();
        assertThat(groceries.has("userId")).isFalse();
    }

    @Test
    void keepsTheSignAndTheForeignLegOfWhatWasImported() throws Exception {
        String accountId = importedMonzoAccount();
        JsonNode transactions = body(mockMvc.perform(
                get("/api/accounts/" + accountId + "/transactions")).andReturn());

        assertThat(find(transactions, "SAMPLE EMPLOYER LTD SALARY").get("amount").decimalValue())
                .isEqualByComparingTo("1842.55");

        JsonNode abroad = find(transactions, "BAHNHOF BUCHHANDLUNG");
        assertThat(abroad.get("amount").decimalValue()).isEqualByComparingTo("-18.62");
        assertThat(abroad.get("originalAmount").decimalValue()).isEqualByComparingTo("-21.90");
        assertThat(abroad.get("originalCurrency").asText()).isEqualTo("EUR");

        // A domestic movement has no foreign leg, and the field is omitted rather than null.
        assertThat(find(transactions, "GREENFIELD GROCERS 4821").has("originalAmount")).isFalse();
    }

    @Test
    void omitsRatherThanNullsWhatTheBankDidNotSupply() throws Exception {
        String userId = createUser("bos-tx@example.com");
        String accountId = createAccount(userId, "bank_of_scotland", "BoS Current");
        mockMvc.perform(multipart("/api/accounts/" + accountId + "/statements")
                .file(new MockMultipartFile("file", "s.csv", "text/csv",
                        fixture("/bankofscotland/statement.csv"))));

        JsonNode transactions = body(mockMvc.perform(
                get("/api/accounts/" + accountId + "/transactions")).andReturn());

        assertThat(transactions).hasSize(10).allSatisfy(transaction -> {
            // Bank of Scotland supplies no transaction id, no time and no category.
            assertThat(transaction.has("externalId")).isFalse();
            assertThat(transaction.has("occurredOn")).isFalse();
            assertThat(transaction.get("category").asText()).isEqualTo("UNCATEGORISED");
            assertThat(transaction.get("categorySource").asText()).isEqualTo("DEFAULT");
        });
    }

    @Test
    void returnsAnEmptyListForAnAccountWithNoTransactions() throws Exception {
        String accountId = createAccount(createUser("quiet@example.com"), "monzo", "Monzo Current");

        mockMvc.perform(get("/api/accounts/" + accountId + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void answersNotFoundRatherThanAnEmptyListForAnAccountThatDoesNotExist() throws Exception {
        createUser("no-account@example.com");

        mockMvc.perform(get("/api/accounts/" + UUID.randomUUID() + "/transactions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void keepsEachAccountsTransactionsToItself() throws Exception {
        String userId = createUser("two-accounts@example.com");
        String monzo = createAccount(userId, "monzo", "Monzo Current");
        String bos = createAccount(userId, "bank_of_scotland", "BoS Current");

        mockMvc.perform(multipart("/api/accounts/" + monzo + "/statements")
                .file(new MockMultipartFile("file", "s.csv", "text/csv", fixture("/monzo/statement.csv"))));
        mockMvc.perform(multipart("/api/accounts/" + bos + "/statements")
                .file(new MockMultipartFile("file", "s.csv", "text/csv",
                        fixture("/bankofscotland/statement.csv"))));

        mockMvc.perform(get("/api/accounts/" + monzo + "/transactions"))
                .andExpect(jsonPath("$.length()").value(18));
        mockMvc.perform(get("/api/accounts/" + bos + "/transactions"))
                .andExpect(jsonPath("$.length()").value(10));
    }

    @Test
    void letsTheUserFileATransactionUnderADifferentCategory() throws Exception {
        String transactionId = aTransactionId();

        mockMvc.perform(patch("/api/transactions/" + transactionId + "/category")
                        .contentType("application/json")
                        .content("""
                                {"category": "RESTAURANTS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("RESTAURANTS"))
                // Marked as the user's choice, which is what stops a rule overwriting it.
                .andExpect(jsonPath("$.categorySource").value("USER"));

        assertThat(jdbc().queryForObject(
                "SELECT category FROM transactions WHERE id = ?::uuid", String.class, transactionId))
                .isEqualTo("RESTAURANTS");
    }

    @Test
    void acceptsACategoryTheUserNamedThemselves() throws Exception {
        String transactionId = aTransactionId();

        mockMvc.perform(patch("/api/transactions/" + transactionId + "/category")
                        .contentType("application/json")
                        .content("""
                                {"category": "CUSTOM", "customCategory": "  Gym  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("CUSTOM"))
                .andExpect(jsonPath("$.customCategory").value("Gym"));

        assertThat(jdbc().queryForObject(
                "SELECT custom_category FROM transactions WHERE id = ?::uuid", String.class, transactionId))
                .isEqualTo("Gym");
    }

    @Test
    void rejectsACustomCategoryWithNoNameAndForgetsAnOldName() throws Exception {
        String transactionId = aTransactionId();

        mockMvc.perform(patch("/api/transactions/" + transactionId + "/category")
                        .contentType("application/json")
                        .content("""
                                {"category": "CUSTOM"}
                                """))
                .andExpect(status().isBadRequest());

        // Moving back to a fixed category must clear the label, or the database refuses it.
        mockMvc.perform(patch("/api/transactions/" + transactionId + "/category")
                        .contentType("application/json")
                        .content("""
                                {"category": "CUSTOM", "customCategory": "Gym"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/transactions/" + transactionId + "/category")
                        .contentType("application/json")
                        .content("""
                                {"category": "BILLS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customCategory").doesNotExist());
    }

    @Test
    void refusesToRecategoriseSomebodyElsesTransaction() throws Exception {
        String transactionId = aTransactionId();

        createUser("not-their-transaction@example.com");
        authenticateAs("not-their-transaction@example.com");
        mockMvc.perform(patch("/api/transactions/" + transactionId + "/category")
                        .contentType("application/json")
                        .content("""
                                {"category": "BILLS"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void appliesAHandSetCategoryToEveryMovementWithTheSameName() throws Exception {
        // The Bank of Scotland fixture repeats a payee, which is the case this exists for.
        String accountId = createAccount(createUser("same-name@example.com"), "bank_of_scotland", "BoS");
        mockMvc.perform(multipart("/api/accounts/" + accountId + "/statements")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv",
                                fixture("/bankofscotland/statement.csv"))))
                .andExpect(status().isCreated());

        MvcResult all = mockMvc.perform(get("/api/accounts/" + accountId + "/transactions")).andReturn();
        JsonNode transactions = body(all);
        String description = null;
        for (JsonNode candidate : transactions) {
            String text = candidate.get("description").asText();
            int seen = 0;
            for (JsonNode other : transactions) {
                if (other.get("description").asText().equals(text)) {
                    seen++;
                }
            }
            if (seen > 1) {
                description = text;
                break;
            }
        }
        assertThat(description).as("the fixture must repeat a payee").isNotNull();
        String oneId = find(transactions, description).get("id").asText();

        mockMvc.perform(patch("/api/transactions/" + oneId + "/category")
                        .contentType("application/json")
                        .content("""
                                {"category": "SHOPPING"}
                                """))
                .andExpect(status().isOk());

        Integer stillOther = jdbc().queryForObject(
                "SELECT count(*) FROM transactions WHERE lower(description) = lower(?) AND category <> 'SHOPPING'",
                Integer.class, description);
        assertThat(stillOther).isZero();
    }

    private String aTransactionId() throws Exception {
        String accountId = importedMonzoAccount();
        MvcResult result = mockMvc.perform(get("/api/accounts/" + accountId + "/transactions")).andReturn();
        return find(body(result), "GREENFIELD GROCERS 4821").get("id").asText();
    }

    private String importedMonzoAccount() throws Exception {
        String accountId = createAccount(createUser("tx@example.com"), "monzo", "Monzo Current");
        mockMvc.perform(multipart("/api/accounts/" + accountId + "/statements")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv",
                                fixture("/monzo/statement.csv"))))
                .andExpect(status().isCreated());
        return accountId;
    }

    private static JsonNode find(JsonNode transactions, String description) {
        for (JsonNode transaction : transactions) {
            if (description.equals(transaction.get("description").asText())) {
                return transaction;
            }
        }
        throw new AssertionError("no transaction described as " + description);
    }
}
