package com.fapp.api;

import com.fapp.security.CurrentUser;
import com.fapp.user.UserRepository;
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
class AuthController {

    private final CurrentUser currentUser;
    private final UserRepository users;

    AuthController(CurrentUser currentUser, UserRepository users) {
        this.currentUser = currentUser;
        this.users = users;
    }

    @GetMapping("/me")
    UserResponse me() {
        return users.findById(currentUser.requireId())
                .map(UserResponse::of)
                .orElseThrow(() -> new NotFoundException(
                        "USER_NOT_FOUND", "the authenticated user no longer exists"));
    }
}
