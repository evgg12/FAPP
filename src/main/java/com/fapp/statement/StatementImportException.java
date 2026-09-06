package com.fapp.statement;

/**
 * Thrown when a statement cannot be imported for a reason outside the statement's own
 * contents: no adapter reads the account's bank, the file has already been imported, or
 * a concurrent import stored the same transactions first.
 *
 * <p>Distinct from {@link StatementParseException}, which means the file itself is
 * unreadable. Both are unchecked, and both mean the caller's request failed rather than
 * that something needs retrying blindly.
 */
public class StatementImportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StatementImportException(String message) {
        super(message);
    }

    public StatementImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
