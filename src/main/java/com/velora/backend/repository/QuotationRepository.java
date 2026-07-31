package com.velora.backend.repository;

import com.velora.backend.entity.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    Optional<Quotation> findByBookingId(Long bookingId);
}
