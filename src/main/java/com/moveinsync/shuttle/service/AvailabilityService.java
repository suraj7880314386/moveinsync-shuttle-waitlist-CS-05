package com.moveinsync.shuttle.service;

import com.moveinsync.shuttle.domain.BookingStatus;
import com.moveinsync.shuttle.domain.SeatLedger;
import com.moveinsync.shuttle.domain.Trip;
import com.moveinsync.shuttle.dto.Views.AvailabilityView;
import com.moveinsync.shuttle.dto.Views.QuotaUsage;
import com.moveinsync.shuttle.exception.ApiException;
import com.moveinsync.shuttle.repository.BookingRepository;
import com.moveinsync.shuttle.repository.TripRepository;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {
    public static final String CACHE = "tripAvailability";

    private final TripRepository trips;
    private final BookingRepository bookings;
    private final SeatLedgerLoader ledgerLoader;
    private final Clock clock;

    public AvailabilityService(TripRepository trips, BookingRepository bookings, SeatLedgerLoader ledgerLoader, Clock clock) {
        this.trips = trips;
        this.bookings = bookings;
        this.ledgerLoader = ledgerLoader;
        this.clock = clock;
    }

    @Cacheable(cacheNames = CACHE, key = "#tripId")
    @Transactional(readOnly = true)
    public AvailabilityView getAvailability(Long tripId) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> ApiException.notFound("Trip not found"));
        SeatLedger ledger = ledgerLoader.load(trip);
        List<QuotaUsage> quotas = ledger.quotas().entrySet().stream()
                .map(e -> new QuotaUsage(e.getKey(), e.getValue(), ledger.used(e.getKey()),
                        Math.max(0, e.getValue() - ledger.used(e.getKey()))))
                .sorted(Comparator.comparing(QuotaUsage::costCentre)).toList();
        return new AvailabilityView(trip.getId(), trip.getRouteName(), ledger.capacity(), ledger.confirmedSeats(),
                ledger.freeSeats(), bookings.countByTripIdAndStatus(tripId, BookingStatus.WAITLISTED),
                bookings.sumSeats(tripId, BookingStatus.WAITLISTED), quotas, clock.instant());
    }
}
