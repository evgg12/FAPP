package com.fapp.security;

/**
 * Thrown when an authenticated user asks for data that is not theirs.
 *
 * <p>Distinct from not being signed in at all: the caller is known, and the answer is
 * that being known is not enough. Reported as 403 rather than 404 because the path
 * pattern itself carries the user id the caller supplied — they already know it exists,
 * so there is nothing to conceal by pretending otherwise. Resources reached by an id that
 * does <em>not</em> name its owner, such as an account or an import, answer 404 instead.
 */
public class ForbiddenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException(String message) {
        super(message);
    }
}
