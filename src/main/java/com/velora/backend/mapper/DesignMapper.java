package com.velora.backend.mapper;

import com.velora.backend.dto.design.CategoryResponse;
import com.velora.backend.dto.design.DesignResponse;
import com.velora.backend.dto.design.DesignSummaryResponse;
import com.velora.backend.entity.Category;
import com.velora.backend.entity.Design;
import org.springframework.stereotype.Component;

@Component
public class DesignMapper {

    public CategoryResponse toCategoryResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription());
    }

    public DesignSummaryResponse toSummaryResponse(Design design) {
        return new DesignSummaryResponse(
                design.getId(),
                design.getTitle(),
                design.getCategory().getName(),
                design.getCoverImageUrl(),
                design.getPriceEstimate(),
                design.getStyleTag()
        );
    }

    public DesignResponse toResponse(Design design) {
        return new DesignResponse(
                design.getId(),
                design.getTitle(),
                design.getDescription(),
                toCategoryResponse(design.getCategory()),
                design.getDesigner().getId(),
                design.getDesigner().getFullName(),
                design.getCoverImageUrl(),
                design.getPriceEstimate(),
                design.getStyleTag(),
                design.getCreatedAt()
        );
    }
}
