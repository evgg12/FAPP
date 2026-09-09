package com.fapp.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Everything about a pinned group that may change: its name and its notes. */
public record UpdatePinnedGroupRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String notes) {
}
