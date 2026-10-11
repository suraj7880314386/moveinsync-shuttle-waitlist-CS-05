package com.moveinsync.shuttle.service;

import com.moveinsync.shuttle.domain.*;
import com.moveinsync.shuttle.repository.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfig.class)
abstract class AbstractIntegrationTest {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired protected BookingService bookingService;
    @Autowired protected TripService tripService;
    @Autowired protected TripRepository tripRepo;
    @Autowired protected UserRepository userRepo;
    @Autowired protected BookingRepository bookingRepo;
    @Autowired protected CostCentreQuotaRepository quotaRepo;
    @Autowired protected MutableClock clock;

    protected AppUser user(String cc, Tier tier) {
        String name = "user" + COUNTER.incrementAndGet();
        return userRepo.save(new AppUser(name, "x", Role.EMPLOYEE, cc, tier, "+910000000000"));
    }

    protected AppUser user(String cc) { return user(cc, Tier.STANDARD); }

    protected Trip trip(int capacity) {
        Instant now = clock.instant();
        return tripRepo.save(new Trip("Route-" + COUNTER.incrementAndGet(), now.plus(Duration.ofHours(3)),
                now.plus(Duration.ofHours(2)), capacity));
    }

    protected Booking book(Trip t, AppUser u, int seats) {
        var view = bookingService.requestSeats(t.getId(), u.getId(), seats, null);
        return bookingRepo.findById(view.bookingId()).orElseThrow();
    }

    protected long confirmedSeats(Trip t) {
        return bookingRepo.sumSeats(t.getId(), BookingStatus.CONFIRMED);
    }

    protected BookingStatus status(Booking b) {
        return bookingRepo.findById(b.getId()).orElseThrow().getStatus();
    }

    protected List<Booking> byStatus(Trip t, BookingStatus s) {
        return bookingRepo.findByTripIdAndStatus(t.getId(), s);
    }
}
