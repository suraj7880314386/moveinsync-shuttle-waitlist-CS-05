package com.moveinsync.shuttle.service;

import com.moveinsync.shuttle.config.AppProperties;
import com.moveinsync.shuttle.domain.*;
import com.moveinsync.shuttle.dto.Views.BookingView;
import com.moveinsync.shuttle.dto.Views.CancelResponse;
import com.moveinsync.shuttle.dto.Views.ReclaimResponse;
import com.moveinsync.shuttle.exception.ApiException;
import com.moveinsync.shuttle.metrics.ShuttleMetrics;
import com.moveinsync.shuttle.notification.NotificationService;
import com.moveinsync.shuttle.repository.*;
import com.moveinsync.shuttle.security.AuthPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final Set<BookingStatus> ACTIVE = EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.WAITLISTED);

    private final TripRepository trips;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final CostCentreQuotaRepository quotas;
    private final WaitlistPromotionService promotion;
    private final WaitlistQueryService waitlist;
    private final NotificationService notifications;
    private final ShuttleMetrics metrics;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AppProperties props;

    public BookingService(TripRepository trips, BookingRepository bookings, UserRepository users,
                          CostCentreQuotaRepository quotas, WaitlistPromotionService promotion,
                          WaitlistQueryService waitlist, NotificationService notifications, ShuttleMetrics metrics,
                          ApplicationEventPublisher events, Clock clock, AppProperties props) {
        this.trips = trips;
        this.bookings = bookings;
        this.users = users;
        this.quotas = quotas;
        this.promotion = promotion;
        this.waitlist = waitlist;
        this.notifications = notifications;
        this.metrics = metrics;
        this.events = events;
        this.clock = clock;
        this.props = props;
    }

    @Transactional
    public BookingView requestSeats(Long tripId, Long userId, int seats, String groupLabel) {
        AppUser user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        Trip trip = lockTrip(tripId);
        Instant now = clock.instant();

        if (!now.isBefore(trip.getCheckInCutoff())) {
            throw ApiException.conflict("BOOKING_CLOSED", "Booking is closed for this trip");
        }
        if (seats > props.booking().maxGroupSize()) {
            throw ApiException.badRequest("GROUP_TOO_LARGE", "Maximum group size is " + props.booking().maxGroupSize());
        }
        if (seats > trip.getCapacity()) {
            throw ApiException.unprocessable("EXCEEDS_VEHICLE_CAPACITY", "Request is larger than the vehicle capacity");
        }
        quotas.findByTripIdAndCostCentre(tripId, user.getCostCentre()).ifPresent(q -> {
            if (seats > q.getSeatQuota()) {
                throw ApiException.unprocessable("EXCEEDS_COST_CENTRE_QUOTA",
                        "Request can never fit the cost-centre quota of this trip");
            }
        });
        if (bookings.existsByTripIdAndUserIdAndStatusIn(tripId, userId, ACTIVE)) {
            throw ApiException.conflict("DUPLICATE_BOOKING", "You already have an active booking on this trip");
        }

        Booking booking = bookings.save(new Booking(tripId, userId, user.getCostCentre(), user.getTier(), seats,
                groupLabel, now, trip.allocateSequence()));
        promotion.runPass(trip, booking.getId());

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            metrics.bookingConfirmed();
        } else {
            metrics.bookingWaitlisted();
        }
        log.info("event=booking_created tripId={} bookingId={} userId={} seats={} status={}",
                tripId, booking.getId(), userId, seats, booking.getStatus());
        events.publishEvent(new TripChangedEvent(tripId));
        Integer position = booking.getStatus() == BookingStatus.WAITLISTED
                ? waitlist.positionOf(tripId, booking.getId()) : null;
        return BookingView.from(booking, position);
    }

    @Transactional
    public CancelResponse cancel(Long bookingId, AuthPrincipal actor) {
        Long tripId = bookings.findTripIdById(bookingId).orElseThrow(() -> ApiException.notFound("Booking not found"));
        Trip trip = lockTrip(tripId);
        Booking booking = bookings.findById(bookingId).orElseThrow(() -> ApiException.notFound("Booking not found"));
        assertAccess(booking, actor);

        Instant now = clock.instant();
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw ApiException.conflict("ALREADY_CANCELLED", "Booking is already cancelled");
        }
        if (!now.isBefore(trip.getDepartureTime())) {
            throw ApiException.conflict("TRIP_DEPARTED", "Trip has already departed");
        }
        boolean heldSeats = booking.getStatus() == BookingStatus.CONFIRMED;
        CancelReason reason = booking.getUserId().equals(actor.userId()) ? CancelReason.USER : CancelReason.ADMIN;
        booking.cancel(reason, now);
        metrics.bookingCancelled(reason.name());

        List<Booking> promoted = promotion.runPass(trip, null);
        log.info("event=booking_cancelled tripId={} bookingId={} heldSeats={} promotions={}",
                tripId, bookingId, heldSeats, promoted.size());
        events.publishEvent(new TripChangedEvent(tripId));
        return new CancelResponse(BookingView.from(booking, null),
                promoted.stream().map(p -> BookingView.from(p, null)).toList());
    }

    @Transactional
    public BookingView checkIn(Long bookingId, AuthPrincipal actor) {
        Long tripId = bookings.findTripIdById(bookingId).orElseThrow(() -> ApiException.notFound("Booking not found"));
        Trip trip = lockTrip(tripId);
        Booking booking = bookings.findById(bookingId).orElseThrow(() -> ApiException.notFound("Booking not found"));
        assertAccess(booking, actor);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw ApiException.conflict("NOT_CONFIRMED", "Only CONFIRMED bookings can check in");
        }
        if (!clock.instant().isBefore(trip.getCheckInCutoff())) {
            throw ApiException.conflict("CHECK_IN_CLOSED", "Check-in cut-off has passed");
        }
        booking.checkIn();
        log.info("event=checked_in tripId={} bookingId={}", tripId, bookingId);
        return BookingView.from(booking, null);
    }

    @Transactional
    public ReclaimResponse reclaimNoShows(Long tripId) {
        Trip trip = lockTrip(tripId);
        Instant now = clock.instant();
        if (now.isBefore(trip.getCheckInCutoff())) {
            throw ApiException.conflict("CUTOFF_NOT_REACHED", "Check-in cut-off has not passed yet");
        }
        List<Booking> noShows = bookings.findByTripIdAndStatusAndCheckedInFalse(tripId, BookingStatus.CONFIRMED);
        int seats = 0;
        for (Booking b : noShows) {
            b.cancel(CancelReason.NO_SHOW, now);
            seats += b.getSeats();
            metrics.bookingCancelled(CancelReason.NO_SHOW.name());
            notifications.enqueue(b.getUserId(), "Your " + b.getSeats() + " seat(s) on shuttle '" + trip.getRouteName()
                    + "' were released because you did not check in before the cut-off.");
        }
        metrics.noShowReclaimed(noShows.size());
        List<Booking> promoted = promotion.runPass(trip, null);
        log.info("event=noshow_reclaimed tripId={} bookings={} seats={} promotions={}",
                tripId, noShows.size(), seats, promoted.size());
        events.publishEvent(new TripChangedEvent(tripId));
        return new ReclaimResponse(noShows.size(), seats, promoted.stream().map(p -> BookingView.from(p, null)).toList());
    }

    @Transactional(readOnly = true)
    public BookingView get(Long bookingId, AuthPrincipal actor) {
        Booking booking = bookings.findById(bookingId).orElseThrow(() -> ApiException.notFound("Booking not found"));
        assertAccess(booking, actor);
        Integer position = booking.getStatus() == BookingStatus.WAITLISTED
                ? waitlist.positionOf(booking.getTripId(), bookingId) : null;
        return BookingView.from(booking, position);
    }

    @Transactional(readOnly = true)
    public List<BookingView> mine(Long userId) {
        return bookings.findByUserIdOrderByRequestedAtDesc(userId).stream()
                .map(b -> BookingView.from(b, b.getStatus() == BookingStatus.WAITLISTED
                        ? waitlist.positionOf(b.getTripId(), b.getId()) : null))
                .toList();
    }

    private Trip lockTrip(Long tripId) {
        return trips.findByIdForUpdate(tripId).orElseThrow(() -> ApiException.notFound("Trip not found"));
    }

    private void assertAccess(Booking booking, AuthPrincipal actor) {
        if (actor.role() != Role.ADMIN && !booking.getUserId().equals(actor.userId())) {
            throw ApiException.forbidden("You are not allowed to access this booking");
        }
    }
}
