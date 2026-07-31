package com.velora.backend.mapper;

import com.velora.backend.dto.booking.BookingResponse;
import com.velora.backend.entity.Booking;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {

    public BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getCustomer().getId(),
                booking.getCustomer().getFullName(),
                booking.getDesigner() != null ? booking.getDesigner().getId() : null,
                booking.getDesigner() != null ? booking.getDesigner().getFullName() : null,
                booking.getDesign() != null ? booking.getDesign().getId() : null,
                booking.getDesign() != null ? booking.getDesign().getTitle() : null,
                booking.getScheduledAt(),
                booking.getStatus(),
                booking.getNotes(),
                booking.getCreatedAt()
        );
    }
}
