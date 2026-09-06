package com.fapp.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param email       the address that identifies the user; stored lowercased
 * @param displayName what to call them in the interface
 * @param password    what they will sign in with. Hashed before it is stored and never
 *                    returned by any endpoint. A minimum length is enforced because it
 *                    is the only thing standing between a stranger and someone's
 *                    financial history.
 */
public record CreateUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Size(min = 12, max = 200) String password) {
}
