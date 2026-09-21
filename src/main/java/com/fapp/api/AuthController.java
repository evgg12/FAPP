package com.fapp.api;

import com.fapp.security.CurrentUser;
import com.fapp.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who the caller is.
 *
 * <p>There is no login endpoint to post to: authentication travels with each request, so
 * this is how a client checks that a credential works and finds out which user it
 * belongs to. A 401 from here means the credential is wrong; a 200 carries the user id
 * every other endpoint is scoped by.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Verify authentication credentials and retrieve authenticated user info")
class AuthController {

    private final CurrentUser currentUser;
    private final UserRepository users;

    AuthController(CurrentUser currentUser, UserRepository users) {
        this.currentUser = currentUser;
        this.users = users;
    }

    @GetMapping("/me")
    @Operation(summary = "Get authenticated user info",
            description = "Verifies the authentication credentials are valid and returns the authenticated user's information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully authenticated, returns user details",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or missing authentication credentials")
    })
    @SecurityRequirement(name = "basicAuth")
    UserResponse me() {
        return users.findById(currentUser.requireId())
                .map(UserResponse::of)
                .orElseThrow(() -> new NotFoundException(
                        "USER_NOT_FOUND", "the authenticated user no longer exists"));
    }
}
