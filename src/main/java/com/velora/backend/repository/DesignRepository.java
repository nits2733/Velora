package com.velora.backend.repository;

import com.velora.backend.entity.Design;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface DesignRepository extends JpaRepository<Design, Long>, JpaSpecificationExecutor<Design> {
    long countByDesignerIdAndCategoryId(Long designerId, Long categoryId);

    long countByDesignerIdAndStyleTagIgnoreCase(Long designerId, String styleTag);

    List<Design> findByDesignerId(Long designerId);
}
