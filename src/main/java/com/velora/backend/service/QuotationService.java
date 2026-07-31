package com.velora.backend.service;

import com.velora.backend.dto.quotation.QuotationLineItemRequest;
import com.velora.backend.dto.quotation.QuotationResponse;
import com.velora.backend.dto.quotation.SaveQuotationRequest;
import com.velora.backend.entity.Booking;
import com.velora.backend.entity.Quotation;
import com.velora.backend.entity.QuotationLineItem;
import com.velora.backend.entity.QuotationStatus;
import com.velora.backend.exception.InvalidStateTransitionException;
import com.velora.backend.exception.ResourceNotFoundException;
import com.velora.backend.exception.UnauthorizedActionException;
import com.velora.backend.mapper.QuotationMapper;
import com.velora.backend.repository.BookingRepository;
import com.velora.backend.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuotationService {

    private final QuotationRepository quotationRepository;
    private final BookingRepository bookingRepository;
    private final QuotationMapper quotationMapper;

    @Transactional
    public QuotationResponse saveDraft(Long designerId, Long bookingId, SaveQuotationRequest request) {
        Booking booking = findBooking(bookingId);
        assertIsAssignedDesigner(designerId, booking);

        Quotation quotation = quotationRepository.findByBookingId(bookingId)
                .orElseGet(() -> Quotation.builder()
                        .booking(booking)
                        .status(QuotationStatus.DRAFT)
                        .totalAmount(BigDecimal.ZERO)
                        .build());

        if (quotation.getId() != null && quotation.getStatus() != QuotationStatus.DRAFT) {
            throw new InvalidStateTransitionException(
                    "Cannot edit a quotation once it has been sent, accepted, or rejected");
        }

        quotation.setNotes(request.notes());
        replaceLineItems(quotation, request.lineItems());

        return quotationMapper.toResponse(quotationRepository.save(quotation));
    }

    @Transactional
    public QuotationResponse send(Long designerId, Long bookingId) {
        Quotation quotation = findQuotationByBooking(bookingId);
        assertIsAssignedDesigner(designerId, quotation.getBooking());

        if (quotation.getStatus() != QuotationStatus.DRAFT) {
            throw new InvalidStateTransitionException("Only a draft quotation can be sent");
        }
        if (quotation.getLineItems().isEmpty()) {
            throw new IllegalArgumentException("Cannot send a quotation with no line items");
        }

        quotation.setStatus(QuotationStatus.SENT);
        return quotationMapper.toResponse(quotationRepository.save(quotation));
    }

    @Transactional
    public QuotationResponse accept(Long customerId, Long bookingId) {
        return respond(customerId, bookingId, QuotationStatus.ACCEPTED);
    }

    @Transactional
    public QuotationResponse reject(Long customerId, Long bookingId) {
        return respond(customerId, bookingId, QuotationStatus.REJECTED);
    }

    @Transactional(readOnly = true)
    public QuotationResponse get(Long userId, Long bookingId) {
        Quotation quotation = findQuotationByBooking(bookingId);
        assertParticipant(userId, quotation.getBooking());
        return quotationMapper.toResponse(quotation);
    }

    private QuotationResponse respond(Long customerId, Long bookingId, QuotationStatus newStatus) {
        Quotation quotation = findQuotationByBooking(bookingId);

        if (!quotation.getBooking().getCustomer().getId().equals(customerId)) {
            throw new UnauthorizedActionException("Only the customer on this booking can respond to its quotation");
        }
        if (quotation.getStatus() != QuotationStatus.SENT) {
            throw new InvalidStateTransitionException("Only a sent quotation can be accepted or rejected");
        }

        quotation.setStatus(newStatus);
        return quotationMapper.toResponse(quotationRepository.save(quotation));
    }

    private void replaceLineItems(Quotation quotation, List<QuotationLineItemRequest> lineItemRequests) {
        quotation.getLineItems().clear();

        BigDecimal total = BigDecimal.ZERO;
        int sortOrder = 0;
        for (QuotationLineItemRequest itemRequest : lineItemRequests) {
            QuotationLineItem lineItem = QuotationLineItem.builder()
                    .quotation(quotation)
                    .description(itemRequest.description())
                    .quantity(itemRequest.quantity())
                    .unit(itemRequest.unit())
                    .unitPrice(itemRequest.unitPrice())
                    .amount(itemRequest.amount())
                    .sortOrder(sortOrder++)
                    .build();
            quotation.getLineItems().add(lineItem);
            total = total.add(itemRequest.amount());
        }
        quotation.setTotalAmount(total);
    }

    private void assertIsAssignedDesigner(Long designerId, Booking booking) {
        if (booking.getDesigner() == null) {
            throw new UnauthorizedActionException("This booking has no assigned designer yet");
        }
        if (!booking.getDesigner().getId().equals(designerId)) {
            throw new UnauthorizedActionException("Only the designer assigned to this booking can manage its quotation");
        }
    }

    private void assertParticipant(Long userId, Booking booking) {
        boolean isParticipant = booking.getCustomer().getId().equals(userId)
                || (booking.getDesigner() != null && booking.getDesigner().getId().equals(userId));
        if (!isParticipant) {
            throw new UnauthorizedActionException("You do not have access to this quotation");
        }
    }

    private Booking findBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
    }

    private Quotation findQuotationByBooking(Long bookingId) {
        return quotationRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("No quotation found for booking: " + bookingId));
    }
}
