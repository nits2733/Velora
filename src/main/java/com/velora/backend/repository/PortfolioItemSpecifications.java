package com.velora.backend.repository;

import com.velora.backend.entity.PortfolioItem;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public final class PortfolioItemSpecifications {

    private PortfolioItemSpecifications() {
    }

    public static Specification<PortfolioItem> hasCategory(Long categoryId) {
        return (root, query, cb) -> categoryId == null
                ? null
                : cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<PortfolioItem> hasStyle(String styleTag) {
        return (root, query, cb) -> (styleTag == null || styleTag.isBlank())
                ? null
                : cb.equal(cb.lower(root.join("interiorDesignDetails", JoinType.LEFT).get("styleTag")), styleTag.toLowerCase());
    }

    public static Specification<PortfolioItem> matchesSearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return null;
            }
            String pattern = "%" + search.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            );
        };
    }
}
