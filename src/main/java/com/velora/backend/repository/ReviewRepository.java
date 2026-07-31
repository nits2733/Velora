package com.velora.backend.repository;

import com.velora.backend.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByBookingId(Long bookingId);

    List<Review> findByDesignerId(Long designerId);
}
