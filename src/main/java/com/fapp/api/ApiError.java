package com.fapp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * The single shape every failed request comes back in.
 *
 * <p>{@code code} is the part a client is meant to branch on: a stable, screaming-snake
 * token that will not change when the wording does. {@code message} is for a person
 * reading a log or a form, and {@code fields} appears only when individual inputs were
 * rejected, so a caller can put the message next to the offending field.
 *
 * <p>Deliberately carries no stack trace, no exception class name and no SQL. A failed
 * import says what was wrong with the statement, not how FAPP is built.
 *
 * @param code      stable machine-readable identifier, e.g. {@code ACCOUNT_NOT_FOUND}
 * @param message   human-readable explanation
 * @param fields    per-field messages for a rejected request body, or {@code null}
 * @param timestamp when the failure was produced
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, String> fields, Instant timestamp) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null, Instant.now());
    }

    public static ApiError of(String code, String message, Map<String, String> fields) {
        return new ApiError(code, message, fields.isEmpty() ? null : Map.copyOf(fields), Instant.now());
    }
}
