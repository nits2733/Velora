package com.velora.backend.dto.quotation;

import java.math.BigDecimal;

public record QuotationLineItemResponse(
        Long id,
        String description,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal amount
) {
}
