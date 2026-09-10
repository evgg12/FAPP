package com.fapp.persistence;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the migration pipeline itself, and that V2 landed what it claims to. */
class SchemaMigrationTest extends AbstractPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void appliesEveryMigrationInOrder() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
    }

    @Test
    void createsTheDomainTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name",
                String.class);

        assertThat(tables).containsExactly(
                "accounts", "flyway_schema_history", "pinned_group_transactions", "pinned_groups",
                "savings_goals", "statement_imports", "transactions", "transfers", "users");
    }

    @Test
    void storesMoneyAsExactDecimalNeverFloatingPoint() {
        List<String> types = jdbc.queryForList(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 'transactions' AND column_name IN ('amount', 'original_amount')",
                String.class);

        assertThat(types).isNotEmpty().allMatch("numeric"::equals);

        assertThat(jdbc.queryForObject(
                "SELECT numeric_precision::text || ',' || numeric_scale::text FROM information_schema.columns "
                        + "WHERE table_name = 'transactions' AND column_name = 'amount'", String.class))
                .isEqualTo("19,4");
    }

    @Test
    void usesADateNotATimestampForTheAnalyticalAxis() {
        assertThat(columnType("transactions", "booking_date")).isEqualTo("date");
        assertThat(columnType("transactions", "occurred_on")).isEqualTo("date");
    }

    @Test
    void keepsBookingDateMandatoryAndOccurredOnOptional() {
        assertThat(isNullable("transactions", "booking_date")).isFalse();
        assertThat(isNullable("transactions", "occurred_on")).isTrue();
    }

    @Test
    void carriesTheCompositeForeignKeysThatPinOwnershipAndCurrency() {
        assertThat(foreignKeyColumns("fk_transactions_account"))
                .containsExactly("account_id", "user_id", "currency");
        assertThat(foreignKeyColumns("fk_transactions_import"))
                .containsExactly("statement_import_id", "account_id");
        assertThat(foreignKeyColumns("fk_statement_imports_account"))
                .containsExactly("account_id", "user_id");
        assertThat(foreignKeyColumns("fk_transfers_outgoing"))
                .containsExactly("outgoing_transaction_id", "user_id");
        assertThat(foreignKeyColumns("fk_transfers_incoming"))
                .containsExactly("incoming_transaction_id", "user_id");
    }

    @Test
    void freezesAccountOwnerAndCurrencyWhileTransactionsExist() {
        // confupdtype 'r' is ON UPDATE RESTRICT; confdeltype 'c' is ON DELETE CASCADE.
        assertThat(jdbc.queryForObject(
                "SELECT confupdtype::text || confdeltype::text FROM pg_constraint "
                        + "WHERE conname = 'fk_transactions_account'", String.class))
                .isEqualTo("rc");
    }

    @Test
    void deduplicatesOnAccountFingerprintAndOccurrence() {
        assertThat(uniqueConstraintColumns("uq_transactions_dedup"))
                .containsExactly("account_id", "fingerprint", "occurrence");
    }

    @Test
    void indexesTheBanksOwnTransactionIdOnlyWhereItExists() {
        String definition = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'ux_transactions_external_id'", String.class);

        assertThat(definition)
                .contains("UNIQUE")
                .contains("account_id", "external_id")
                .contains("WHERE (external_id IS NOT NULL)");
    }

    @Test
    void indexesTheColumnsAnalyticsWillGroupAndFilterBy() {
        List<String> indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'transactions' ORDER BY indexname",
                String.class);

        assertThat(indexes).contains(
                "ix_transactions_user_booking_date",
                "ix_transactions_account_booking_date",
                "ix_transactions_user_category_booking_date");
    }

    @Test
    void storesNoAccountIdentifiersOrStatementContentAnywhere() {
        List<String> columns = jdbc.queryForList(
                "SELECT table_name::text || '.' || column_name::text FROM information_schema.columns "
                        + "WHERE table_schema = 'public'", String.class);

        assertThat(columns).noneMatch(column -> {
            String name = column.toLowerCase();
            return name.contains("account_number") || name.contains("sort_code")
                    || name.contains("card_number") || name.contains("iban")
                    || name.contains("filename") || name.contains("raw_");
        });
    }

    private String columnType(String table, String column) {
        return jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = ? AND column_name = ?", String.class, table, column);
    }

    private boolean isNullable(String table, String column) {
        return "YES".equals(jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_name = ? AND column_name = ?", String.class, table, column));
    }

    private List<String> foreignKeyColumns(String constraintName) {
        return jdbc.queryForList(
                "SELECT a.attname FROM pg_constraint c "
                        + "JOIN unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord) ON true "
                        + "JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum "
                        + "WHERE c.conname = ? AND c.contype = 'f' ORDER BY k.ord",
                String.class, constraintName);
    }

    private List<String> uniqueConstraintColumns(String constraintName) {
        return jdbc.queryForList(
                "SELECT a.attname FROM pg_constraint c "
                        + "JOIN unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord) ON true "
                        + "JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum "
                        + "WHERE c.conname = ? AND c.contype = 'u' ORDER BY k.ord",
                String.class, constraintName);
    }
}
