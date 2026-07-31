package com.velora.backend.mapper;

import com.velora.backend.dto.designer.DesignerPublicProfileResponse;
import com.velora.backend.entity.DesignerProfile;
import com.velora.backend.entity.User;
import org.springframework.stereotype.Component;

@Component
public class DesignerMapper {

    public DesignerPublicProfileResponse toPublicProfileResponse(User user, DesignerProfile profile) {
        return new DesignerPublicProfileResponse(
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
