package com.fapp.api;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The statement upload endpoint, over HTTP, against real PostgreSQL.
 *
 * <p>The deduplication assertions matter most here. They are not re-testing the import
 * service — they prove the counts it produces survive the boundary intact, so a caller
 * can see that an overlapping statement was reconciled rather than doubled.
 */
class StatementImportApiTest extends ApiTestSupport {

    private static final String BOS_HEADER = "Transaction Date,Transaction Type,Sort Code,Account Number,"
            + "Transaction Description,Debit Amount,Credit Amount,Balance";

    @Test
    void importsAMonzoStatement() throws Exception {
        String accountId = monzoAccount();

        MvcResult result = mockMvc.perform(upload(accountId, fixture("/monzo/statement.csv")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/imports/")))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.provider").value("monzo"))
                // 19 data rows, one a zero-value authorisation hold the adapter skips.
                .andExpect(jsonPath("$.rowCount").value(18))
                .andExpect(jsonPath("$.importedCount").value(18))
                .andExpect(jsonPath("$.duplicateCount").value(0))
                .andExpect(jsonPath("$.periodStart").value("2026-08-03"))
                .andExpect(jsonPath("$.periodEnd").value("2026-08-29"))
                .andReturn();

        // The hash used to recognise a file already seen is not a caller's business.
        assertThat(body(result).has("contentHash")).isFalse();
    }

    @Test
    void importsABankOfScotlandStatement() throws Exception {
        String accountId = bankOfScotlandAccount();

        mockMvc.perform(upload(accountId, fixture("/bankofscotland/statement.csv")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("bank_of_scotland"))
                .andExpect(jsonPath("$.rowCount").value(10))
                .andExpect(jsonPath("$.importedCount").value(10))
                .andExpect(jsonPath("$.duplicateCount").value(0))
                .andExpect(jsonPath("$.periodStart").value("2026-08-03"))
                .andExpect(jsonPath("$.periodEnd").value("2026-08-28"));
    }

    @Test
    void readsTheImportBackFromTheLocationItReturned() throws Exception {
        String accountId = monzoAccount();
        MvcResult imported = mockMvc.perform(upload(accountId, fixture("/monzo/statement.csv")))
                .andExpect(status().isCreated())
                .andReturn();

        String location = imported.getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(body(imported).get("id").asText()))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.importedCount").value(18));
    }

    @Test
    void answersNotFoundForAnImportThatDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/imports/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMPORT_NOT_FOUND"));
    }

    @Test
    void refusesTheSameFileTwiceWithAConflict() throws Exception {
        String accountId = monzoAccount();
        mockMvc.perform(upload(accountId, fixture("/monzo/statement.csv")))
                .andExpect(status().isCreated());

        mockMvc.perform(upload(accountId, fixture("/monzo/statement.csv")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATEMENT_ALREADY_IMPORTED"));

        // Nothing was added by the refused attempt.
        mockMvc.perform(get("/api/accounts/" + accountId + "/transactions"))
                .andExpect(jsonPath("$.length()").value(18));
    }

    @Test
    void reconcilesAnOverlappingStatementInsteadOfDoublingIt() throws Exception {
        String accountId = bankOfScotlandAccount();
        mockMvc.perform(upload(accountId, fixture("/bankofscotland/statement.csv")))
                .andExpect(status().isCreated());

        // A later download still carrying two August rows, plus two genuinely new ones.
        // Bank of Scotland supplies no transaction id, so this is settled by fingerprint.
        mockMvc.perform(upload(accountId, bankOfScotlandExport(
                        "24/08/2026,DEB,00-00-00,00000000,SAMPLE POWER CO,88.40,,3391.60",
                        "28/08/2026,DEB,00-00-00,00000000,SAMPLE HARDWARE 22,64.00,,3327.60",
                        "01/09/2026,DEB,00-00-00,00000000,SAMPLE GROCER 1234,31.05,,3296.55",
                        "02/09/2026,FPI,00-00-00,00000000,A COUNTERPARTY,,15.00,3311.55")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rowCount").value(4))
                .andExpect(jsonPath("$.duplicateCount").value(2))
                .andExpect(jsonPath("$.importedCount").value(2));

        mockMvc.perform(get("/api/accounts/" + accountId + "/transactions"))
                .andExpect(jsonPath("$.length()").value(12));
    }

    @Test
    void rejectsAMalformedStatementAndImportsNothing() throws Exception {
        String accountId = bankOfScotlandAccount();

        mockMvc.perform(upload(accountId, bankOfScotlandExport(
                        "03/08/2026,DEB,00-00-00,00000000,FINE,1.00,,1.00",
                        "04/08/2026,DEB,00-00-00,00000000,BROKEN,not-a-number,,1.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STATEMENT_MALFORMED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("row 3")));

        mockMvc.perform(get("/api/accounts/" + accountId + "/transactions"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejectsAFileThatIsNotTheAccountsBankFormat() throws Exception {
        // A Bank of Scotland export sent to a Monzo account. The provider comes from the
        // account, so this is read as Monzo and refused rather than sniffed.
        String accountId = monzoAccount();

        mockMvc.perform(upload(accountId, fixture("/bankofscotland/statement.csv")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STATEMENT_MALFORMED"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("does not look like a Monzo export")));
    }

    @Test
    void reportsAnAccountConfiguredForABankItCannotRead() throws Exception {
        String userId = createUser("unsupported@example.com");
        String accountId = createAccount(userId, "starling", "Starling Current");

        mockMvc.perform(upload(accountId, fixture("/monzo/statement.csv")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROVIDER_NOT_SUPPORTED"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("starling")));
    }

    @Test
    void answersNotFoundForAnAccountThatDoesNotExist() throws Exception {
        mockMvc.perform(upload(UUID.randomUUID().toString(), fixture("/monzo/statement.csv")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void rejectsAnEmptyUpload() throws Exception {
        String accountId = monzoAccount();

        mockMvc.perform(upload(accountId, new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("empty")));
    }

    @Test
    void rejectsARequestWithNoFileAtAll() throws Exception {
        String accountId = monzoAccount();

        mockMvc.perform(multipart("/api/accounts/" + accountId + "/statements")
                        .file(new MockMultipartFile("statement", "s.csv", "text/csv", "x".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_REQUIRED"));
    }

    @Test
    void rejectsAnAccountIdInThePathThatIsNotAUuid() throws Exception {
        mockMvc.perform(upload("not-a-uuid", fixture("/monzo/statement.csv")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void reportsEveryFailureInTheSameJsonShape() throws Exception {
        String accountId = monzoAccount();

        for (var request : java.util.List.of(
                upload(UUID.randomUUID().toString(), fixture("/monzo/statement.csv")),
                upload(accountId, new byte[0]),
                upload(accountId, "not a statement".getBytes(StandardCharsets.UTF_8)))) {
            MvcResult result = mockMvc.perform(request).andReturn();

            assertThat(result.getResponse().getContentType()).startsWith("application/json");
            var error = body(result);
            assertThat(error.get("code").asText()).matches("^[A-Z][A-Z_]+$");
            assertThat(error.get("message").asText()).isNotBlank();
            assertThat(error.has("timestamp")).isTrue();
            // No internals leak into an error response.
            assertThat(error.toString())
                    .doesNotContain("Exception", "org.springframework", "com.fapp", "SQL", "Hibernate");
        }
    }

    // --- helpers ---

    private String monzoAccount() throws Exception {
        return createAccount(createUser("monzo-" + UUID.randomUUID() + "@example.com"),
                "monzo", "Monzo Current");
    }

    private String bankOfScotlandAccount() throws Exception {
        return createAccount(createUser("bos-" + UUID.randomUUID() + "@example.com"),
                "bank_of_scotland", "BoS Current");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder upload(
            String accountId, byte[] content) {
        return multipart("/api/accounts/" + accountId + "/statements")
                .file(new MockMultipartFile("file", "statement.csv", "text/csv", content));
    }

    private static byte[] bankOfScotlandExport(String... rows) {
        return (BOS_HEADER + "\r\n" + String.join("\r\n", rows) + "\r\n").getBytes(StandardCharsets.UTF_8);
    }
}
