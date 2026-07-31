package com.velora.backend.dto.user;

public record DesignerProfileResponse(
        String bio,
        Integer yearsExperience,
        String specialization,
        String city
) {
}
