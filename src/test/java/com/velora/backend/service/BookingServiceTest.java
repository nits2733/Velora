package com.velora.backend.service;

import com.velora.backend.dto.booking.BookingRequest;
import com.velora.backend.dto.booking.BookingResponse;
import com.velora.backend.entity.Booking;
import com.velora.backend.entity.BookingStatus;
import com.velora.backend.entity.Role;
import com.velora.backend.entity.User;
import com.velora.backend.exception.InvalidStateTransitionException;
import com.velora.backend.exception.UnauthorizedActionException;
import com.velora.backend.mapper.BookingMapper;
import com.velora.backend.repository.BookingRepository;
import com.velora.backend.repository.CategoryRepository;
import com.velora.backend.repository.DesignRepository;
import com.velora.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DesignRepository designRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private final BookingMapper bookingMapper = new BookingMapper();

    private BookingService bookingService;

    private User customer;
    private User designer;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, userRepository, designRepository, categoryRepository, bookingMapper);

        customer = User.builder().id(1L).email("customer@velora.test").fullName("Cust").role(Role.CUSTOMER).build();
        designer = User.builder().id(2L).email("designer@velora.test").fullName("Des").role(Role.DESIGNER).build();
    }

    @Test
    void createBookingRejectsNonDesignerTarget() {
        User notDesigner = User.builder().id(3L).fullName("X").role(Role.CUSTOMER).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(userRepository.findById(3L)).thenReturn(Optional.of(notDesigner));

        BookingRequest request = new BookingRequest(3L, null, Instant.now().plus(1, ChronoUnit.DAYS), null, null, null, null, null);

        assertThatThrownBy(() -> bookingService.createBooking(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a designer");
    }

    @Test
    void createBookingSucceedsForValidDesigner() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(designer));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(100L);
            b.setCreatedAt(Instant.now());
            return b;
        });

        BookingRequest request = new BookingRequest(2L, null, Instant.now().plus(1, ChronoUnit.DAYS), "please call ahead", null, null, null, null);

        BookingResponse response = bookingService.createBooking(1L, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.designerId()).isEqualTo(2L);
    }

    @Test
    void cancelRejectsNonOwnerCustomer() {
        Booking booking = Booking.builder()
                .id(5L).customer(customer).designer(designer)
                .status(BookingStatus.PENDING).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(999L, 5L))
                .isInstanceOf(UnauthorizedActionException.class);
    }

    @Test
    void cancelRejectsAlreadyCompletedBooking() {
        Booking booking = Booking.builder()
                .id(6L).customer(customer).designer(designer)
                .status(BookingStatus.COMPLETED).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(6L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(1L, 6L))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void updateStatusRejectsInvalidTransitionFromCancelled() {
        Booking booking = Booking.builder()
                .id(7L).customer(customer).designer(designer)
                .status(BookingStatus.CANCELLED).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.updateStatus(2L, 7L, BookingStatus.CONFIRMED))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void updateStatusAllowsPendingToConfirmed() {
        Booking booking = Booking.builder()
                .id(8L).customer(customer).designer(designer)
                .status(BookingStatus.PENDING).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(8L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.updateStatus(2L, 8L, BookingStatus.CONFIRMED);

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void createBookingWithoutDesignerIdStartsAwaitingAssignment() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(101L);
            b.setCreatedAt(Instant.now());
            return b;
        });

        BookingRequest request = new BookingRequest(null, null, Instant.now().plus(1, ChronoUnit.DAYS), null, null, null, null, null);

        BookingResponse response = bookingService.createBooking(1L, request);

        assertThat(response.status()).isEqualTo(BookingStatus.PENDING_ASSIGNMENT);
        assertThat(response.designerId()).isNull();
    }

    @Test
    void createBookingRejectsDesignIdWithoutDesignerId() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        BookingRequest request = new BookingRequest(null, 5L, Instant.now().plus(1, ChronoUnit.DAYS), null, null, null, null, null);

        assertThatThrownBy(() -> bookingService.createBooking(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("designId requires an explicit designerId");
    }

    @Test
    void assignDesignerSucceedsForValidTarget() {
        Booking booking = Booking.builder()
                .id(9L).customer(customer).designer(null)
                .status(BookingStatus.PENDING_ASSIGNMENT).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(9L)).thenReturn(Optional.of(booking));
        when(userRepository.findById(2L)).thenReturn(Optional.of(designer));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.assignDesigner(9L, 2L);

        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.designerId()).isEqualTo(2L);
    }

    @Test
    void assignDesignerRejectsNonDesignerTarget() {
        Booking booking = Booking.builder()
                .id(10L).customer(customer).designer(null)
                .status(BookingStatus.PENDING_ASSIGNMENT).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> bookingService.assignDesigner(10L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignDesignerRejectsAlreadyAssignedBooking() {
        Booking booking = Booking.builder()
                .id(11L).customer(customer).designer(designer)
                .status(BookingStatus.PENDING).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(11L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.assignDesigner(11L, 2L))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cancelAllowsPendingAssignmentBooking() {
        Booking booking = Booking.builder()
                .id(12L).customer(customer).designer(null)
                .status(BookingStatus.PENDING_ASSIGNMENT).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(12L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.cancel(1L, 12L);

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
    }
}
