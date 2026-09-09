package com.fapp.pinned;

/**
 * Thrown when a pinned group cannot be resolved for the user asking for it.
 *
 * <p>A group belonging to somebody else reports the same thing as one that does not
 * exist, so the API cannot be used to discover what another user is tracking.
 */
public class PinnedGroupNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public PinnedGroupNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
