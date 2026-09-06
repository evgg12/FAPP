package com.fapp.analytics;

import java.util.Objects;
import java.util.UUID;

/**
 * Whose figures are being asked for: one user, and optionally one of their accounts.
 *
 * @param userId    the owner; every query is filtered by it
 * @param accountId a single account of theirs, or {@code null} for all of them
 */
public record AnalyticsScope(UUID userId, UUID accountId) {

    public AnalyticsScope {
        Objects.requireNonNull(userId, "userId must not be null");
    }

    public static AnalyticsScope ofUser(UUID userId) {
        return new AnalyticsScope(userId, null);
    }

    public static AnalyticsScope of(UUID userId, UUID accountId) {
        return new AnalyticsScope(userId, accountId);
    }
}
