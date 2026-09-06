package com.fapp.analytics;

/**
 * Thrown when a figure would have to add unlike currencies together to exist.
 *
 * <p>Analytics sums stored amounts, and 100 GBP plus 100 EUR is not 200 of anything. The
 * only honest answers are to convert with a stated rate on a stated date, which FAPP does
 * not do, or to refuse — and a refusal a caller can act on ("narrow this to one account")
 * is far better than a number that looks right and is not.
 */
public class MixedCurrencyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MixedCurrencyException(String message) {
        super(message);
    }
}
