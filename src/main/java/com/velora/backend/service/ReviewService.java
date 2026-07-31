package com.velora.backend.service;

import com.velora.backend.dto.review.CreateReviewRequest;
import com.velora.backend.dto.review.ReviewResponse;
import com.velora.backend.entity.Booking;
import com.velora.backend.entity.BookingStatus;
import com.velora.backend.entity.DesignerProfile;
import com.velora.backend.entity.Review;
import com.velora.backend.exception.DuplicateResourceException;
import com.velora.backend.exception.InvalidStateTransitionException;
import com.velora.backend.exception.ResourceNotFoundException;
import com.velora.backend.exception.UnauthorizedActionException;
import com.velora.backend.mapper.ReviewMapper;
import com.velora.backend.repository.BookingRepository;
import com.velora.backend.repository.DesignerProfileRepository;
import com.velora.backend.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final DesignerProfileRepository designerProfileRepository;
    private final ReviewMapper reviewMapper;

    @Transactional
    public ReviewResponse createReview(Long customerId, Long bookingId, CreateReviewRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (!booking.getCustomer().getId().equals(customerId)) {
            throw new UnauthorizedActionException("Only the customer on this booking can leave a review");
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new InvalidStateTransitionException("Can only review a completed booking");
        }
        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new DuplicateResourceException("This booking has already been reviewed");
        }

        Review review = Review.builder()
                .booking(booking)
                .customer(booking.getCustomer())
                .designer(booking.getDesigner())
                .rating(request.rating())
                .comment(request.comment())
                .build();

        review = reviewRepository.save(review);

        recomputeDesignerRating(booking.getDesigner().getId());

        return reviewMapper.toResponse(review);
    }

    private void recomputeDesignerRating(Long designerId) {
        List<Review> reviews = reviewRepository.findByDesignerId(designerId);

        DesignerProfile profile = designerProfileRepository.findByUserId(designerId)
                .orElseThrow(() -> new ResourceNotFoundException("Designer profile not found: " + designerId));

        BigDecimal total = reviews.stream()
                .map(r -> BigDecimal.valueOf(r.getRating()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal average = total.divide(new BigDecimal(reviews.size()), 2, RoundingMode.HALF_UP);

        profile.setAverageRating(average);
        profile.setRatingCount(reviews.size());
        designerProfileRepository.save(profile);
    }
}
