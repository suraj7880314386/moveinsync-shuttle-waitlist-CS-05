package com.moveinsync.shuttle.service;

import static org.junit.jupiter.api.Assertions.*;

import com.moveinsync.shuttle.domain.*;
import com.moveinsync.shuttle.security.AuthPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class SeatConcurrencyTest extends AbstractIntegrationTest {

    private static void runConcurrently(List<Callable<Object>> jobs) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(jobs.size());
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> job : jobs) {
            futures.add(pool.submit(() -> { gate.await(); return job.call(); }));
        }
        gate.countDown();
        for (Future<Object> f : futures) {
            f.get(60, TimeUnit.SECONDS); 
        }
        pool.shutdown();
    }

    @Test
    void twentyConcurrentRequestsForTheLastSeatNeverOverbook() throws Exception {
        Trip trip = trip(5);
        for (int i = 0; i < 4; i++) book(trip, user("ENG"), 1);
        List<AppUser> contenders = new ArrayList<>();
        for (int i = 0; i < 20; i++) contenders.add(user("ENG"));

        List<Callable<Object>> jobs = new ArrayList<>();
        for (AppUser u : contenders) jobs.add(() -> bookingService.requestSeats(trip.getId(), u.getId(), 1, null));
        runConcurrently(jobs);

        assertEquals(5, confirmedSeats(trip), "exactly the vehicle capacity, never more");
        assertEquals(5, byStatus(trip, BookingStatus.CONFIRMED).size());
        assertEquals(19, byStatus(trip, BookingStatus.WAITLISTED).size());
    }

    @Test
    void twoSimultaneousCancellationsCauseExactlyTwoPromotionsInFairOrder() throws Exception {
        Trip trip = trip(2);
        Booking c1 = book(trip, user("ENG"), 1);
        Booking c2 = book(trip, user("ENG"), 1);
        List<Booking> waiting = new ArrayList<>();
        for (int i = 0; i < 4; i++) waiting.add(book(trip, user("ENG"), 1));

        List<Callable<Object>> jobs = new ArrayList<>();
        for (Booking c : List.of(c1, c2)) {
            AuthPrincipal admin = new AuthPrincipal(0L, "admin", Role.ADMIN);
            jobs.add(() -> bookingService.cancel(c.getId(), admin));
        }
        runConcurrently(jobs);

        assertEquals(2, confirmedSeats(trip));
        assertEquals(BookingStatus.CONFIRMED, status(waiting.get(0)));
        assertEquals(BookingStatus.CONFIRMED, status(waiting.get(1)));
        assertEquals(BookingStatus.WAITLISTED, status(waiting.get(2)));
        assertEquals(BookingStatus.WAITLISTED, status(waiting.get(3)));
    }

    @Test
    void groupIsNeverSplitUnderConcurrentCancellations() throws Exception {
        Trip trip = trip(5);
        List<Booking> seated = new ArrayList<>();
        for (int i = 0; i < 5; i++) seated.add(book(trip, user("ENG"), 1));
        Booking group = book(trip, user("ENG"), 3);
        List<Booking> singles = new ArrayList<>();
        for (int i = 0; i < 5; i++) singles.add(book(trip, user("ENG"), 1));

        List<Callable<Object>> jobs = new ArrayList<>();
        AuthPrincipal admin = new AuthPrincipal(0L, "admin", Role.ADMIN);
        for (Booking s : seated) jobs.add(() -> bookingService.cancel(s.getId(), admin));
        runConcurrently(jobs);

        Booking g = bookingRepo.findById(group.getId()).orElseThrow();
        assertEquals(BookingStatus.WAITLISTED, g.getStatus(), "group must be waitlisted as a whole (0 of 3 seats)");
        assertTrue(confirmedSeats(trip) <= 5);
        assertEquals(3, g.getSkipCount());
        assertEquals(3, confirmedSeats(trip));
        assertEquals(BookingStatus.CONFIRMED, status(singles.get(0)));
        assertEquals(BookingStatus.CONFIRMED, status(singles.get(1)));
        assertEquals(BookingStatus.CONFIRMED, status(singles.get(2)));
        assertEquals(BookingStatus.WAITLISTED, status(singles.get(3)));
    }

    @Test
    void groupOfThreeIsPromotedAtOnceWhenThreeSeatsFree() {
        Trip trip = trip(3);
        List<Booking> seated = new ArrayList<>();
        for (int i = 0; i < 3; i++) seated.add(book(trip, user("ENG"), 1));
        Booking group = book(trip, user("ENG"), 3);
        AuthPrincipal admin = new AuthPrincipal(0L, "admin", Role.ADMIN);

        bookingService.cancel(seated.get(0).getId(), admin);
        assertEquals(BookingStatus.WAITLISTED, status(group));
        bookingService.cancel(seated.get(1).getId(), admin);
        assertEquals(BookingStatus.WAITLISTED, status(group));
        bookingService.cancel(seated.get(2).getId(), admin);
        assertEquals(BookingStatus.CONFIRMED, status(group));
        assertEquals(3, confirmedSeats(trip));
    }
}
