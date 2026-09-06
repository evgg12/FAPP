package com.fapp.account;

/**
 * What kind of financial account this is. Provider-independent by design: a Monzo Pot
 * and a Bank of Scotland instant-access saver are both {@link #SAVINGS}, so a move from
 * a current account into either is recognised as an internal transfer rather than
 * spending.
 */
public enum AccountType {
    CURRENT,
    SAVINGS,
    CREDIT_CARD,
    OTHER
}
