package com.velora.backend.dto.portfolio;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Used for both creating and replacing a portfolio item - an update is a full
 * replacement, not a patch, so every field is resent every time.
 * <p>
 * styleTag/priceEstimate are optional - supplying either attaches the item's
 * InteriorDesignDetails satellite; a painter/plumber/electrician/carpenter simply
 * omits both and gets a plain PortfolioItem with no satellite row. On update,
 * omitting both drops an existing satellite, which is what lets an item mistakenly
 * saved as interior-design work be corrected.
 */
public record SavePortfolioItemRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @NotNull Long categoryId,
        @Size(max = 1000) String coverImageUrl,
        @Size(max = 100) String styleTag,
        @DecimalMin("0") BigDecimal priceEstimate
) {
}
