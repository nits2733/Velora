package com.velora.backend.service;

import com.velora.backend.dto.user.UpdateProfileRequest;
import com.velora.backend.dto.user.UserProfileResponse;
import com.velora.backend.entity.DesignerProfile;
import com.velora.backend.entity.Role;
import com.velora.backend.entity.User;
import com.velora.backend.exception.ResourceNotFoundException;
import com.velora.backend.mapper.UserMapper;
import com.velora.backend.repository.DesignerProfileRepository;
import com.velora.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DesignerProfileRepository designerProfileRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = findUser(userId);
        DesignerProfile designerProfile = user.getRole() == Role.DESIGNER
                ? designerProfileRepository.findByUserId(userId).orElse(null)
                : null;
        return userMapper.toProfileResponse(user, designerProfile);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        userRepository.save(user);

        DesignerProfile designerProfile = null;
        if (user.getRole() == Role.DESIGNER) {
            designerProfile = designerProfileRepository.findByUserId(userId)
                    .orElseGet(() -> DesignerProfile.builder().user(user).build());

            if (request.bio() != null) {
                designerProfile.setBio(request.bio());
            }
            if (request.yearsExperience() != null) {
                designerProfile.setYearsExperience(request.yearsExperience());
            }
            if (request.specialization() != null) {
                designerProfile.setSpecialization(request.specialization());
            }
            if (request.city() != null) {
                designerProfile.setCity(request.city());
            }
            if (request.availabilityStatus() != null) {
                designerProfile.setAvailabilityStatus(request.availabilityStatus());
            }
            designerProfile = designerProfileRepository.save(designerProfile);
        }

        return userMapper.toProfileResponse(user, designerProfile);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}
