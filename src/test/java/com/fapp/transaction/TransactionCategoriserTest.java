package com.fapp.transaction;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Descriptors here are written the way banks actually write them — the merchant name
 * padded out with a store number, a town, a web address or a card reference — because
 * that is what the rules have to survive.
 */
class TransactionCategoriserTest {

    private final TransactionCategoriser categoriser = new TransactionCategoriser();

    @ParameterizedTest
    @CsvSource({
            "LIDL GB SAMPLETON,                 GROCERIES",
            "LIDL,                              GROCERIES",
            "WAGAMAMA SAMPLETON,                RESTAURANTS",
            "DOMINO'S PIZZA 1234,               RESTAURANTS",
            "DOMINOS PIZZA,                     RESTAURANTS",
            "SPORTS DIRECT 421,                 SHOPPING",
            "SPORTSDIRECT.COM,                  SHOPPING",
            "AMAZON.CO.UK*A12BC34D,             SHOPPING",
            "AMAZON MKTPLACE,                   SHOPPING",
            "APPLE.COM/BILL,                    SUBSCRIPTIONS",
            "MARKS&SPENCER PLC,                 SHOPPING",
            "MARKS & SPENCER SAMPLETON,         SHOPPING",
            "MYPROTEIN,                         SHOPPING",
            "MYPROTEIN.COM,                     SHOPPING",
    })
    void recognisesTheMerchantsItHasRulesFor(String descriptor, Category expected) {
        assertThat(categoriser.categorise(descriptor, descriptor)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LIDL GB SAMPLETON", "lidl gb sampleton", "Lidl Gb Sampleton", "LiDl GB SamPLEton"})
    void ignoresHowTheBankCasedIt(String descriptor) {
        assertThat(categoriser.categorise(descriptor, descriptor)).contains(Category.GROCERIES);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MARKS&SPENCER",
            "MARKS & SPENCER",
            "MARKS   &   SPENCER",
            "MARKS-AND... no, MARKS & SPENCER",
            "  MARKS & SPENCER PLC  ",
            "MARKS&SPENCER/SIMPLY FOOD",
    })
    void ignoresThePunctuationAndSpacingAroundAMerchantName(String descriptor) {
        assertThat(categoriser.categorise(descriptor, descriptor)).contains(Category.SHOPPING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOMINO'S", "DOMINOS", "DOMINO’S PIZZA", "Domino's Pizza 4821"})
    void treatsAnApostropheAsAbsentRatherThanAsAWordBreak(String descriptor) {
        assertThat(categoriser.categorise(descriptor, descriptor)).contains(Category.RESTAURANTS);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SAMPLE GROCER 1234",
            "A COUNTERPARTY",
            "SOME SHOP NOBODY WROTE A RULE FOR",
            "CARD PAYMENT 4821",
    })
    void leavesAMerchantItDoesNotRecogniseAlone(String descriptor) {
        assertThat(categoriser.categorise(descriptor, descriptor)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPLE STORE SAMPLETON", "APPLE", "APPLE PAY TOP UP", "PINEAPPLE FARM SHOP"})
    void doesNotCallEveryAppleASubscription(String descriptor) {
        // A subscription is billed through APPLE.COM/BILL. A purchase at an Apple Store
        // is shopping, and a pineapple is groceries — so a bare APPLE decides nothing.
        assertThat(categoriser.categorise(descriptor, descriptor)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "PINEAPPLE FARM,        APPLE",
            "LIDLE HOMEWARES,       LIDL",
            "AMAZONIA CAFE,         AMAZON",
            "SUPERSPORTSDIRECTOR,   SPORTSDIRECT",
    })
    void matchesWholeWordsSoALongerNameIsNotMistakenForAShorterOne(String descriptor, String notThisMerchant) {
        assertThat(descriptor.toUpperCase()).contains(notThisMerchant);
        assertThat(categoriser.categorise(descriptor, descriptor)).isEmpty();
    }

    @Test
    void readsTheDescriptionWhenNoMerchantWasIdentified() {
        // Bank of Scotland supplies one text field; Monzo may leave the counterparty out.
        assertThat(categoriser.categorise(null, "LIDL GB SAMPLETON")).contains(Category.GROCERIES);
        assertThat(categoriser.categorise("", "WAGAMAMA SAMPLETON")).contains(Category.RESTAURANTS);
    }

    @Test
    void readsTheMerchantWhenTheDescriptionIsTheUninformativeHalf() {
        assertThat(categoriser.categorise("Wagamama", "CARD PAYMENT 4821"))
                .contains(Category.RESTAURANTS);
    }

    @Test
    void decidesNothingWhenThereIsNoTextAtAll() {
        assertThat(categoriser.categorise(null, null)).isEmpty();
        assertThat(categoriser.categorise("", "")).isEmpty();
        assertThat(categoriser.categorise("   ", "  ")).isEmpty();
    }

    @Test
    void answersTheSameWayEveryTimeForTheSameInput() {
        Optional<Category> first = categoriser.categorise("AMAZON.CO.UK*A12BC34D", "AMAZON");
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(categoriser.categorise("AMAZON.CO.UK*A12BC34D", "AMAZON")).isEqualTo(first);
        }
        assertThat(first).contains(Category.SHOPPING);
    }

    @Test
    void appliesTheMoreSpecificRuleWhenTwoCouldMatch() {
        // A descriptor naming both an Apple bill and a shop: the ordered rule set puts
        // APPLE.COM/BILL first, so the subscription wins rather than the answer
        // depending on which rule happened to be checked.
        assertThat(categoriser.categorise("APPLE.COM/BILL AMAZON REF", null))
                .contains(Category.SUBSCRIPTIONS);
    }

    @Test
    void onlyEverAnswersWithFappsOwnCategories() {
        for (String descriptor : new String[] {
                "LIDL", "WAGAMAMA", "DOMINO'S", "SPORTS DIRECT",
                "AMAZON", "APPLE.COM/BILL", "MARKS & SPENCER", "MYPROTEIN"}) {
            assertThat(categoriser.categorise(descriptor, descriptor))
                    .isPresent()
                    .get()
                    .isIn((Object[]) Category.values());
        }
    }
}
