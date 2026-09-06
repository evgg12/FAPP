package com.fapp.transaction;

/** How the two legs of an internal transfer came to be linked. */
public enum TransferDetectionSource {
    /** Matched automatically by deterministic detection logic. */
    RULE,
    /** Confirmed or created by the user. */
    USER
}
