package com.velora.backend.dto.booking;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * designerId is optional: present means the customer picked this designer directly;
 * absent means the customer wants Velora to assign one (booking starts in
 * PENDING_ASSIGNMENT until an admin assigns a designer). categoryId/preferredStyle/
 * budget/location are only meaningful on that assignment-request path - they feed
 * DesignerMatchingService's scoring and are otherwise unused.
 */
public record BookingRequest(
        Long designerId,
        Long designId,
        @NotNull @Future Instant scheduledAt,
        @Size(max = 1000) String notes,
        Long categoryId,
        @Size(max = 100) String preferredStyle,
        @DecimalMin("0") BigDecimal budget,
        @Size(max = 150) String location
) {
}
