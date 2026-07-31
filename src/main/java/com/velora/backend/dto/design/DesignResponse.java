package com.velora.backend.dto.design;

import java.math.BigDecimal;
import java.time.Instant;

public record DesignResponse(
        Long id,
        String title,
        String description,
        CategoryResponse category,
        Long designerId,
        String designerName,
        String coverImageUrl,
        BigDecimal priceEstimate,
        String styleTag,
        Instant createdAt
) {
}
