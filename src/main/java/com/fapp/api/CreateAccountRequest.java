package com.fapp.api;

import com.fapp.account.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * @param userId      the owner, who must already exist
 * @param provider    the bank's slug, e.g. {@code monzo} or {@code bank_of_scotland}.
 *                    This is what decides which adapter reads the account's statements,
 *                    so it is fixed here at creation rather than chosen per upload.
 * @param displayName the user's own label for the account
 * @param accountType current account, savings, credit card or other
 * @param currency    ISO 4217 code; fixed for the account's life
 */
public record CreateAccountRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = 40) @Pattern(regexp = "^[a-z][a-z0-9_]*$",
                message = "must be a lowercase slug such as 'bank_of_scotland'") String provider,
        @NotBlank @Size(max = 100) String displayName,
        @NotNull AccountType accountType,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$",
                message = "must be a three-letter ISO 4217 code") String currency) {
}
