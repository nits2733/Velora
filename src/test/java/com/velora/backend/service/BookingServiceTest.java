package com.velora.backend.service;

import com.velora.backend.dto.booking.BookingRequest;
import com.velora.backend.dto.booking.BookingResponse;
import com.velora.backend.entity.Booking;
import com.velora.backend.entity.BookingStatus;
import com.velora.backend.entity.Category;
import com.velora.backend.entity.RequestType;
import com.velora.backend.entity.Role;
import com.velora.backend.entity.ServiceGroup;
import com.velora.backend.entity.User;
import com.velora.backend.exception.InvalidStateTransitionException;
import com.velora.backend.exception.UnauthorizedActionException;
import com.velora.backend.mapper.BookingMapper;
import com.velora.backend.repository.BookingRepository;
import com.velora.backend.repository.CategoryRepository;
import com.velora.backend.repository.PortfolioItemRepository;
import com.velora.backend.repository.UserRepository;
import com.velora.backend.util.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
    private PortfolioItemRepository portfolioItemRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private final BookingMapper bookingMapper = new BookingMapper();

    private BookingService bookingService;

    private User customer;
    private User professional;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, userRepository, portfolioItemRepository, categoryRepository, bookingMapper);

        customer = User.builder().id(1L).email("customer@velora.test").fullName("Cust").role(Role.CUSTOMER).build();
        professional = User.builder().id(2L).email("professional@velora.test").fullName("Pro").role(Role.PROFESSIONAL).build();
    }

    @Test
    void createBookingRejectsNonProfessionalTarget() {
        User notProfessional = User.builder().id(3L).fullName("X").role(Role.CUSTOMER).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(userRepository.findById(3L)).thenReturn(Optional.of(notProfessional));

        BookingRequest request = new BookingRequest(RequestType.FULL_HOME_PROJECT, 3L, null,
                Instant.now().plus(1, ChronoUnit.DAYS), null, null, null, null, null);

        assertThatThrownBy(() -> bookingService.createBooking(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a professional");
    }

    @Test
    void createBookingSucceedsForValidProfessional() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(professional));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(100L);
            b.setCreatedAt(Instant.now());
            return b;
        });

        BookingRequest request = new BookingRequest(RequestType.FULL_HOME_PROJECT, 2L, null,
                Instant.now().plus(1, ChronoUnit.DAYS), "please call ahead", null, null, null, null);

        BookingResponse response = bookingService.createBooking(1L, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.professionalId()).isEqualTo(2L);
    }

    @Test
    void cancelRejectsNonOwnerCustomer() {
        Booking booking = Booking.builder()
                .id(5L).customer(customer).professional(professional).requestType(RequestType.FULL_HOME_PROJECT)
                .status(BookingStatus.PENDING).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(999L, 5L))
                .isInstanceOf(UnauthorizedActionException.class);
    }

    @Test
    void cancelRejectsAlreadyCompletedBooking() {
        Booking booking = Booking.builder()
                .id(6L).customer(customer).professional(professional).requestType(RequestType.FULL_HOME_PROJECT)
                .status(BookingStatus.COMPLETED).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(6L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(1L, 6L))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void updateStatusRejectsInvalidTransitionFromCancelled() {
        Booking booking = Booking.builder()
                .id(7L).customer(customer).professional(professional).requestType(RequestType.FULL_HOME_PROJECT)
                .status(BookingStatus.CANCELLED).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.updateStatus(2L, 7L, BookingStatus.CONFIRMED))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void updateStatusAllowsPendingToConfirmed() {
        Booking booking = Booking.builder()
                .id(8L).customer(customer).professional(professional).requestType(RequestType.FULL_HOME_PROJECT)
                .status(BookingStatus.PENDING).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(8L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.updateStatus(2L, 8L, BookingStatus.CONFIRMED);

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void createBookingWithoutProfessionalIdStartsAwaitingAssignment() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(101L);
            b.setCreatedAt(Instant.now());
            return b;
        });

        BookingRequest request = new BookingRequest(RequestType.FULL_HOME_PROJECT, null, null,
                Instant.now().plus(1, ChronoUnit.DAYS), null, null, null, null, null);

        BookingResponse response = bookingService.createBooking(1L, request);

        assertThat(response.status()).isEqualTo(BookingStatus.PENDING_ASSIGNMENT);
        assertThat(response.professionalId()).isNull();
    }

    @Test
    void createBookingRejectsPortfolioItemIdWithoutProfessionalId() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        BookingRequest request = new BookingRequest(RequestType.FULL_HOME_PROJECT, null, 5L,
                Instant.now().plus(1, ChronoUnit.DAYS), null, null, null, null, null);

        assertThatThrownBy(() -> bookingService.createBooking(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("portfolioItemId requires an explicit professionalId");
    }

    @Test
    void assignProfessionalSucceedsForValidTarget() {
        Booking booking = Booking.builder()
                .id(9L).customer(customer).professional(null).requestType(RequestType.FULL_HOME_PROJECT)
                .status(BookingStatus.PENDING_ASSIGNMENT).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(9L)).thenReturn(Optional.of(booking));
        when(userRepository.findById(2L)).thenReturn(Optional.of(professional));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.assignProfessional(9L, 2L);

        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.professionalId()).isEqualTo(2L);
    }

    @Test
    void assignProfessionalRejectsNonProfessionalTarget() {
        Booking booking = Booking.builder()
                .id(10L).customer(customer).professional(null).requestType(RequestType.FULL_HOME_PROJECT)
                .status(BookingStatus.PENDING_ASSIGNMENT).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> bookingService.assignProfessional(10L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignProfessionalRejectsAlreadyAssignedBooking() {
        Booking booking = Booking.builder()
                .id(11L).customer(customer).professional(professional).requestType(RequestType.FULL_HOME_PROJECT)
                .status(BookingStatus.PENDING).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(11L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.assignProfessional(11L, 2L))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cancelAllowsPendingAssignmentBooking() {
        Booking booking = Booking.builder()
                .id(12L).customer(customer).professional(null).requestType(RequestType.FULL_HOME_PROJECT)
                .status(BookingStatus.PENDING_ASSIGNMENT).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(12L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.cancel(1L, 12L);

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void createBookingRejectsIndividualServiceWithExplicitProfessionalId() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        BookingRequest request = new BookingRequest(RequestType.INDIVIDUAL_SERVICE, 2L, null,
                Instant.now().plus(1, ChronoUnit.DAYS), null, 3L, null, null, null);

        assertThatThrownBy(() -> bookingService.createBooking(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assigned by Velora");
    }

    @Test
    void createBookingRejectsIndividualServiceWithoutCategory() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        BookingRequest request = new BookingRequest(RequestType.INDIVIDUAL_SERVICE, null, null,
                Instant.now().plus(1, ChronoUnit.DAYS), null, null, null, null, null);

        assertThatThrownBy(() -> bookingService.createBooking(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must specify a category");
    }

    @Test
    void createBookingRejectsMismatchedCategoryServiceGroup() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        Category homeCategory = Category.builder().id(4L).name("Kitchen").serviceGroup(ServiceGroup.HOME_PROJECT).build();
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(homeCategory));

        BookingRequest request = new BookingRequest(RequestType.INDIVIDUAL_SERVICE, null, null,
                Instant.now().plus(1, ChronoUnit.DAYS), null, 4L, null, null, null);

        assertThatThrownBy(() -> bookingService.createBooking(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match the request type");
    }

    @Test
    void createBookingAcceptsValidIndividualServiceRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        Category tradeCategory = Category.builder().id(5L).name("Plumbing").serviceGroup(ServiceGroup.INDIVIDUAL_SERVICE).build();
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(tradeCategory));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(102L);
            b.setCreatedAt(Instant.now());
            return b;
        });

        BookingRequest request = new BookingRequest(RequestType.INDIVIDUAL_SERVICE, null, null,
                Instant.now().plus(1, ChronoUnit.DAYS), "leaking tap", 5L, null, null, "Mumbai");

        BookingResponse response = bookingService.createBooking(1L, request);

        assertThat(response.status()).isEqualTo(BookingStatus.PENDING_ASSIGNMENT);
        assertThat(response.requestType()).isEqualTo(RequestType.INDIVIDUAL_SERVICE);
    }

    @Test
    void awaitingAssignmentQueueReturnsOnlyPendingAssignmentBookings() {
        Pageable pageable = PageRequest.of(0, 20);
        Booking waiting = Booking.builder()
                .id(200L)
                .customer(customer)
                .requestType(RequestType.INDIVIDUAL_SERVICE)
                .status(BookingStatus.PENDING_ASSIGNMENT)
                .scheduledAt(Instant.now().plus(2, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .build();
        when(bookingRepository.findByStatus(BookingStatus.PENDING_ASSIGNMENT, pageable))
                .thenReturn(new PageImpl<>(List.of(waiting), pageable, 1));

        PageResponse<BookingResponse> page = bookingService.getBookingsAwaitingAssignment(pageable);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).singleElement()
                .satisfies(booking -> {
                    assertThat(booking.id()).isEqualTo(200L);
                    assertThat(booking.status()).isEqualTo(BookingStatus.PENDING_ASSIGNMENT);
                    assertThat(booking.professionalId()).isNull();
                });
    }

    @Test
    void awaitingAssignmentQueueIsEmptyWhenNothingIsWaiting() {
        Pageable pageable = PageRequest.of(0, 20);
        when(bookingRepository.findByStatus(BookingStatus.PENDING_ASSIGNMENT, pageable))
                .thenReturn(Page.empty(pageable));

        PageResponse<BookingResponse> page = bookingService.getBookingsAwaitingAssignment(pageable);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }
}
