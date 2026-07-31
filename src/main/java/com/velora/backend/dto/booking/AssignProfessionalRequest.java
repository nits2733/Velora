package com.velora.backend.dto.booking;

import jakarta.validation.constraints.NotNull;

public record AssignProfessionalRequest(
        @NotNull Long professionalId
) {
}
