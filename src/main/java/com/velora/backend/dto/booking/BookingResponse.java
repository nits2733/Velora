package com.velora.backend.dto.booking;

import com.velora.backend.entity.BookingStatus;

import java.time.Instant;

public record BookingResponse(
        Long id,
        Long customerId,
        String customerName,
        Long designerId,
        String designerName,
        Long designId,
        String designTitle,
        Instant scheduledAt,
        BookingStatus status,
        String notes,
        Instant createdAt
) {
}
