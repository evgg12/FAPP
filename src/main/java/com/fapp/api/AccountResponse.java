package com.fapp.api;

import com.fapp.account.Account;
import com.fapp.account.AccountType;
import java.util.UUID;

/**
 * An account as the API describes it. Carries the provider slug, which is a statement
 * of which adapter reads it, and no bank identifiers at all — FAPP never holds the
 * account number or sort code, so there is nothing here to withhold.
 */
public record AccountResponse(
        UUID id,
        UUID userId,
        String provider,
        String displayName,
        AccountType accountType,
        String currency) {

    public static AccountResponse of(Account account) {
        return new AccountResponse(
                account.id(),
                account.userId(),
                account.provider(),
                account.displayName(),
                account.accountType(),
                account.currency().getCurrencyCode());
    }
}
