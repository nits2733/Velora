package com.velora.backend.mapper;

import com.velora.backend.dto.user.DesignerProfileResponse;
import com.velora.backend.dto.user.UserProfileResponse;
import com.velora.backend.entity.DesignerProfile;
import com.velora.backend.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserProfileResponse toProfileResponse(User user, DesignerProfile designerProfile) {
        DesignerProfileResponse designerResponse = designerProfile == null ? null : new DesignerProfileResponse(
                designerProfile.getBio(),
                designerProfile.getYearsExperience(),
                designerProfile.getSpecialization(),
                designerProfile.getCity()
        );

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.getCreatedAt(),
                designerResponse
        );
    }
}
