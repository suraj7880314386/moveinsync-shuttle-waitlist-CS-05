package com.moveinsync.shuttle.service;

import com.moveinsync.shuttle.domain.*;
import com.moveinsync.shuttle.dto.Requests.CreateTripRequest;
import com.moveinsync.shuttle.dto.Views.TripView;
import com.moveinsync.shuttle.dto.Views.WaitlistEntryView;
import com.moveinsync.shuttle.exception.ApiException;
import com.moveinsync.shuttle.repository.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {
    private final TripRepository trips;
    private final BookingRepository bookings;
    private final CostCentreQuotaRepository quotas;
    private final WaitlistPromotionService promotion;
    private final WaitlistQueryService waitlist;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TripService(TripRepository trips, BookingRepository bookings, CostCentreQuotaRepository quotas,
                       WaitlistPromotionService promotion, WaitlistQueryService waitlist,
                       ApplicationEventPublisher events, Clock clock) {
        this.trips = trips;
        this.bookings = bookings;
        this.quotas = quotas;
        this.promotion = promotion;
        this.waitlist = waitlist;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public TripView create(CreateTripRequest r) {
        Instant now = clock.instant();
        if (!r.checkInCutoff().isBefore(r.departureTime())) {
            throw ApiException.badRequest("INVALID_CUTOFF", "checkInCutoff must be before departureTime");
        }
        if (!r.checkInCutoff().isAfter(now)) {
            throw ApiException.badRequest("INVALID_CUTOFF", "checkInCutoff must be in the future");
        }
        return TripView.from(trips.save(new Trip(r.routeName().trim(), r.departureTime(), r.checkInCutoff(), r.capacity())));
    }

    @Transactional(readOnly = true)
    public List<TripView> list() {
        return trips.findAllByOrderByDepartureTimeAsc().stream().map(TripView::from).toList();
    }

    @Transactional(readOnly = true)
    public TripView get(Long id) {
        return TripView.from(trips.findById(id).orElseThrow(() -> ApiException.notFound("Trip not found")));
    }

    @Transactional
    public TripView updateCapacity(Long tripId, int newCapacity) {
        Trip trip = lock(tripId);
        long confirmed = bookings.sumSeats(tripId, BookingStatus.CONFIRMED);
        if (newCapacity < confirmed) {
            throw ApiException.conflict("CAPACITY_BELOW_CONFIRMED", "Capacity cannot be lower than the " + confirmed + " confirmed seats");
        }
        trip.setCapacity(newCapacity);
        promotion.runPass(trip, null); 
        events.publishEvent(new TripChangedEvent(tripId));
        return TripView.from(trip);
    }

    @Transactional
    public void setQuota(Long tripId, String costCentre, int seatQuota) {
        Trip trip = lock(tripId);
        String cc = costCentre.trim().toUpperCase();
        long used = bookings.sumSeatsForCostCentre(tripId, BookingStatus.CONFIRMED, cc);
        if (seatQuota < used) {
            throw ApiException.conflict("QUOTA_BELOW_USAGE", "Quota cannot be lower than the " + used + " seats already confirmed");
        }
        CostCentreQuota quota = quotas.findByTripIdAndCostCentre(tripId, cc).orElse(null);
        if (quota == null) {
            quotas.save(new CostCentreQuota(tripId, cc, seatQuota));
        } else {
            quota.setSeatQuota(seatQuota);
        }
        promotion.runPass(trip, null); 
        events.publishEvent(new TripChangedEvent(tripId));
    }

    @Transactional(readOnly = true)
    public List<WaitlistEntryView> waitlist(Long tripId) {
        if (!trips.existsById(tripId)) {
            throw ApiException.notFound("Trip not found");
        }
        List<Booking> ordered = waitlist.ordered(tripId);
        return IntStream.range(0, ordered.size()).mapToObj(i -> {
            Booking b = ordered.get(i);
            return new WaitlistEntryView(i + 1, b.getId(), b.getUserId(), b.getCostCentre(), b.getTier(), b.getSeats(),
                    b.getGroupLabel(), b.getRequestedAt(), b.getSkipCount());
        }).toList();
    }

    private Trip lock(Long tripId) {
        return trips.findByIdForUpdate(tripId).orElseThrow(() -> ApiException.notFound("Trip not found"));
    }
}
