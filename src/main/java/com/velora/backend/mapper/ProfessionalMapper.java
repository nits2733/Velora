package com.velora.backend.mapper;

import com.velora.backend.dto.professional.ProfessionalPublicProfileResponse;
import com.velora.backend.entity.ProfessionalProfile;
import com.velora.backend.entity.User;
import org.springframework.stereotype.Component;

@Component
public class ProfessionalMapper {

    public ProfessionalPublicProfileResponse toPublicProfileResponse(User user, ProfessionalProfile profile) {
        return new ProfessionalPublicProfileResponse(
                user.getId(),
                user.getFullName(),
                profile.getBio(),
                profile.getYearsExperience(),
                profile.getSpecialization(),
                profile.getCity(),
                profile.getAvailabilityStatus(),
                profile.getAverageRating(),
                profile.getRatingCount()
        );
    }
}
