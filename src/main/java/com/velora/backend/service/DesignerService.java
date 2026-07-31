package com.velora.backend.service;

import com.velora.backend.dto.designer.DesignerPublicProfileResponse;
import com.velora.backend.entity.DesignerProfile;
import com.velora.backend.entity.Role;
import com.velora.backend.entity.User;
import com.velora.backend.exception.ResourceNotFoundException;
import com.velora.backend.mapper.DesignerMapper;
import com.velora.backend.repository.DesignerProfileRepository;
import com.velora.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DesignerService {

    private final UserRepository userRepository;
    private final DesignerProfileRepository designerProfileRepository;
    private final DesignerMapper designerMapper;

    @Transactional(readOnly = true)
    public DesignerPublicProfileResponse getPublicProfile(Long designerId) {
        User user = userRepository.findById(designerId)
                .filter(u -> u.getRole() == Role.DESIGNER)
                .orElseThrow(() -> new ResourceNotFoundException("Designer not found: " + designerId));

        DesignerProfile profile = designerProfileRepository.findByUserId(designerId)
                .orElseThrow(() -> new ResourceNotFoundException("Designer profile not found: " + designerId));

        return designerMapper.toPublicProfileResponse(user, profile);
    }
}
