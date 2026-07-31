package com.velora.backend.mapper;

import com.velora.backend.dto.portfolio.CategoryResponse;
import com.velora.backend.dto.portfolio.PortfolioItemResponse;
import com.velora.backend.dto.portfolio.PortfolioItemSummaryResponse;
import com.velora.backend.entity.Category;
import com.velora.backend.entity.InteriorDesignDetails;
import com.velora.backend.entity.PortfolioItem;
import org.springframework.stereotype.Component;

@Component
public class PortfolioItemMapper {

    public CategoryResponse toCategoryResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(),
                category.getServiceGroup());
    }

    public PortfolioItemSummaryResponse toSummaryResponse(PortfolioItem item) {
        InteriorDesignDetails details = item.getInteriorDesignDetails();
        return new PortfolioItemSummaryResponse(
                item.getId(),
                item.getTitle(),
                item.getCategory().getName(),
                item.getCoverImageUrl(),
                details != null ? details.getPriceEstimate() : null,
                details != null ? details.getStyleTag() : null
        );
    }

    public PortfolioItemResponse toResponse(PortfolioItem item) {
        InteriorDesignDetails details = item.getInteriorDesignDetails();
        return new PortfolioItemResponse(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                toCategoryResponse(item.getCategory()),
                item.getProfessional().getId(),
                item.getProfessional().getFullName(),
                item.getCoverImageUrl(),
                details != null ? details.getPriceEstimate() : null,
                details != null ? details.getStyleTag() : null,
                item.getCreatedAt()
        );
    }
}
