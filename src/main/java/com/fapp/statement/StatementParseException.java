package com.fapp.statement;

/**
 * Thrown when a statement cannot be read: wrong format for the adapter, a corrupt
 * upload, or a data row FAPP cannot make sense of.
 *
 * <p>Distinct from a programming error on purpose. It means the upload is at fault, so
 * the import is rejected whole and reported back to the user rather than retried, and
 * the application layer can tell this apart from a genuine bug. Unchecked because a
 * malformed upload is a validation outcome, not a condition every caller in the chain
 * should have to declare.
 *
 * <p>Messages are user-facing: implementations should say what was wrong and where,
 * for example {@code "row 12: 'ninety pounds' is not an amount"}.
 */
public class StatementParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StatementParseException(String message) {
        super(message);
    }

    public StatementParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
