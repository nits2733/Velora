package com.velora.backend.service;

import com.velora.backend.dto.portfolio.CreatePortfolioItemRequest;
import com.velora.backend.dto.portfolio.PortfolioItemResponse;
import com.velora.backend.entity.Category;
import com.velora.backend.entity.PortfolioItem;
import com.velora.backend.entity.Role;
import com.velora.backend.entity.User;
import com.velora.backend.exception.ResourceNotFoundException;
import com.velora.backend.mapper.PortfolioItemMapper;
import com.velora.backend.repository.CategoryRepository;
import com.velora.backend.repository.PortfolioItemRepository;
import com.velora.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioItemServiceTest {

    @Mock
    private PortfolioItemRepository portfolioItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private final PortfolioItemMapper portfolioItemMapper = new PortfolioItemMapper();

    private PortfolioItemService portfolioItemService;

    private User professional;
    private Category paintingCategory;

    @BeforeEach
    void setUp() {
        portfolioItemService = new PortfolioItemService(
                portfolioItemRepository, portfolioItemMapper, userRepository, categoryRepository);

        professional = User.builder().id(3L).fullName("Rebrand Plumber").role(Role.PROFESSIONAL).build();
        paintingCategory = Category.builder().id(8L).name("Painting").build();
    }

    @Test
    void createWithoutStyleOrPriceOmitsInteriorDesignDetails() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(professional));
        when(categoryRepository.findById(8L)).thenReturn(Optional.of(paintingCategory));
        when(portfolioItemRepository.save(any(PortfolioItem.class))).thenAnswer(inv -> {
            PortfolioItem item = inv.getArgument(0);
            item.setId(50L);
            return item;
        });

        CreatePortfolioItemRequest request = new CreatePortfolioItemRequest(
                "Before/After Living Room Repaint", "Two-coat repaint", 8L, "https://img/1.jpg", null, null);

        PortfolioItemResponse response = portfolioItemService.create(3L, request);

        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.title()).isEqualTo("Before/After Living Room Repaint");
        assertThat(response.styleTag()).isNull();
        assertThat(response.priceEstimate()).isNull();

        ArgumentCaptor<PortfolioItem> captor = ArgumentCaptor.forClass(PortfolioItem.class);
        verify(portfolioItemRepository).save(captor.capture());
        assertThat(captor.getValue().getInteriorDesignDetails()).isNull();
    }

    @Test
    void createWithStyleAndPriceAttachesInteriorDesignDetails() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(professional));
        when(categoryRepository.findById(8L)).thenReturn(Optional.of(paintingCategory));
        when(portfolioItemRepository.save(any(PortfolioItem.class))).thenAnswer(inv -> {
            PortfolioItem item = inv.getArgument(0);
            item.setId(51L);
            return item;
        });

        CreatePortfolioItemRequest request = new CreatePortfolioItemRequest(
                "Minimalist Living Room Concept", null, 8L, null, "minimalist", new BigDecimal("42000"));

        PortfolioItemResponse response = portfolioItemService.create(3L, request);

        assertThat(response.styleTag()).isEqualTo("minimalist");
        assertThat(response.priceEstimate()).isEqualByComparingTo("42000");
    }

    @Test
    void createRejectsUnknownProfessional() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        CreatePortfolioItemRequest request = new CreatePortfolioItemRequest(
                "Title", null, 8L, null, null, null);

        assertThatThrownBy(() -> portfolioItemService.create(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createRejectsUnknownCategory() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(professional));
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        CreatePortfolioItemRequest request = new CreatePortfolioItemRequest(
                "Title", null, 404L, null, null, null);

        assertThatThrownBy(() -> portfolioItemService.create(3L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
