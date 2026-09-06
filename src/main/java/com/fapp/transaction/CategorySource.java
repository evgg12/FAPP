package com.fapp.transaction;

/**
 * How a transaction's category was decided. Recorded so that re-running automatic
 * categorisation cannot overwrite a choice the user made by hand.
 */
public enum CategorySource {
    /** Chosen by the user. Automatic categorisation must never overwrite this. */
    USER,
    /** Assigned by a categorisation rule. */
    RULE,
    /** Derived by a bank adapter from data in the statement. */
    ADAPTER,
    /** No rule matched; the transaction kept its default category. */
    DEFAULT
}
