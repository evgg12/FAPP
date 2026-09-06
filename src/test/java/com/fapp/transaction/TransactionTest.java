package com.fapp.transaction;

import com.fapp.account.Account;
import com.fapp.account.AccountType;
import com.fapp.money.Money;
import com.fapp.statement.StatementImport;
import com.fapp.user.User;
import java.time.LocalDate;
import java.util.Currency;
import org.junit.jupiter.api.Test;

import static com.fapp.transaction.DomainFixtures.account;
import static com.fapp.transaction.DomainFixtures.statementImport;
import static com.fapp.transaction.DomainFixtures.transaction;
import static com.fapp.transaction.DomainFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Boundary validation: malformed statement data must be refused with a readable message
 * before it reaches persistence, not surface as a constraint violation mid-import.
 */
class TransactionTest {

    private final User user = user("owner@example.com");
    private final Account account = account(user, "bank_of_scotland", AccountType.CURRENT);
    private final StatementImport statementImport = statementImport(account, "seed", 1);

    @Test
    void buildsAnExpenditureWithSensibleDefaults() {
        Transaction transaction = transaction(account, statementImport, "-12.3456").build();

        assertThat(transaction.amount()).isEqualTo(Money.of("-12.3456", "GBP"));
        assertThat(transaction.userId()).isEqualTo(user.id());
        assertThat(transaction.accountId()).isEqualTo(account.id());
        assertThat(transaction.category()).isEqualTo(Category.UNCATEGORISED);
        assertThat(transaction.categorySource()).isEqualTo(CategorySource.DEFAULT);
        assertThat(transaction.occurrence()).isEqualTo((short) 1);
        assertThat(transaction.fingerprintVersion()).isEqualTo(Transaction.CURRENT_FINGERPRINT_VERSION);
        assertThat(transaction.occurredOn()).isEmpty();
        assertThat(transaction.originalAmount()).isEmpty();
        assertThat(transaction.externalId()).isEmpty();
        assertThat(transaction.merchant()).isEmpty();
    }

    @Test
    void rejectsAZeroAmount() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> transaction(account, statementImport, "0.00").build())
                .withMessageContaining("zero");
    }

    @Test
    void rejectsAnAmountInADifferentCurrencyFromTheAccount() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> transaction(account, statementImport, "-10.00")
                        .amount(Money.of("-10.00", "EUR"))
                        .build())
                .withMessageContaining("originalAmount");
    }

    @Test
    void rejectsAnImportBelongingToAnotherAccount() {
        Account otherAccount = account(user, "monzo", AccountType.CURRENT);
        StatementImport otherImport = statementImport(otherAccount, "other", 1);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> transaction(account, otherImport, "-10.00").build())
                .withMessageContaining("different account");
    }

    @Test
    void recordsAForeignCurrencyLeg() {
        Transaction transaction = transaction(account, statementImport, "-8.50")
                .originalAmount(Money.of("-10.00", "EUR"))
                .build();

        assertThat(transaction.amount()).isEqualTo(Money.of("-8.50", "GBP"));
        assertThat(transaction.originalAmount()).contains(Money.of("-10.00", "EUR"));
    }

    @Test
    void rejectsAForeignLegPointingTheOtherWay() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> transaction(account, statementImport, "-8.50")
                        .originalAmount(Money.of("10.00", "EUR"))
                        .build())
                .withMessageContaining("same way");
    }

    @Test
    void rejectsAForeignLegInTheAccountsOwnCurrency() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> transaction(account, statementImport, "-8.50")
                        .originalAmount(Money.of("-8.50", "GBP"))
                        .build())
                .withMessageContaining("different currency");
    }

    @Test
    void rejectsAMalformedFingerprint() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> transaction(account, statementImport, "-1.00")
                        .fingerprint("NOTAHASH")
                        .build())
                .withMessageContaining("SHA-256");
    }

    @Test
    void rejectsABlankDescriptionAndTrimsAGoodOne() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> transaction(account, statementImport, "-1.00")
                        .description("   ")
                        .build())
                .withMessageContaining("description");

        assertThat(transaction(account, statementImport, "-1.00")
                .description("  TESCO STORES  ")
                .build()
                .description()).isEqualTo("TESCO STORES");
    }

    @Test
    void rejectsAnOccurrenceBelowOne() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> transaction(account, statementImport, "-1.00").occurrence(0).build())
                .withMessageContaining("occurrence");
    }

    @Test
    void allowsRecategorisationButNotRewritingHistory() {
        Transaction transaction = transaction(account, statementImport, "-42.00").build();

        transaction.recategorise(Category.GROCERIES, CategorySource.USER);
        transaction.assignMerchant("Tesco");

        assertThat(transaction.category()).isEqualTo(Category.GROCERIES);
        assertThat(transaction.categorySource()).isEqualTo(CategorySource.USER);
        assertThat(transaction.merchant()).contains("Tesco");
        // The money and the dates expose no mutator at all.
        assertThat(transaction.amount()).isEqualTo(Money.of("-42.00", "GBP"));
        assertThat(transaction.bookingDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void keepsCreditCardPurchasesNegativeLikeEveryOtherAccountType() {
        Account creditCard = account(user, "bank_of_scotland", AccountType.CREDIT_CARD,
                Currency.getInstance("GBP"));
        StatementImport cardImport = statementImport(creditCard, "card", 1);

        Transaction purchase = transaction(creditCard, cardImport, "-60.00").build();

        assertThat(purchase.amount().isNegative()).isTrue();
        assertThat(creditCard.accountType()).isEqualTo(AccountType.CREDIT_CARD);
    }
}
