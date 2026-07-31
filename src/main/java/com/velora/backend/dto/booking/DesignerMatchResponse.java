package com.velora.backend.dto.booking;

import com.velora.backend.entity.AvailabilityStatus;

import java.math.BigDecimal;

public record DesignerMatchResponse(
        Long designerId,
        String fullName,
        String specialization,
        String city,
        Integer yearsExperience,
        BigDecimal averageRating,
        int ratingCount,
        AvailabilityStatus availabilityStatus,
        int score
) {
}
