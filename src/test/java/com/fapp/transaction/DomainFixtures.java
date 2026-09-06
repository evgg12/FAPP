package com.fapp.transaction;

import com.fapp.account.Account;
import com.fapp.account.AccountType;
import com.fapp.money.Money;
import com.fapp.statement.StatementImport;
import com.fapp.statement.StatementPeriod;
import com.fapp.user.User;
import java.time.LocalDate;
import java.util.Currency;
import java.util.HexFormat;
import java.util.Locale;

/** Minimal, readable graphs for tests. Not production code. */
public final class DomainFixtures {

    public static final Currency GBP = Currency.getInstance("GBP");
    public static final LocalDate MARCH_1 = LocalDate.of(2026, 3, 1);
    public static final StatementPeriod MARCH =
            StatementPeriod.of(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

    private DomainFixtures() {
    }

    public static User user(String email) {
        return User.of(email, "Test User");
    }

    public static Account account(User user, String provider, AccountType type) {
        return account(user, provider, type, GBP);
    }

    public static Account account(User user, String provider, AccountType type, Currency currency) {
        return Account.of(user, provider, provider + " " + type, type, currency);
    }

    public static StatementImport statementImport(Account account, String hashSeed, int rows) {
        return StatementImport.of(account, hash(hashSeed), MARCH, rows, rows, 0);
    }

    public static Transaction.Builder transaction(Account account, StatementImport statementImport, String amount) {
        return Transaction.builder()
                .account(account)
                .statementImport(statementImport)
                .bookingDate(MARCH_1)
                .amount(Money.of(amount, account.currency().getCurrencyCode()))
                .description("TEST PAYMENT")
                .transactionType(TransactionType.CARD_PAYMENT)
                .fingerprint(hash(account.displayName() + amount));
    }

    /** A deterministic, well-formed 64-character lowercase hex string. */
    public static String hash(String seed) {
        StringBuilder hex = new StringBuilder(HexFormat.of().toHexDigits(seed.hashCode()));
        while (hex.length() < 64) {
            hex.append(HexFormat.of().toHexDigits(hex.toString().hashCode()));
        }
        return hex.substring(0, 64).toLowerCase(Locale.ROOT);
    }
}
