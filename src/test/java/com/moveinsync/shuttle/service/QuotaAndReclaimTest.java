package com.moveinsync.shuttle.service;

import static org.junit.jupiter.api.Assertions.*;

import com.moveinsync.shuttle.domain.*;
import com.moveinsync.shuttle.exception.ApiException;
import com.moveinsync.shuttle.security.AuthPrincipal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class QuotaAndReclaimTest extends AbstractIntegrationTest {

    @Test
    void freeSeatIsStillBlockedByCostCentreQuota() {
        Trip trip = trip(3);
        tripService.setQuota(trip.getId(), "ENG", 1);
        Booking eng1 = book(trip, user("ENG"), 1);
        book(trip, user("OPS"), 1);
        Booking eng2 = book(trip, user("ENG"), 1); 

        assertEquals(BookingStatus.CONFIRMED, status(eng1));
        assertEquals(BookingStatus.WAITLISTED, status(eng2));
        assertEquals(2, confirmedSeats(trip));

        tripService.setQuota(trip.getId(), "ENG", 2); 
        assertEquals(BookingStatus.CONFIRMED, status(eng2));
    }

    @Test
    void cancelledOpsSeatGoesToOpsNotBlockedEng() {
        Trip trip = trip(2);
        tripService.setQuota(trip.getId(), "ENG", 1);
        book(trip, user("ENG"), 1);
        Booking ops = book(trip, user("OPS"), 1);
        Booking eng2 = book(trip, user("ENG"), 1);  
        Booking ops2 = book(trip, user("OPS"), 1);  

        bookingService.cancel(ops.getId(), new AuthPrincipal(0L, "admin", Role.ADMIN));
        assertEquals(BookingStatus.WAITLISTED, status(eng2), "ENG quota exhausted, FIFO head must be skipped");
        assertEquals(BookingStatus.CONFIRMED, status(ops2));
    }

    @Test
    void duplicateActiveBookingIsRejected() {
        Trip trip = trip(2);
        AppUser u = user("ENG");
        book(trip, u, 1);
        ApiException ex = assertThrows(ApiException.class, () -> book(trip, u, 1));
        assertEquals("DUPLICATE_BOOKING", ex.getCode());
    }

    @Test
    void requestLargerThanVehicleIsRejected() {
        Trip trip = trip(2);
        ApiException ex = assertThrows(ApiException.class, () -> book(trip, user("ENG"), 3));
        assertEquals("EXCEEDS_VEHICLE_CAPACITY", ex.getCode());
    }

    @Test
    void noShowSeatIsReclaimedAndNextWaitlistedPromotedAfterCutoff() {
        Trip trip = trip(2);
        Booking checkedIn = book(trip, user("ENG"), 1);
        Booking noShow = book(trip, user("ENG"), 1);
        Booking waiting = book(trip, user("ENG"), 1);
        AuthPrincipal owner = new AuthPrincipal(checkedIn.getUserId(), "x", Role.EMPLOYEE);
        bookingService.checkIn(checkedIn.getId(), owner);

        ApiException early = assertThrows(ApiException.class, () -> bookingService.reclaimNoShows(trip.getId()));
        assertEquals("CUTOFF_NOT_REACHED", early.getCode());

        clock.advance(Duration.ofHours(2).plusMinutes(1)); // past cut-off, before departure
        var result = bookingService.reclaimNoShows(trip.getId());

        assertEquals(1, result.reclaimedBookings());
        assertEquals(BookingStatus.CANCELLED, status(noShow));
        assertEquals(CancelReason.NO_SHOW, bookingRepo.findById(noShow.getId()).orElseThrow().getCancelReason());
        Booking promoted = bookingRepo.findById(waiting.getId()).orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, promoted.getStatus());
        assertTrue(promoted.isCheckedIn(), "late promotion is auto checked-in so it is not reclaimed again");
        assertEquals(2, confirmedSeats(trip));

        assertEquals(0, bookingService.reclaimNoShows(trip.getId()).reclaimedBookings()); // idempotent
    }

    @Test
    void checkInAfterCutoffIsRejected() {
        Trip trip = trip(1);
        Booking b = book(trip, user("ENG"), 1);
        clock.advance(Duration.ofHours(2).plusMinutes(1));
        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.checkIn(b.getId(), new AuthPrincipal(b.getUserId(), "x", Role.EMPLOYEE)));
        assertEquals("CHECK_IN_CLOSED", ex.getCode());
    }

    @Test
    void employeeCannotCancelSomeoneElsesBooking() {
        Trip trip = trip(2);
        Booking b = book(trip, user("ENG"), 1);
        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.cancel(b.getId(), new AuthPrincipal(999999L, "mallory", Role.EMPLOYEE)));
        assertEquals("FORBIDDEN", ex.getCode());
    }
}
