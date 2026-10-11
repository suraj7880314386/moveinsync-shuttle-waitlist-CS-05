package com.moveinsync.shuttle.web;

import com.moveinsync.shuttle.dto.Requests.*;
import com.moveinsync.shuttle.dto.Views.*;
import com.moveinsync.shuttle.service.AvailabilityService;
import com.moveinsync.shuttle.service.BookingService;
import com.moveinsync.shuttle.service.TripService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TripController {
    private final TripService trips;
    private final AvailabilityService availability;
    private final BookingService bookings;

    public TripController(TripService trips, AvailabilityService availability, BookingService bookings) {
        this.trips = trips;
        this.availability = availability;
        this.bookings = bookings;
    }

    @PostMapping("/admin/trips")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public TripView create(@Valid @RequestBody CreateTripRequest r) {
        return trips.create(r);
    }

    @PutMapping("/admin/trips/{id}/capacity")
    @PreAuthorize("hasRole('ADMIN')")
    public TripView capacity(@PathVariable Long id, @Valid @RequestBody UpdateCapacityRequest r) {
        return trips.updateCapacity(id, r.capacity());
    }

    @PostMapping("/admin/trips/{id}/reclaim")
    @PreAuthorize("hasRole('ADMIN')")
    public ReclaimResponse reclaim(@PathVariable Long id) {
        return bookings.reclaimNoShows(id);
    }

    @PutMapping("/trips/{id}/quotas")
    @PreAuthorize("hasAnyRole('ADMIN','APPROVER')")
    public AvailabilityView setQuota(@PathVariable Long id, @Valid @RequestBody SetQuotaRequest r) {
        trips.setQuota(id, r.costCentre(), r.seatQuota());
        return availability.getAvailability(id);
    }

    @GetMapping("/trips")
    public List<TripView> list() {
        return trips.list();
    }

    @GetMapping("/trips/{id}")
    public TripView get(@PathVariable Long id) {
        return trips.get(id);
    }

    @GetMapping("/trips/{id}/availability")
    public AvailabilityView availability(@PathVariable Long id) {
        return availability.getAvailability(id);
    }

    @GetMapping("/trips/{id}/waitlist")
    @PreAuthorize("hasAnyRole('ADMIN','APPROVER')")
    public List<WaitlistEntryView> waitlist(@PathVariable Long id) {
        return trips.waitlist(id);
    }
}
