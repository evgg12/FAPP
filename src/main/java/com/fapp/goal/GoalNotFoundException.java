package com.fapp.goal;

/**
 * Thrown when a goal cannot be resolved for the user asking for it.
 *
 * <p>A goal belonging to somebody else reports the same thing as one that does not
 * exist, so the API cannot be used to discover what other people are saving for.
 */
public class GoalNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public GoalNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
