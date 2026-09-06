package com.fapp.transaction;

import com.fapp.account.Account;
import com.fapp.account.AccountType;
import com.fapp.money.Money;
import com.fapp.statement.StatementImport;
import com.fapp.user.User;
import java.util.Currency;
import org.junit.jupiter.api.Test;

import static com.fapp.transaction.DomainFixtures.account;
import static com.fapp.transaction.DomainFixtures.statementImport;
import static com.fapp.transaction.DomainFixtures.transaction;
import static com.fapp.transaction.DomainFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The invariants PostgreSQL cannot express, because each one compares two rows.
 * Same-user ownership is checked here for a clear message but is also guaranteed by
 * the composite foreign keys, so it is verified again in
 * {@link com.fapp.persistence.SchemaConstraintTest}.
 */
class TransferTest {

    private final User user = user("owner@example.com");
    private final Account current = account(user, "monzo", AccountType.CURRENT);
    private final Account pot = account(user, "monzo", AccountType.SAVINGS);
    private final StatementImport currentImport = statementImport(current, "current", 1);
    private final StatementImport potImport = statementImport(pot, "pot", 1);

    @Test
    void linksAnEqualAndOppositePairAcrossTwoAccounts() {
        Transaction outgoing = transaction(current, currentImport, "-500.00").build();
        Transaction incoming = transaction(pot, potImport, "500.00").build();

        Transfer transfer = Transfer.of(outgoing, incoming, TransferDetectionSource.RULE);

        assertThat(transfer.outgoing()).isSameAs(outgoing);
        assertThat(transfer.incoming()).isSameAs(incoming);
        assertThat(transfer.userId()).isEqualTo(user.id());
        assertThat(transfer.detectionSource()).isEqualTo(TransferDetectionSource.RULE);
        assertThat(transfer.id()).isNotNull();
    }

    @Test
    void rejectsLegsInTheSameAccount() {
        Transaction outgoing = transaction(current, currentImport, "-500.00").build();
        Transaction incoming = transaction(current, currentImport, "500.00")
                .fingerprint(DomainFixtures.hash("other")).build();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Transfer.of(outgoing, incoming, TransferDetectionSource.RULE))
                .withMessageContaining("two different accounts");
    }

    @Test
    void rejectsTheSameTransactionAsBothLegs() {
        Transaction leg = transaction(current, currentImport, "-500.00").build();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Transfer.of(leg, leg, TransferDetectionSource.RULE))
                .withMessageContaining("both legs");
    }

    @Test
    void rejectsMismatchedMagnitudes() {
        Transaction outgoing = transaction(current, currentImport, "-500.00").build();
        Transaction incoming = transaction(pot, potImport, "499.99").build();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Transfer.of(outgoing, incoming, TransferDetectionSource.RULE))
                .withMessageContaining("equal and opposite");
    }

    @Test
    void rejectsTwoOutflows() {
        Transaction outgoing = transaction(current, currentImport, "-500.00").build();
        Transaction alsoOutgoing = transaction(pot, potImport, "-500.00").build();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Transfer.of(outgoing, alsoOutgoing, TransferDetectionSource.RULE))
                .withMessageContaining("incoming leg must be positive");
    }

    @Test
    void rejectsTheLegsBeingPassedTheWrongWayRound() {
        Transaction outgoing = transaction(current, currentImport, "-500.00").build();
        Transaction incoming = transaction(pot, potImport, "500.00").build();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Transfer.of(incoming, outgoing, TransferDetectionSource.RULE))
                .withMessageContaining("outgoing leg must be negative");
    }

    @Test
    void rejectsLegsInDifferentCurrencies() {
        Account euroAccount = account(user, "monzo", AccountType.SAVINGS, Currency.getInstance("EUR"));
        StatementImport euroImport = statementImport(euroAccount, "euro", 1);
        Transaction outgoing = transaction(current, currentImport, "-500.00").build();
        Transaction incoming = Transaction.builder()
                .account(euroAccount)
                .statementImport(euroImport)
                .bookingDate(DomainFixtures.MARCH_1)
                .amount(Money.of("500.00", "EUR"))
                .description("TRANSFER IN")
                .transactionType(TransactionType.BANK_TRANSFER)
                .fingerprint(DomainFixtures.hash("euro-leg"))
                .build();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Transfer.of(outgoing, incoming, TransferDetectionSource.RULE))
                .withMessageContaining("same currency");
    }

    @Test
    void rejectsLegsBelongingToDifferentUsers() {
        User other = user("other@example.com");
        Account otherAccount = account(other, "bank_of_scotland", AccountType.CURRENT);
        StatementImport otherImport = statementImport(otherAccount, "other", 1);

        Transaction outgoing = transaction(current, currentImport, "-500.00").build();
        Transaction incoming = transaction(otherAccount, otherImport, "500.00").build();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Transfer.of(outgoing, incoming, TransferDetectionSource.RULE))
                .withMessageContaining("same user");
    }
}
