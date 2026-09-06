package com.fapp.statement;

/**
 * Reads one bank's statement format and produces FAPP's provider-independent
 * {@link RawTransaction} rows.
 *
 * <p>This is the only place bank-specific knowledge is allowed to live. An adapter
 * owns its format's column layout, character encoding, date and decimal formats, its
 * bank's transaction code list, and which sign that bank writes a debit with — and it
 * resolves all of it before returning, so nothing downstream branches on which bank a
 * record came from. Supporting another bank means adding an implementation of this
 * interface; no existing type, table or service changes.
 *
 * <p>Implementations are expected to be stateless and safe to reuse across imports.
 */
public interface StatementAdapter {

    /**
     * The provider this adapter reads, as a stable slug such as
     * {@code bank_of_scotland} or {@code monzo}.
     *
     * <p>Must be identical to the value stored in {@code accounts.provider} for
     * accounts at this bank, and must match {@code ^[a-z][a-z0-9_]*$} — the format the
     * database enforces. This is how the application layer picks the right adapter for
     * a target account without knowing which adapters exist.
     */
    String provider();

    /**
     * Parses an uploaded statement.
     *
     * <p>Takes bytes rather than text because character encoding is part of a bank's
     * format, not a platform-wide assumption, and so is the adapter's business to
     * decide. Implementations must not modify the array.
     *
     * <p>All or nothing: an implementation must either return every data row in the
     * statement or throw. Returning the rows it managed to read would let a malformed
     * statement be partially imported, which FAPP does not allow.
     *
     * @param statement the raw bytes of the uploaded statement
     * @return the statement's rows and the period it covers
     * @throws StatementParseException if the statement is not in this provider's
     *                                 format, or any data row cannot be read
     */
    ParsedStatement parse(byte[] statement);
}
