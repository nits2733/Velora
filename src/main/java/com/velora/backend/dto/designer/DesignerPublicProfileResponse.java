package com.velora.backend.dto.designer;

import com.velora.backend.entity.AvailabilityStatus;

import java.math.BigDecimal;

public record DesignerPublicProfileResponse(
        Long id,
        String fullName,
        String bio,
        Integer yearsExperience,
        String specialization,
        String city,
        AvailabilityStatus availabilityStatus,
        BigDecimal averageRating,
        int ratingCount
) {
}
