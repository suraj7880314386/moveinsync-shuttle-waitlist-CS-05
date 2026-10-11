package com.moveinsync.shuttle.scheduler;

import com.moveinsync.shuttle.domain.BookingStatus;
import com.moveinsync.shuttle.repository.BookingRepository;
import com.moveinsync.shuttle.service.BookingService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NoShowReclaimJob {
    private static final Logger log = LoggerFactory.getLogger(NoShowReclaimJob.class);

    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final Clock clock;

    public NoShowReclaimJob(BookingRepository bookings, BookingService bookingService, Clock clock) {
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${app.reclaim.interval-ms}", fixedDelayString = "${app.reclaim.interval-ms}")
    public void run() {
        for (Long tripId : bookings.findTripIdsWithNoShows(BookingStatus.CONFIRMED, clock.instant())) {
            try {
                bookingService.reclaimNoShows(tripId); // one failing trip never blocks the others
            } catch (Exception ex) {
                log.error("event=reclaim_failed tripId={} error={}", tripId, ex.toString());
            }
        }
    }
}
