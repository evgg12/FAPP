package com.fapp.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated user, carrying the id everything in FAPP is scoped by.
 *
 * <p>The id matters more than the name here: every account, transaction, goal and
 * analytical figure belongs to a user id, so authorisation is a comparison against this
 * rather than a lookup by email on every request.
 *
 * <p>There are no roles. FAPP has one kind of user, who may see their own financial data
 * and nobody else's, and inventing an authority hierarchy for that would be pretending
 * to a distinction the application does not have.
 */
public record FappUserDetails(UUID userId, String email, String passwordHash) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
