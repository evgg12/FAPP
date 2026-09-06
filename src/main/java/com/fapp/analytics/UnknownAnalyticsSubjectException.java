package com.fapp.analytics;

/**
 * Thrown when analytics are asked for a user or account that cannot be resolved.
 *
 * <p>Carries the stable error code the API should answer with, so the web layer does not
 * have to work out which of the two was missing. An account that exists but belongs to
 * somebody else reports the same code as one that does not exist at all: telling those
 * apart would let a caller enumerate other people's accounts.
 */
public class UnknownAnalyticsSubjectException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public UnknownAnalyticsSubjectException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
