package com.velora.backend.service;

import com.velora.backend.dto.portfolio.PortfolioItemResponse;
import com.velora.backend.dto.portfolio.PortfolioItemSummaryResponse;
import com.velora.backend.entity.PortfolioItem;
import com.velora.backend.exception.ResourceNotFoundException;
import com.velora.backend.mapper.PortfolioItemMapper;
import com.velora.backend.repository.PortfolioItemRepository;
import com.velora.backend.repository.PortfolioItemSpecifications;
import com.velora.backend.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PortfolioItemService {

    private final PortfolioItemRepository portfolioItemRepository;
    private final PortfolioItemMapper portfolioItemMapper;

    @Transactional(readOnly = true)
    public PageResponse<PortfolioItemSummaryResponse> search(Long categoryId, String search, String style, Pageable pageable) {
        Specification<PortfolioItem> spec = Specification
                .where(PortfolioItemSpecifications.hasCategory(categoryId))
                .and(PortfolioItemSpecifications.matchesSearch(search))
                .and(PortfolioItemSpecifications.hasStyle(style));

        Page<PortfolioItemSummaryResponse> page = portfolioItemRepository.findAll(spec, pageable)
                .map(portfolioItemMapper::toSummaryResponse);

        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PortfolioItemResponse getById(Long id) {
        PortfolioItem item = portfolioItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio item not found: " + id));
        return portfolioItemMapper.toResponse(item);
    }
}
