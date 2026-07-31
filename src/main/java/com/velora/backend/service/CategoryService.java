package com.velora.backend.service;

import com.velora.backend.dto.portfolio.CategoryResponse;
import com.velora.backend.mapper.PortfolioItemMapper;
import com.velora.backend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final PortfolioItemMapper portfolioItemMapper;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(portfolioItemMapper::toCategoryResponse)
                .toList();
    }
}
