package com.velora.backend.repository;

import com.velora.backend.entity.AvailabilityStatus;
import com.velora.backend.entity.ProfessionalProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfessionalProfileRepository extends JpaRepository<ProfessionalProfile, Long> {
    Optional<ProfessionalProfile> findByUserId(Long userId);

    List<ProfessionalProfile> findByAvailabilityStatus(AvailabilityStatus status);
}
