package com.velora.backend.service;

import com.velora.backend.dto.design.DesignResponse;
import com.velora.backend.dto.design.DesignSummaryResponse;
import com.velora.backend.entity.Design;
import com.velora.backend.exception.ResourceNotFoundException;
import com.velora.backend.mapper.DesignMapper;
import com.velora.backend.repository.DesignRepository;
import com.velora.backend.repository.DesignSpecifications;
import com.velora.backend.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DesignService {

    private final DesignRepository designRepository;
    private final DesignMapper designMapper;

    @Transactional(readOnly = true)
    public PageResponse<DesignSummaryResponse> search(Long categoryId, String search, String style, Pageable pageable) {
        Specification<Design> spec = Specification
                .where(DesignSpecifications.hasCategory(categoryId))
                .and(DesignSpecifications.matchesSearch(search))
                .and(DesignSpecifications.hasStyle(style));

        Page<DesignSummaryResponse> page = designRepository.findAll(spec, pageable)
                .map(designMapper::toSummaryResponse);

        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public DesignResponse getById(Long id) {
        Design design = designRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Design not found: " + id));
        return designMapper.toResponse(design);
    }
}
