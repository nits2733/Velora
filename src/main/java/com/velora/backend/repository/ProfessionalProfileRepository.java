package com.velora.backend.repository;

import com.velora.backend.entity.AvailabilityStatus;
import com.velora.backend.entity.ProfessionalProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ProfessionalProfileRepository extends JpaRepository<ProfessionalProfile, Long>,
        JpaSpecificationExecutor<ProfessionalProfile> {
    Optional<ProfessionalProfile> findByUserId(Long userId);

    List<ProfessionalProfile> findByAvailabilityStatus(AvailabilityStatus status);
}
