package com.velora.backend.service;

import com.velora.backend.dto.booking.DesignerMatchResponse;
import com.velora.backend.entity.AvailabilityStatus;
import com.velora.backend.entity.Booking;
import com.velora.backend.entity.BookingStatus;
import com.velora.backend.entity.Category;
import com.velora.backend.entity.DesignerProfile;
import com.velora.backend.entity.Role;
import com.velora.backend.entity.User;
import com.velora.backend.exception.InvalidStateTransitionException;
import com.velora.backend.repository.BookingRepository;
import com.velora.backend.repository.DesignRepository;
import com.velora.backend.repository.DesignerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesignerMatchingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private DesignerProfileRepository designerProfileRepository;
    @Mock
    private DesignRepository designRepository;

    private DesignerMatchingService matchingService;

    private User customer;
    private Category kitchenCategory;

    @BeforeEach
    void setUp() {
        matchingService = new DesignerMatchingService(bookingRepository, designerProfileRepository, designRepository);

        customer = User.builder().id(1L).fullName("Cust").role(Role.CUSTOMER).build();
        kitchenCategory = Category.builder().id(3L).name("Kitchen").build();
    }

    @Test
    void recommendRejectsBookingNotAwaitingAssignment() {
        Booking booking = Booking.builder()
                .id(50L).customer(customer).designer(null)
                .status(BookingStatus.PENDING).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(50L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> matchingService.recommend(50L))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void recommendRanksMatchingDesignerAboveNonMatchingOne() {
        Booking booking = Booking.builder()
                .id(51L).customer(customer).designer(null)
                .status(BookingStatus.PENDING_ASSIGNMENT).scheduledAt(Instant.now())
                .category(kitchenCategory).location("Mumbai").build();
        when(bookingRepository.findById(51L)).thenReturn(Optional.of(booking));

        User matchingUser = User.builder().id(10L).fullName("Kitchen Expert").role(Role.DESIGNER).build();
        DesignerProfile matchingProfile = DesignerProfile.builder()
                .id(100L).user(matchingUser).specialization("Kitchen Renovations")
                .city("Mumbai").yearsExperience(8)
                .availabilityStatus(AvailabilityStatus.AVAILABLE).build();

        User nonMatchingUser = User.builder().id(11L).fullName("Bedroom Only").role(Role.DESIGNER).build();
        DesignerProfile nonMatchingProfile = DesignerProfile.builder()
                .id(101L).user(nonMatchingUser).specialization("Bedroom Design")
                .city("Delhi").yearsExperience(2)
                .availabilityStatus(AvailabilityStatus.AVAILABLE).build();

        when(designerProfileRepository.findByAvailabilityStatus(AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(nonMatchingProfile, matchingProfile));
        when(designRepository.countByDesignerIdAndCategoryId(anyLong(), any())).thenReturn(0L);

        List<DesignerMatchResponse> results = matchingService.recommend(51L);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).designerId()).isEqualTo(10L);
        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
    }

    @Test
    void recommendGivesUnratedDesignerNeutralScoreNotZero() {
        Booking booking = Booking.builder()
                .id(52L).customer(customer).designer(null)
                .status(BookingStatus.PENDING_ASSIGNMENT).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(52L)).thenReturn(Optional.of(booking));

        User unratedUser = User.builder().id(12L).fullName("New Designer").role(Role.DESIGNER).build();
        DesignerProfile unratedProfile = DesignerProfile.builder()
                .id(102L).user(unratedUser).availabilityStatus(AvailabilityStatus.AVAILABLE)
                .averageRating(null).ratingCount(0).build();

        when(designerProfileRepository.findByAvailabilityStatus(AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(unratedProfile));

        List<DesignerMatchResponse> results = matchingService.recommend(52L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).score()).isGreaterThan(0);
    }

    @Test
    void recommendBreaksTiesInFavorOfLessBusyDesigner() {
        Booking booking = Booking.builder()
                .id(53L).customer(customer).designer(null)
                .status(BookingStatus.PENDING_ASSIGNMENT).scheduledAt(Instant.now()).build();
        when(bookingRepository.findById(53L)).thenReturn(Optional.of(booking));

        User busyUser = User.builder().id(13L).fullName("Busy Designer").role(Role.DESIGNER).build();
        DesignerProfile busyProfile = DesignerProfile.builder()
                .id(103L).user(busyUser).availabilityStatus(AvailabilityStatus.AVAILABLE).build();

        User freeUser = User.builder().id(14L).fullName("Free Designer").role(Role.DESIGNER).build();
        DesignerProfile freeProfile = DesignerProfile.builder()
                .id(104L).user(freeUser).availabilityStatus(AvailabilityStatus.AVAILABLE).build();

        when(designerProfileRepository.findByAvailabilityStatus(AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(busyProfile, freeProfile));
        when(bookingRepository.countByDesignerIdAndStatusIn(eq(13L), any())).thenReturn(3L);
        when(bookingRepository.countByDesignerIdAndStatusIn(eq(14L), any())).thenReturn(0L);

        List<DesignerMatchResponse> results = matchingService.recommend(53L);

        assertThat(results.get(0).designerId()).isEqualTo(14L);
    }
}
