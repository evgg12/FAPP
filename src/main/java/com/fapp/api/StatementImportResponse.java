package com.fapp.api;

import com.fapp.statement.StatementImport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The outcome of an import, as the import service determined it.
 *
 * <p>The three counts are the whole point: {@code rowCount} is how many movements the
 * statement described, {@code importedCount} how many were new, and
 * {@code duplicateCount} how many the account already held. Re-sending an overlapping
 * statement is a normal thing to do, and this is how a caller sees that it was handled
 * rather than doubled.
 *
 * <p>The content hash is deliberately absent. It exists to recognise a file already
 * seen and is of no use to a caller.
 */
public record StatementImportResponse(
        UUID id,
        UUID accountId,
        String provider,
        LocalDate periodStart,
        LocalDate periodEnd,
        int rowCount,
        int importedCount,
        int duplicateCount,
        Instant importedAt) {

    /** @param statementImport must have been loaded with its account */
    public static StatementImportResponse of(StatementImport statementImport) {
        return new StatementImportResponse(
                statementImport.id(),
                statementImport.account().id(),
                statementImport.provider(),
                statementImport.period().start(),
                statementImport.period().end(),
                statementImport.rowCount(),
                statementImport.importedCount(),
                statementImport.duplicateCount(),
                statementImport.importedAt());
    }
}
