package com.fapp.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SavingsGoalApiTest extends ApiTestSupport {

    private String userId;

    @BeforeEach
    void aUser() throws Exception {
        userId = createUser("saver@example.com");
    }

    @Test
    void createsAGoalAndReportsItsProgress() throws Exception {
        mockMvc.perform(post(goals()).contentType("application/json").content("""
                        {"name": "Car Fund", "targetAmount": 8000.00, "currency": "GBP",
                         "targetDate": "2030-06-01"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.name").value("Car Fund"))
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andExpect(jsonPath("$.targetDate").value("2030-06-01"))
                .andExpect(jsonPath("$.achieved").value(false));
    }

    @Test
    void reportsProgressExactlyAfterMoneyIsPutAside() throws Exception {
        String goalId = create("Car Fund", "8000.00");

        JsonNode updated = body(mockMvc.perform(put(goals() + "/" + goalId)
                        .contentType("application/json").content("""
                                {"name": "Car Fund", "targetAmount": 8000.00,
                                 "currentAmount": 2350.00, "targetDate": "2030-06-01"}
                                """))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(updated.get("currentAmount").decimalValue()).isEqualByComparingTo("2350.00");
        assertThat(updated.get("remainingAmount").decimalValue()).isEqualByComparingTo("5650.00");
        // 2350 of 8000 is 29.375%, to two places 29.38.
        assertThat(updated.get("percentageComplete").decimalValue()).isEqualByComparingTo("29.38");
        assertThat(updated.get("achieved").asBoolean()).isFalse();
    }

    @Test
    void reportsOverachievementRatherThanClampingIt() throws Exception {
        String goalId = create("Car Fund", "8000.00");

        JsonNode updated = body(mockMvc.perform(put(goals() + "/" + goalId)
                        .contentType("application/json").content("""
                                {"name": "Car Fund", "targetAmount": 8000.00,
                                 "currentAmount": 9600.00, "targetDate": "2030-06-01"}
                                """))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(updated.get("percentageComplete").decimalValue()).isEqualByComparingTo("120.00");
        assertThat(updated.get("remainingAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(updated.get("achieved").asBoolean()).isTrue();
    }

    @Test
    void listsAndRetrievesAUsersGoals() throws Exception {
        create("Car Fund", "8000.00");
        String holidayId = create("Holiday", "1200.00");

        mockMvc.perform(get(goals()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get(goals() + "/" + holidayId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Holiday"));
    }

    @Test
    void deletesAGoal() throws Exception {
        String goalId = create("Car Fund", "8000.00");

        mockMvc.perform(delete(goals() + "/" + goalId)).andExpect(status().isNoContent());

        mockMvc.perform(get(goals() + "/" + goalId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOAL_NOT_FOUND"));
        mockMvc.perform(get(goals())).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void answersNotFoundForAGoalThatDoesNotExist() throws Exception {
        mockMvc.perform(get(goals() + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOAL_NOT_FOUND"));
    }

    @Test
    void rejectsAGoalForNothingOrWithNoName() throws Exception {
        mockMvc.perform(post(goals()).contentType("application/json").content("""
                        {"name": "", "targetAmount": 0, "currency": "GBP", "targetDate": "2030-06-01"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.name").isNotEmpty())
                .andExpect(jsonPath("$.fields.targetAmount").isNotEmpty());
    }

    @Test
    void rejectsAGoalThatIsAlreadyOverdue() throws Exception {
        mockMvc.perform(post(goals()).contentType("application/json").content("""
                        {"name": "Yesterday", "targetAmount": 100.00, "currency": "GBP",
                         "targetDate": "2020-01-01"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("due before it is created")));
    }

    @Test
    void rejectsACurrencyThatIsNotAnIsoCodeAndAMissingDate() throws Exception {
        mockMvc.perform(post(goals()).contentType("application/json").content("""
                        {"name": "Bad", "targetAmount": 100.00, "currency": "pounds"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.currency").isNotEmpty())
                .andExpect(jsonPath("$.fields.targetDate").isNotEmpty());
    }

    @Test
    void refusesASecondGoalOfTheSameName() throws Exception {
        create("Car Fund", "8000.00");

        mockMvc.perform(post(goals()).contentType("application/json").content("""
                        {"name": "Car Fund", "targetAmount": 100.00, "currency": "GBP",
                         "targetDate": "2030-06-01"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void rejectsAGoalIdThatIsNotAUuid() throws Exception {
        mockMvc.perform(get(goals() + "/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    /**
     * "featured" must be resolved as its own literal route, not fall through to
     * {@code /{goalId}} and fail there trying to parse "featured" as a UUID. A goal
     * exists so there is something for the wrong route to find; landing on the wrong
     * route would answer 400 INVALID_PARAMETER instead of this 404.
     */
    @Test
    void routesFeaturedAsItsOwnEndpointRatherThanAsAGoalId() throws Exception {
        create("Car Fund", "8000.00");

        mockMvc.perform(get(goals() + "/featured"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOAL_NOT_FOUND"))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not("INVALID_PARAMETER")));
    }

    @Test
    void featuresAndUnfeaturesAGoalThroughTheApi() throws Exception {
        String goalId = create("Car Fund", "8000.00");

        mockMvc.perform(put(goals() + "/" + goalId + "/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured").value(true));

        mockMvc.perform(get(goals() + "/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(goalId))
                .andExpect(jsonPath("$.name").value("Car Fund"))
                .andExpect(jsonPath("$.featured").value(true));

        mockMvc.perform(delete(goals() + "/" + goalId + "/featured")).andExpect(status().isNoContent());

        mockMvc.perform(get(goals() + "/featured")).andExpect(status().isNotFound());
        mockMvc.perform(get(goals() + "/" + goalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured").value(false));
    }

    @Test
    void featuringASecondGoalUnfeaturesTheFirst() throws Exception {
        String first = create("Car Fund", "8000.00");
        String second = create("Holiday", "1200.00");

        mockMvc.perform(put(goals() + "/" + first + "/featured")).andExpect(status().isOk());
        mockMvc.perform(put(goals() + "/" + second + "/featured")).andExpect(status().isOk());

        mockMvc.perform(get(goals() + "/" + first))
                .andExpect(jsonPath("$.featured").value(false));
        mockMvc.perform(get(goals() + "/" + second))
                .andExpect(jsonPath("$.featured").value(true));
    }

    @Test
    void featuringAGoalIsIdempotentThroughTheApi() throws Exception {
        String goalId = create("Car Fund", "8000.00");

        mockMvc.perform(put(goals() + "/" + goalId + "/featured")).andExpect(status().isOk());
        mockMvc.perform(put(goals() + "/" + goalId + "/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured").value(true));
    }

    @Test
    void aUserCannotFeatureAnotherUsersGoalThroughTheApi() throws Exception {
        String goalId = create("Car Fund", "8000.00");
        String strangerId = createUser("stranger@example.com");

        mockMvc.perform(put("/api/users/" + strangerId + "/goals/" + goalId + "/featured"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOAL_NOT_FOUND"));
    }

    @Test
    void keepsMoneyExactThroughTheApi() throws Exception {
        String goalId = create("Precise", "3333.3333");

        JsonNode goal = body(mockMvc.perform(get(goals() + "/" + goalId)).andReturn());
        assertThat(goal.get("targetAmount").decimalValue()).isEqualByComparingTo("3333.3333");
    }

    private String goals() {
        return "/api/users/" + userId + "/goals";
    }

    private String create(String name, String target) throws Exception {
        return body(mockMvc.perform(post(goals()).contentType("application/json").content("""
                        {"name": "%s", "targetAmount": %s, "currency": "GBP",
                         "targetDate": "2030-06-01"}
                        """.formatted(name, target)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }
}
