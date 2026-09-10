package com.fapp.api;

import com.fapp.account.AccountRepository;
import com.fapp.security.CurrentUser;
import com.fapp.user.User;
import com.fapp.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@Tag(name = "Users", description = "User registration and retrieval")
class UserController {

    private final UserRepository users;
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    UserController(UserRepository users,
                   AccountRepository accounts,
                   PasswordEncoder passwordEncoder,
                   CurrentUser currentUser) {
        this.users = users;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Operation(summary = "Register a new user",
            description = "Creates a new user account. The password is hashed and never returned.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User successfully created",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input or validation failed"),
            @ApiResponse(responseCode = "409", description = "User with this email already exists")
    })
    ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User user = User.of(request.email(), request.displayName());
        // Hashed here and never held: the plaintext exists only for the length of this
        // call, and UserResponse has no field it could be returned in.
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        // Answered from what save returned: the created timestamp is stamped on the
        // managed instance, and a user has no association to resolve afterwards.
        User created = users.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.of(created));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID",
            description = "Retrieves a user's information. Only the user themselves can retrieve their own info.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or missing authentication credentials"),
            @ApiResponse(responseCode = "403", description = "Accessing another user's information"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @SecurityRequirement(name = "basicAuth")
    UserResponse get(@PathVariable UUID userId) {
        currentUser.requireSelf(userId);
        return users.findById(userId)
                .map(UserResponse::of)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "no user with id " + userId));
    }

    /**
     * The user's accounts.
     *
     * <p>Its own endpoint because listing accounts is not an analytics question. The
     * per-account analytics breakdown happens to return every account and was standing in
     * for this, which meant asking for a list required inventing a date range.
     */
    @GetMapping("/{userId}/accounts")
    @Operation(summary = "List user's accounts",
            description = "Returns all accounts belonging to the user, ordered by provider and display name")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of user's accounts"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing authentication credentials"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @SecurityRequirement(name = "basicAuth")
    List<AccountResponse> accounts(@PathVariable UUID userId) {
        if (!users.existsById(userId)) {
            throw new NotFoundException("USER_NOT_FOUND", "no user with id " + userId);
        }
        return accounts.findByUser_IdOrderByProviderAscDisplayNameAsc(userId).stream()
                .map(AccountResponse::of)
                .toList();
    }
}
