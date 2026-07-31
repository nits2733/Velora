package com.velora.backend.dto.booking;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record BookingRequest(
        @NotNull Long designerId,
        Long designId,
        @NotNull @Future Instant scheduledAt,
        @Size(max = 1000) String notes
) {
}
