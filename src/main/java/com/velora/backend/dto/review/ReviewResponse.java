package com.velora.backend.dto.review;

import java.time.Instant;

public record ReviewResponse(
        Long id,
        Long bookingId,
        Integer rating,
        String comment,
        Instant createdAt
) {
}
