package com.fapp.transaction;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Works out what a transaction was for from the merchant a bank named.
 *
 * <p>Exists because the banks do not agree on how much they tell us. Monzo files every
 * transaction under a category of its own, which its adapter maps onto FAPP's
 * vocabulary. Bank of Scotland's export has no category column at all, so without this
 * every Lidl shop and every Wagamama would sit in {@link Category#UNCATEGORISED} and a
 * category breakdown across two banks would be half empty.
 *
 * <p>Bank-agnostic on purpose: it is given a merchant and a description and knows
 * nothing about who produced them, so the same rule serves both banks and any future
 * one. It is also entirely deterministic — an ordered list of merchant patterns, no
 * scoring, no learning, no model. The same statement always produces the same
 * categories, which is what makes the resulting totals checkable.
 *
 * <p><strong>Rules are kept narrow.</strong> A pattern has to match whole words, so
 * {@code APPLE} does not match {@code PINEAPPLE}, and the Apple rule is keyed on
 * {@code APPLE.COM/BILL} rather than on {@code APPLE} — an Apple Store purchase is
 * shopping, not a subscription, and guessing wrong quietly files a real purchase under
 * the wrong heading. A merchant nobody has written a rule for stays uncategorised,
 * which is honest and visible.
 */
@Component
public class TransactionCategoriser {

    /** Apostrophes vanish, so {@code DOMINO'S} and {@code DOMINOS} are the same merchant. */
    private static final Pattern APOSTROPHES = Pattern.compile("['‘’`]");

    /** Everything else that is not a letter or digit becomes a gap between words. */
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Za-z0-9]+");

    /**
     * The rule set, in the order it is applied: the first pattern to match a
     * transaction's merchant or description decides its category.
     *
     * <p>Patterns are written as they appear on a statement and normalised the same way
     * the text being searched is, so {@code MARKS & SPENCER} here matches
     * {@code MARKS&SPENCER PLC} on a statement without a second entry.
     *
     * <p>Kept deliberately short. Every line is a claim about a real merchant that
     * somebody has to be able to defend, and a wrong one is worse than a missing one.
     */
    private static final List<Rule> RULES = List.of(
            // Most specific first: this must not be reachable by a bare "APPLE".
            rule("APPLE.COM/BILL", Category.SUBSCRIPTIONS),

            rule("LIDL", Category.GROCERIES),

            rule("WAGAMAMA", Category.RESTAURANTS),
            rule("DOMINO'S", Category.RESTAURANTS),

            rule("SPORTS DIRECT", Category.SHOPPING),
            // How the same retailer appears when the descriptor is its web address.
            rule("SPORTSDIRECT", Category.SHOPPING),
            rule("AMAZON", Category.SHOPPING),
            rule("MARKS & SPENCER", Category.SHOPPING),
            rule("MYPROTEIN", Category.SHOPPING));

    /**
     * The category this merchant is known to belong to.
     *
     * <p>Both fields are consulted because banks put the useful text in different
     * places: Bank of Scotland has only a description, and Monzo names the counterparty
     * separately. Either may be absent.
     *
     * @param merchant    the merchant an adapter identified, or {@code null}
     * @param description the statement's description of the movement, or {@code null}
     * @return the category to use, or empty if no rule recognises the merchant
     */
    public Optional<Category> categorise(String merchant, String description) {
        String normalisedMerchant = normalise(merchant);
        String normalisedDescription = normalise(description);

        for (Rule rule : RULES) {
            if (rule.matches(normalisedMerchant) || rule.matches(normalisedDescription)) {
                return Optional.of(rule.category());
            }
        }
        return Optional.empty();
    }

    /**
     * Folds away the differences between how two banks write the same merchant: case,
     * spacing, and the punctuation that turns {@code MARKS&SPENCER} into
     * {@code MARKS & SPENCER}. The result is padded with spaces so a pattern can be
     * required to match whole words rather than any run of characters.
     *
     * <p>Separate from the normalisation behind
     * {@link TransactionFingerprint}, which folds only case and whitespace. That one
     * cannot be widened to fold punctuation as well: it defines transaction identity,
     * and every fingerprint already stored was produced by it.
     */
    private static String normalise(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String withoutApostrophes = APOSTROPHES.matcher(text).replaceAll("");
        String words = NON_ALPHANUMERIC.matcher(withoutApostrophes).replaceAll(" ").trim();
        return " " + words.toUpperCase(Locale.ROOT) + " ";
    }

    private static Rule rule(String merchantAsWritten, Category category) {
        return new Rule(normalise(merchantAsWritten), category);
    }

    /**
     * @param paddedPattern the merchant, normalised and space-padded
     */
    private record Rule(String paddedPattern, Category category) {

        /** True when the pattern appears as whole words in the padded text. */
        boolean matches(String paddedText) {
            return !paddedText.isEmpty() && paddedText.contains(paddedPattern);
        }
    }
}
