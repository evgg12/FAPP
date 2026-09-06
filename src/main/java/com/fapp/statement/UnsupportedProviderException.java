package com.fapp.statement;

/**
 * Thrown when no adapter reads the bank an account is configured for.
 *
 * <p>Its own type because it says something about the account rather than the upload:
 * the file may be perfectly good, but FAPP cannot read that bank's format yet, and
 * re-sending it will not help.
 */
public class UnsupportedProviderException extends StatementImportException {

    private static final long serialVersionUID = 1L;

    public UnsupportedProviderException(String message) {
        super(message);
    }
}
