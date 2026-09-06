package com.fapp.api;

import com.fapp.user.User;
import com.fapp.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Creating the owner every account and transaction hangs off.
 *
 * <p>Registration, credentials and sessions are a separate concern that does not exist
 * yet: this creates the record that owns financial data, and nothing more.
 */
@RestController
@RequestMapping("/api/users")
class UserController {

    private final UserRepository users;

    UserController(UserRepository users) {
        this.users = users;
    }

    @PostMapping
    ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        // Answered from what save returned: the created timestamp is stamped on the
        // managed instance, and a user has no association to resolve afterwards.
        User created = users.save(User.of(request.email(), request.displayName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.of(created));
    }
}
