package com.fapp.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param notes free-form, optional; nothing here is interpreted
 */
public record CreatePinnedGroupRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String notes) {
}
