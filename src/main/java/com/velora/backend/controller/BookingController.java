package com.velora.backend.controller;

import com.velora.backend.dto.booking.AssignDesignerRequest;
import com.velora.backend.dto.booking.BookingRequest;
import com.velora.backend.dto.booking.BookingResponse;
import com.velora.backend.dto.booking.BookingStatusUpdateRequest;
import com.velora.backend.dto.booking.DesignerMatchResponse;
import com.velora.backend.security.UserPrincipal;
import com.velora.backend.service.BookingService;
import com.velora.backend.service.DesignerMatchingService;
import com.velora.backend.util.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Consultation booking lifecycle")
public class BookingController {

    private static final int MAX_PAGE_SIZE = 50;

    private final BookingService bookingService;
    private final DesignerMatchingService designerMatchingService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Book a consultation with a designer")
    public ResponseEntity<BookingResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                                   @Valid @RequestBody BookingRequest request) {
        BookingResponse response = bookingService.createBooking(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List bookings for the current user (customer sees own, designer sees assigned)")
    public ResponseEntity<PageResponse<BookingResponse>> getBookings(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "scheduledAt"));

        return ResponseEntity.ok(bookingService.getBookingsForUser(principal.getId(), principal.getRole(), pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single booking's details")
    public ResponseEntity<BookingResponse> getById(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getById(principal.getId(), id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Cancel a booking (customer only, before completion)")
    public ResponseEntity<BookingResponse> cancel(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancel(principal.getId(), id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('DESIGNER')")
    @Operation(summary = "Update booking status (designer only)")
    public ResponseEntity<BookingResponse> updateStatus(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable Long id,
                                                          @Valid @RequestBody BookingStatusUpdateRequest request) {
        return ResponseEntity.ok(bookingService.updateStatus(principal.getId(), id, request.status()));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign a designer to a booking awaiting assignment (admin only)")
    public ResponseEntity<BookingResponse> assign(@PathVariable Long id,
                                                   @Valid @RequestBody AssignDesignerRequest request) {
        return ResponseEntity.ok(bookingService.assignDesigner(id, request.designerId()));
    }

    @GetMapping("/{id}/recommendations")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rank candidate designers for a booking awaiting assignment (admin only)")
    public ResponseEntity<List<DesignerMatchResponse>> recommendations(@PathVariable Long id) {
        return ResponseEntity.ok(designerMatchingService.recommend(id));
    }
}
