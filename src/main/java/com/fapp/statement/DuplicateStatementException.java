package com.fapp.statement;

/**
 * Thrown when this exact file has already been imported into this account, matching
 * {@code uq_statement_imports_content}.
 *
 * <p>Its own type so a caller can tell it apart from the other reasons an import fails:
 * nothing is wrong with the file, it has simply been seen before, and the right answer
 * is to say so rather than to retry.
 */
public class DuplicateStatementException extends StatementImportException {

    private static final long serialVersionUID = 1L;

    public DuplicateStatementException(String message) {
        super(message);
    }
}
