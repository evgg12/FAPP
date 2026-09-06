package com.fapp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Enforces that a request naming a user in its path is that user's own request.
 *
 * <p>One place rather than a check repeated in every handler. Every user-scoped
 * endpoint — analytics, goals, simulations, recategorisation, the user itself — sits
 * under {@code /api/users/{userId}}, so comparing that path variable against the
 * authenticated principal covers all of them at once and cannot be forgotten when the
 * next one is added.
 *
 * <p>It deliberately does not try to cover resources reached by their own id, such as
 * {@code /api/accounts/{accountId}}: proving those belong to the caller means loading
 * them, which is the handler's job, and guessing here would be worse than not trying.
 */
@Component
public class UserScopeInterceptor implements HandlerInterceptor {

    private final CurrentUser currentUser;

    UserScopeInterceptor(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(variables instanceof Map<?, ?> templateVariables)) {
            return true;
        }
        Object userId = templateVariables.get("userId");
        if (userId == null) {
            return true;
        }
        try {
            currentUser.requireSelf(UUID.fromString(userId.toString()));
        } catch (IllegalArgumentException e) {
            // Not a UUID at all: let the handler's own conversion report it, so a
            // malformed id is a bad request rather than a forbidden one.
            return true;
        }
        return true;
    }
}
