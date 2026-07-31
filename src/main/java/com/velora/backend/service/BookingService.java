package com.velora.backend.service;

import com.velora.backend.dto.booking.BookingRequest;
import com.velora.backend.dto.booking.BookingResponse;
import com.velora.backend.entity.Booking;
import com.velora.backend.entity.BookingStatus;
import com.velora.backend.entity.Design;
import com.velora.backend.entity.Role;
import com.velora.backend.entity.User;
import com.velora.backend.exception.InvalidStateTransitionException;
import com.velora.backend.exception.ResourceNotFoundException;
import com.velora.backend.exception.UnauthorizedActionException;
import com.velora.backend.mapper.BookingMapper;
import com.velora.backend.repository.BookingRepository;
import com.velora.backend.repository.DesignRepository;
import com.velora.backend.repository.UserRepository;
import com.velora.backend.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final Set<BookingStatus> CANCELLABLE_STATUSES = Set.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final DesignRepository designRepository;
    private final BookingMapper bookingMapper;

    @Transactional
    public BookingResponse createBooking(Long customerId, BookingRequest request) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + customerId));

        User designer = userRepository.findById(request.designerId())
                .orElseThrow(() -> new ResourceNotFoundException("Designer not found: " + request.designerId()));

        if (designer.getRole() != Role.DESIGNER) {
            throw new IllegalArgumentException("Selected user is not a designer");
        }

        Design design = null;
        if (request.designId() != null) {
            design = designRepository.findById(request.designId())
                    .orElseThrow(() -> new ResourceNotFoundException("Design not found: " + request.designId()));

            if (!design.getDesigner().getId().equals(designer.getId())) {
                throw new IllegalArgumentException("The selected design does not belong to the selected designer");
            }
        }

        Booking booking = Booking.builder()
                .customer(customer)
                .designer(designer)
                .design(design)
                .scheduledAt(request.scheduledAt())
                .status(BookingStatus.PENDING)
                .notes(request.notes())
                .build();

        return bookingMapper.toResponse(bookingRepository.save(booking));
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getBookingsForUser(Long userId, Role role, Pageable pageable) {
        Page<Booking> bookings = role == Role.DESIGNER
                ? bookingRepository.findByDesignerId(userId, pageable)
                : bookingRepository.findByCustomerId(userId, pageable);

        return PageResponse.from(bookings.map(bookingMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public BookingResponse getById(Long userId, Long bookingId) {
        Booking booking = findBooking(bookingId);
        assertParticipant(userId, booking);
        return bookingMapper.toResponse(booking);
    }

    @Transactional
    public BookingResponse cancel(Long customerId, Long bookingId) {
        Booking booking = findBooking(bookingId);

        if (!booking.getCustomer().getId().equals(customerId)) {
            throw new UnauthorizedActionException("Only the customer who made this booking can cancel it");
        }

        if (!CANCELLABLE_STATUSES.contains(booking.getStatus())) {
            throw new InvalidStateTransitionException(
                    "Cannot cancel a booking with status " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        return bookingMapper.toResponse(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse updateStatus(Long designerId, Long bookingId, BookingStatus newStatus) {
        Booking booking = findBooking(bookingId);

        if (!booking.getDesigner().getId().equals(designerId)) {
            throw new UnauthorizedActionException("Only the assigned designer can update this booking's status");
        }

        validateTransition(booking.getStatus(), newStatus);

        booking.setStatus(newStatus);
        return bookingMapper.toResponse(bookingRepository.save(booking));
    }

    private void validateTransition(BookingStatus current, BookingStatus next) {
        boolean allowed = switch (current) {
            case PENDING -> next == BookingStatus.CONFIRMED || next == BookingStatus.CANCELLED;
            case CONFIRMED -> next == BookingStatus.COMPLETED || next == BookingStatus.CANCELLED;
            case CANCELLED, COMPLETED -> false;
        };

        if (!allowed) {
            throw new InvalidStateTransitionException(
                    "Cannot transition booking from " + current + " to " + next);
        }
    }

    private void assertParticipant(Long userId, Booking booking) {
        boolean isParticipant = booking.getCustomer().getId().equals(userId)
                || booking.getDesigner().getId().equals(userId);
        if (!isParticipant) {
            throw new UnauthorizedActionException("You do not have access to this booking");
        }
    }

    private Booking findBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
    }
}
