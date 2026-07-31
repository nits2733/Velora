package com.velora.backend.dto.design;

import java.math.BigDecimal;

public record DesignSummaryResponse(
        Long id,
        String title,
        String categoryName,
        String coverImageUrl,
        BigDecimal priceEstimate,
        String styleTag
) {
}
