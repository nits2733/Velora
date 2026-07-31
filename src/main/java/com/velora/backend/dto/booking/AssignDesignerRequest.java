package com.velora.backend.dto.booking;

import jakarta.validation.constraints.NotNull;

public record AssignDesignerRequest(
        @NotNull Long designerId
) {
}
