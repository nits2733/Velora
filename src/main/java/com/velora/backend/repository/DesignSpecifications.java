package com.velora.backend.repository;

import com.velora.backend.entity.Design;
import org.springframework.data.jpa.domain.Specification;

public final class DesignSpecifications {

    private DesignSpecifications() {
    }

    public static Specification<Design> hasCategory(Long categoryId) {
        return (root, query, cb) -> categoryId == null
                ? null
                : cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Design> hasStyle(String styleTag) {
        return (root, query, cb) -> (styleTag == null || styleTag.isBlank())
                ? null
                : cb.equal(cb.lower(root.get("styleTag")), styleTag.toLowerCase());
    }

    public static Specification<Design> matchesSearch(String search) {
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
