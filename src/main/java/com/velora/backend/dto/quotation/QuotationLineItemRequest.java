package com.velora.backend.dto.quotation;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record QuotationLineItemRequest(
        @NotBlank @Size(max = 300) String description,
        @DecimalMin("0") BigDecimal quantity,
        @Size(max = 30) String unit,
        @DecimalMin("0") BigDecimal unitPrice,
        @NotNull @DecimalMin("0") BigDecimal amount
) {
}
