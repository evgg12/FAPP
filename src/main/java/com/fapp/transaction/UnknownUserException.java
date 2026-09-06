package com.fapp.transaction;

/** Thrown when an operation names a user that does not exist. */
public class UnknownUserException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnknownUserException(String message) {
        super(message);
    }
}
