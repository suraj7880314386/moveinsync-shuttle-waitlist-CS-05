package com.moveinsync.shuttle.service;

import com.moveinsync.shuttle.config.AppProperties;
import com.moveinsync.shuttle.domain.*;
import com.moveinsync.shuttle.metrics.ShuttleMetrics;
import com.moveinsync.shuttle.notification.NotificationService;
import com.moveinsync.shuttle.repository.BookingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WaitlistPromotionService {
    private static final Logger log = LoggerFactory.getLogger(WaitlistPromotionService.class);

    private final BookingRepository bookings;
    private final SeatLedgerLoader ledgerLoader;
    private final NotificationService notifications;
    private final ShuttleMetrics metrics;
    private final Clock clock;
    private final PromotionPlanner planner;

    public WaitlistPromotionService(BookingRepository bookings, SeatLedgerLoader ledgerLoader,
                                    NotificationService notifications, ShuttleMetrics metrics, Clock clock,
                                    AppProperties props) {
        this.bookings = bookings;
        this.ledgerLoader = ledgerLoader;
        this.notifications = notifications;
        this.metrics = metrics;
        this.clock = clock;
        this.planner = new PromotionPlanner(props.booking().maxSkipsBeforeReservation());
    }
    public List<Booking> runPass(Trip trip, Long selfBookingId) {
        SeatLedger ledger = ledgerLoader.load(trip);
        if (ledger.freeSeats() <= 0) {
            return List.of(); 
        }
        List<Booking> waitlisted = bookings.findByTripIdAndStatus(trip.getId(), BookingStatus.WAITLISTED);
        if (waitlisted.isEmpty()) {
            return List.of();
        }
        Instant now = clock.instant();
        boolean autoCheckIn = !now.isBefore(trip.getCheckInCutoff()); 
        PassResult result = metrics.timePass(() -> planner.plan(waitlisted, ledger, now, autoCheckIn));

        metrics.promotionSkipped("quota", result.quotaBlocked());
        metrics.promotionSkipped("capacity", result.capacityBlocked());
        if (result.reservationApplied()) {
            metrics.promotionSkipped("starvation_reservation", 1);
        }

        List<Booking> promotions = result.promoted().stream()
                .filter(b -> !Objects.equals(b.getId(), selfBookingId)).toList();
        for (Booking p : promotions) {
            p.markPromotedFromWaitlist();
            log.info("event=promoted tripId={} bookingId={} userId={} seats={} costCentre={}",
                    trip.getId(), p.getId(), p.getUserId(), p.getSeats(), p.getCostCentre());
            notifications.enqueue(p.getUserId(), "Good news! Your " + p.getSeats() + " seat(s) on shuttle '"
                    + trip.getRouteName() + "' departing " + trip.getDepartureTime() + " are now CONFIRMED.");
        }
        metrics.promoted(promotions.size());
        return promotions;
    }
}
