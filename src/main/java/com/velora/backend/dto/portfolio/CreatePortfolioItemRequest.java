package com.velora.backend.dto.portfolio;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * styleTag/priceEstimate are optional - supplying either creates the item's
 * InteriorDesignDetails satellite; a painter/plumber/electrician/carpenter simply
 * omits both and gets a plain PortfolioItem with no satellite row.
 */
public record CreatePortfolioItemRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @NotNull Long categoryId,
        @Size(max = 1000) String coverImageUrl,
        @Size(max = 100) String styleTag,
        @DecimalMin("0") BigDecimal priceEstimate
) {
}
