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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PinnedGroupApiTest extends ApiTestSupport {

    private String userId;
    private String accountId;

    @Test
    void createsAGroup() throws Exception {
        aUserWithTransactions();

        mockMvc.perform(post(groups()).contentType("application/json").content("""
                        {"name": "IOU: Sam", "notes": "Lent for the school trip"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.name").value("IOU: Sam"))
                .andExpect(jsonPath("$.notes").value("Lent for the school trip"))
                .andExpect(jsonPath("$.transactions").isArray())
                .andExpect(jsonPath("$.transactions.length()").value(0));
    }

    @Test
    void listsAUsersGroups() throws Exception {
        aUserWithTransactions();
        create("IOU: Sam", null);
        create("Holiday pot", "Shared with housemates");

        mockMvc.perform(get(groups()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void renamesAGroupAndUpdatesItsNotes() throws Exception {
        aUserWithTransactions();
        String groupId = create("IOU: Sam", "Lent for the school trip");

        JsonNode updated = body(mockMvc.perform(put(groups() + "/" + groupId)
                        .contentType("application/json").content("""
                                {"name": "IOU: Samantha", "notes": "Paid back half"}
                                """))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(updated.get("name").asText()).isEqualTo("IOU: Samantha");
        assertThat(updated.get("notes").asText()).isEqualTo("Paid back half");
    }

    @Test
    void deletesAGroupWithoutTouchingItsTransactions() throws Exception {
        String[] transactionIds = aUserWithTransactions();
        String groupId = create("IOU: Sam", null);
        addTransactions(groupId, transactionIds[0]);

        mockMvc.perform(delete(groups() + "/" + groupId)).andExpect(status().isNoContent());

        mockMvc.perform(get(groups() + "/" + groupId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PINNED_GROUP_NOT_FOUND"));
        assertThat(jdbc().queryForObject(
                "SELECT count(*) FROM transactions WHERE id = ?", Integer.class,
                UUID.fromString(transactionIds[0])))
                .isEqualTo(1);
        assertThat(jdbc().queryForObject(
                "SELECT count(*) FROM pinned_group_transactions", Integer.class))
                .isZero();
    }

    @Test
    void addsOneTransaction() throws Exception {
        String[] transactionIds = aUserWithTransactions();
        String groupId = create("IOU: Sam", null);

        JsonNode updated = addTransactions(groupId, transactionIds[0]);

        assertThat(updated.get("transactions").size()).isEqualTo(1);
        assertThat(updated.get("transactions").get(0).get("transactionId").asText())
                .isEqualTo(transactionIds[0]);
    }

    @Test
    void addsMultipleTransactionsInOneRequest() throws Exception {
        String[] transactionIds = aUserWithTransactions();
        String groupId = create("IOU: Sam", null);

        JsonNode updated = addTransactions(groupId, transactionIds[0], transactionIds[1]);

        assertThat(updated.get("transactions").size()).isEqualTo(2);
    }

    @Test
    void removesATransactionFromAGroupWithoutDeletingIt() throws Exception {
        String[] transactionIds = aUserWithTransactions();
        String groupId = create("IOU: Sam", null);
        addTransactions(groupId, transactionIds[0], transactionIds[1]);

        mockMvc.perform(delete(groups() + "/" + groupId + "/transactions/" + transactionIds[0]))
                .andExpect(status().isNoContent());

        JsonNode after = body(mockMvc.perform(get(groups() + "/" + groupId)).andReturn());
        assertThat(after.get("transactions").size()).isEqualTo(1);
        assertThat(after.get("transactions").get(0).get("transactionId").asText())
                .isEqualTo(transactionIds[1]);
        assertThat(jdbc().queryForObject(
                "SELECT count(*) FROM transactions WHERE id = ?", Integer.class,
                UUID.fromString(transactionIds[0])))
                .isEqualTo(1);
    }

    /**
     * Adding a transaction that is already a member is a no-op, not a failure: the
     * request is asking for a state that already holds.
     */
    @Test
    void addingAnAlreadyPinnedTransactionAgainIsIdempotent() throws Exception {
        String[] transactionIds = aUserWithTransactions();
        String groupId = create("IOU: Sam", null);
        addTransactions(groupId, transactionIds[0]);

        JsonNode after = addTransactions(groupId, transactionIds[0]);

        assertThat(after.get("transactions").size()).isEqualTo(1);
        assertThat(jdbc().queryForObject(
                "SELECT count(*) FROM pinned_group_transactions", Integer.class))
                .isEqualTo(1);
    }

    /**
     * A request mixing an already-pinned id with new ones adds only what is new,
     * leaving the existing membership untouched rather than failing the whole request.
     */
    @Test
    void mixingAlreadyPinnedAndNewTransactionIdsAddsOnlyTheNewOnes() throws Exception {
        String[] transactionIds = aUserWithTransactions();
        String groupId = create("IOU: Sam", null);
        addTransactions(groupId, transactionIds[0]);

        JsonNode after = addTransactions(groupId, transactionIds[0], transactionIds[1], transactionIds[2]);

        assertThat(after.get("transactions").size()).isEqualTo(3);
        assertThat(jdbc().queryForObject(
                "SELECT count(*) FROM pinned_group_transactions", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void aUserCannotSeeOrModifyAnotherUsersGroup() throws Exception {
        aUserWithTransactions();
        String groupId = create("IOU: Sam", null);
        createUser("stranger@example.com");

        mockMvc.perform(get("/api/users/" + userId + "/pinned-groups/" + groupId))
                .andExpect(status().isForbidden());
    }

    @Test
    void aGroupBelongingToAnotherUserIsNotFoundRatherThanForbiddenOnItsOwnRoute() throws Exception {
        aUserWithTransactions();
        String groupId = create("IOU: Sam", null);
        String strangerId = createUser("stranger@example.com");

        mockMvc.perform(get("/api/users/" + strangerId + "/pinned-groups/" + groupId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PINNED_GROUP_NOT_FOUND"));
    }

    @Test
    void aUserCannotAddAnotherUsersTransactionToTheirGroup() throws Exception {
        String[] mine = aUserWithTransactions();
        String groupId = create("IOU: Sam", null);

        String strangerId = createUser("stranger@example.com");
        String strangerAccountId = createAccount(strangerId, "monzo", "Stranger's Monzo");
        String strangerTransactionId = importStatement(strangerAccountId)[0];

        // Back to acting as the group's owner.
        authenticateAs("owner@example.com");

        mockMvc.perform(post(groups() + "/" + groupId + "/transactions")
                        .contentType("application/json")
                        .content("{\"transactionIds\": [\"" + strangerTransactionId + "\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));

        JsonNode group = body(mockMvc.perform(get(groups() + "/" + groupId)).andReturn());
        assertThat(group.get("transactions").size()).isEqualTo(0);
    }

    @Test
    void deletingTheUnderlyingTransactionRemovesItsMembershipRow() throws Exception {
        String[] transactionIds = aUserWithTransactions();
        String groupId = create("IOU: Sam", null);
        addTransactions(groupId, transactionIds[0]);

        // Deleting the account cascades its transactions, exactly as reverting an
        // import would; membership rows must not be left pointing at nothing.
        mockMvc.perform(delete("/api/accounts/" + accountId)).andExpect(status().isNoContent());

        assertThat(jdbc().queryForObject(
                "SELECT count(*) FROM pinned_group_transactions WHERE group_id = ?", Integer.class,
                UUID.fromString(groupId)))
                .isZero();
        // The group itself is untouched by its transactions disappearing.
        mockMvc.perform(get(groups() + "/" + groupId)).andExpect(status().isOk());
    }

    @Test
    void aPinnedGroupPersistsAcrossRequests() throws Exception {
        String[] transactionIds = aUserWithTransactions();
        String groupId = create("IOU: Sam", "Track what's owed");
        addTransactions(groupId, transactionIds[0]);

        JsonNode reloaded = body(mockMvc.perform(get(groups() + "/" + groupId)).andReturn());
        assertThat(reloaded.get("name").asText()).isEqualTo("IOU: Sam");
        assertThat(reloaded.get("notes").asText()).isEqualTo("Track what's owed");
        assertThat(reloaded.get("transactions").size()).isEqualTo(1);
    }

    @Test
    void pinningTransactionsDoesNotChangeAnalyticsTotals() throws Exception {
        aUserWithTransactions();
        JsonNode before = body(mockMvc.perform(get("/api/users/" + userId + "/analytics/summary")
                        .param("from", "2000-01-01").param("to", "2100-01-01"))
                .andReturn());

        String groupId = create("IOU: Sam", null);
        String[] transactionIds = idsOfAccountTransactions();
        addTransactions(groupId, transactionIds);
        mockMvc.perform(delete(groups() + "/" + groupId + "/transactions/" + transactionIds[0]));

        JsonNode after = body(mockMvc.perform(get("/api/users/" + userId + "/analytics/summary")
                        .param("from", "2000-01-01").param("to", "2100-01-01"))
                .andReturn());

        assertThat(after.get("income").decimalValue()).isEqualByComparingTo(before.get("income").decimalValue());
        assertThat(after.get("expenditure").decimalValue())
                .isEqualByComparingTo(before.get("expenditure").decimalValue());
        assertThat(after.get("netSavings").decimalValue())
                .isEqualByComparingTo(before.get("netSavings").decimalValue());
        assertThat(after.get("transactionCount").asInt()).isEqualTo(before.get("transactionCount").asInt());
    }

    @Test
    void pinsATransactionIndividuallyWithAnOptionalNote() throws Exception {
        String[] transactionIds = aUserWithTransactions();

        JsonNode pin = body(mockMvc.perform(post(individual())
                        .contentType("application/json")
                        .content("{\"transactionId\": \"" + transactionIds[0] + "\", \"note\": \"Owed by Sam\"}"))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(pin.get("transactionId").asText()).isEqualTo(transactionIds[0]);
        assertThat(pin.get("note").asText()).isEqualTo("Owed by Sam");

        mockMvc.perform(get(individual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transactionId").value(transactionIds[0]))
                .andExpect(jsonPath("$[0].note").value("Owed by Sam"));
    }

    @Test
    void pinsATransactionIndividuallyWithNoNoteAtAll() throws Exception {
        String[] transactionIds = aUserWithTransactions();

        JsonNode pin = body(mockMvc.perform(post(individual())
                        .contentType("application/json")
                        .content("{\"transactionId\": \"" + transactionIds[0] + "\"}"))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(pin.has("note")).isFalse();
    }

    @Test
    void pinningTheSameTransactionIndividuallyTwiceIsIdempotent() throws Exception {
        String[] transactionIds = aUserWithTransactions();

        mockMvc.perform(post(individual()).contentType("application/json")
                .content("{\"transactionId\": \"" + transactionIds[0] + "\"}"));
        mockMvc.perform(post(individual()).contentType("application/json")
                .content("{\"transactionId\": \"" + transactionIds[0] + "\"}"));

        mockMvc.perform(get(individual())).andExpect(jsonPath("$.length()").value(1));
        assertThat(jdbc().queryForObject(
                "SELECT count(*) FROM pinned_group_transactions WHERE group_id IS NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void unpinsAnIndividuallyPinnedTransactionWithoutDeletingIt() throws Exception {
        String[] transactionIds = aUserWithTransactions();
        mockMvc.perform(post(individual()).contentType("application/json")
                .content("{\"transactionId\": \"" + transactionIds[0] + "\"}"));

        mockMvc.perform(delete(individual() + "/" + transactionIds[0])).andExpect(status().isNoContent());

        mockMvc.perform(get(individual())).andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbc().queryForObject(
                "SELECT count(*) FROM transactions WHERE id = ?", Integer.class,
                UUID.fromString(transactionIds[0])))
                .isEqualTo(1);
    }

    @Test
    void unpinningATransactionThatWasNeverIndividuallyPinnedIsANoOp() throws Exception {
        String[] transactionIds = aUserWithTransactions();

        mockMvc.perform(delete(individual() + "/" + transactionIds[0])).andExpect(status().isNoContent());
    }

    @Test
    void listsEveryPinnedTransactionIdWhetherIndividualOrGrouped() throws Exception {
        String[] transactionIds = aUserWithTransactions();
        mockMvc.perform(post(individual()).contentType("application/json")
                .content("{\"transactionId\": \"" + transactionIds[0] + "\"}"));
        String groupId = create("IOU: Sam", null);
        addTransactions(groupId, transactionIds[1]);

        JsonNode ids = body(mockMvc.perform(get(individual() + "/ids")).andReturn());

        assertThat(ids).extracting(JsonNode::asText).containsExactlyInAnyOrder(transactionIds[0], transactionIds[1]);
    }

    @Test
    void aUserCannotPinAnotherUsersTransactionIndividually() throws Exception {
        aUserWithTransactions();
        String strangerId = createUser("stranger@example.com");
        String strangerAccountId = createAccount(strangerId, "monzo", "Stranger's Monzo");
        String strangerTransactionId = importStatement(strangerAccountId)[0];

        authenticateAs("owner@example.com");

        mockMvc.perform(post(individual()).contentType("application/json")
                        .content("{\"transactionId\": \"" + strangerTransactionId + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void aUserCannotSeeAnotherUsersIndividualPins() throws Exception {
        String[] mine = aUserWithTransactions();
        mockMvc.perform(post(individual()).contentType("application/json")
                .content("{\"transactionId\": \"" + mine[0] + "\"}"));
        String strangerId = createUser("stranger@example.com");

        mockMvc.perform(get("/api/users/" + strangerId + "/pinned-transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletingTheUnderlyingTransactionRemovesItsIndividualPin() throws Exception {
        String[] transactionIds = aUserWithTransactions();
        mockMvc.perform(post(individual()).contentType("application/json")
                .content("{\"transactionId\": \"" + transactionIds[0] + "\"}"));

        mockMvc.perform(delete("/api/accounts/" + accountId)).andExpect(status().isNoContent());

        assertThat(jdbc().queryForObject(
                "SELECT count(*) FROM pinned_group_transactions", Integer.class))
                .isZero();
    }

    private String individual() {
        return "/api/users/" + userId + "/pinned-transactions";
    }

    private String groups() {
        return "/api/users/" + userId + "/pinned-groups";
    }

    private String create(String name, String notes) throws Exception {
        String body = notes == null
                ? "{\"name\": \"" + name + "\"}"
                : "{\"name\": \"" + name + "\", \"notes\": \"" + notes + "\"}";
        return body(mockMvc.perform(post(groups()).contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private JsonNode addTransactions(String groupId, String... transactionIds) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < transactionIds.length; i++) {
            if (i > 0) {
                ids.append(',');
            }
            ids.append('"').append(transactionIds[i]).append('"');
        }
        return body(mockMvc.perform(post(groups() + "/" + groupId + "/transactions")
                        .contentType("application/json")
                        .content("{\"transactionIds\": [" + ids + "]}"))
                .andExpect(status().isOk())
                .andReturn());
    }

    /** Registers a user, imports the standard Monzo fixture, and returns its transaction ids. */
    private String[] aUserWithTransactions() throws Exception {
        userId = createUser("owner@example.com");
        accountId = createAccount(userId, "monzo", "Owner's Monzo");
        return importStatement(accountId);
    }

    private String[] idsOfAccountTransactions() throws Exception {
        JsonNode transactions = body(mockMvc.perform(get("/api/accounts/" + accountId + "/transactions"))
                .andReturn());
        String[] ids = new String[transactions.size()];
        for (int i = 0; i < transactions.size(); i++) {
            ids[i] = transactions.get(i).get("id").asText();
        }
        return ids;
    }

    private String[] importStatement(String forAccountId) throws Exception {
        mockMvc.perform(multipart("/api/accounts/" + forAccountId + "/statements")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv",
                                fixture("/monzo/statement.csv"))))
                .andExpect(status().isCreated());
        JsonNode transactions = body(mockMvc.perform(get("/api/accounts/" + forAccountId + "/transactions"))
                .andReturn());
        String[] ids = new String[transactions.size()];
        for (int i = 0; i < transactions.size(); i++) {
            ids[i] = transactions.get(i).get("id").asText();
        }
        return ids;
    }
}
