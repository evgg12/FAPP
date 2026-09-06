package com.fapp.transaction;

/**
 * What a backfill did.
 *
 * @param examined      uncategorised transactions considered
 * @param recategorised how many a merchant rule recognised
 */
public record RecategorisationResult(int examined, int recategorised) {

    public int stillUncategorised() {
        return examined - recategorised;
    }
}
