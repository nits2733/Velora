package com.velora.backend.dto.portfolio;

import java.math.BigDecimal;

public record PortfolioItemSummaryResponse(
        Long id,
        String title,
        String categoryName,
        String coverImageUrl,
        BigDecimal priceEstimate,
        String styleTag
) {
}
