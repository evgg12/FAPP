package com.fapp.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * The user with this address. Emails are stored lowercased, so callers lowercase
     * before asking; the unique index is on {@code lower(email)} either way.
     */
    Optional<User> findByEmail(String email);
}
