package com.velora.backend.controller;

import com.velora.backend.dto.portfolio.PortfolioItemResponse;
import com.velora.backend.dto.portfolio.PortfolioItemSummaryResponse;
import com.velora.backend.service.PortfolioItemService;
import com.velora.backend.util.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@Tag(name = "Portfolio", description = "Professional work-sample catalog: browse, search, filter (public)")
public class PortfolioItemController {

    private static final int MAX_PAGE_SIZE = 50;

    private final PortfolioItemService portfolioItemService;

    @GetMapping
    @Operation(summary = "Search/browse the portfolio catalog with pagination and filters")
    public ResponseEntity<PageResponse<PortfolioItemSummaryResponse>> search(
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String style,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field: createdAt, priceEstimate, title")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(sortDirection, sanitizeSortField(sortBy)));

        return ResponseEntity.ok(portfolioItemService.search(category, search, style, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get portfolio item details by id")
    public ResponseEntity<PortfolioItemResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(portfolioItemService.getById(id));
    }

    private String sanitizeSortField(String sortBy) {
        return switch (sortBy) {
            case "priceEstimate" -> "interiorDesignDetails.priceEstimate";
            case "title" -> "title";
            default -> "createdAt";
        };
    }
}
