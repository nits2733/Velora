package com.velora.backend.service;

import com.velora.backend.dto.professional.ProfessionalPublicProfileResponse;
import com.velora.backend.entity.ProfessionalProfile;
import com.velora.backend.entity.Role;
import com.velora.backend.entity.User;
import com.velora.backend.exception.ResourceNotFoundException;
import com.velora.backend.mapper.ProfessionalMapper;
import com.velora.backend.repository.ProfessionalProfileRepository;
import com.velora.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfessionalService {

    private final UserRepository userRepository;
    private final ProfessionalProfileRepository professionalProfileRepository;
    private final ProfessionalMapper professionalMapper;

    @Transactional(readOnly = true)
    public ProfessionalPublicProfileResponse getPublicProfile(Long professionalId) {
        User user = userRepository.findById(professionalId)
                .filter(u -> u.getRole() == Role.PROFESSIONAL)
                .orElseThrow(() -> new ResourceNotFoundException("Professional not found: " + professionalId));

        ProfessionalProfile profile = professionalProfileRepository.findByUserId(professionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Professional profile not found: " + professionalId));

        return professionalMapper.toPublicProfileResponse(user, profile);
    }
}
