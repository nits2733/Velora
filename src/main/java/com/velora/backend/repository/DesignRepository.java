package com.velora.backend.repository;

import com.velora.backend.entity.Design;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DesignRepository extends JpaRepository<Design, Long>, JpaSpecificationExecutor<Design> {
}
