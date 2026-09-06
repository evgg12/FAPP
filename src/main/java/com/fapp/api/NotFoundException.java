package com.fapp.api;

/**
 * Thrown when a path refers to something that does not exist. Carries the stable error
 * code the response should use, so the handler does not have to guess which resource
 * was missing.
 */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public NotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
