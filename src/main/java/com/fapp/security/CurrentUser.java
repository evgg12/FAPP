package com.fapp.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Who is making this request. */
@Component
public class CurrentUser {

    /** The authenticated user's id, or empty if the request is not authenticated. */
    public Optional<UUID> id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof FappUserDetails details) {
            return Optional.of(details.userId());
        }
        return Optional.empty();
    }

    /**
     * @throws ForbiddenException if the request is not authenticated as this user
     */
    public UUID requireId() {
        return id().orElseThrow(() -> new ForbiddenException("this request is not authenticated"));
    }

    /**
     * Refuses a request for somebody else's data.
     *
     * @throws ForbiddenException if the authenticated user is not {@code userId}
     */
    public void requireSelf(UUID userId) {
        if (!requireId().equals(userId)) {
            throw new ForbiddenException("this request is for another user's data");
        }
    }
}
