package com.velora.backend.dto.review;

import java.time.Instant;

/**
 * A review as shown on a professional's public page. Deliberately not
 * {@link ReviewResponse}: that one is handed back to the customer who just wrote the
 * review and carries the {@code bookingId}, which is an internal identifier a stranger
 * browsing the directory has no business receiving. What's left is the review itself
 * plus who wrote it, for credibility.
 */
public record PublicReviewResponse(
        Long id,
        Integer rating,
        String comment,
        String customerName,
        Instant createdAt
) {
}
