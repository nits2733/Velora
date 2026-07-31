package com.velora.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioItemResponse(
        Long id,
        String title,
        String description,
        CategoryResponse category,
        Long professionalId,
        String professionalName,
        String coverImageUrl,
        BigDecimal priceEstimate,
        String styleTag,
        Instant createdAt
) {
}
