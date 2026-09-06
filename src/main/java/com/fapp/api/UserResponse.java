package com.fapp.api;

import com.fapp.user.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName, Instant createdAt) {

    public static UserResponse of(User user) {
        return new UserResponse(user.id(), user.email(), user.displayName(), user.createdAt());
    }
}
