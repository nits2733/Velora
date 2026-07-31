package com.velora.backend.mapper;

import com.velora.backend.dto.review.ReviewResponse;
import com.velora.backend.entity.Review;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

    public ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getBooking().getId(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
