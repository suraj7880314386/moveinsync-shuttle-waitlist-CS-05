package com.moveinsync.shuttle.web;

import com.moveinsync.shuttle.dto.Requests.SeatRequest;
import com.moveinsync.shuttle.dto.Views.BookingView;
import com.moveinsync.shuttle.dto.Views.CancelResponse;
import com.moveinsync.shuttle.security.AuthPrincipal;
import com.moveinsync.shuttle.service.BookingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class BookingController {
    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping("/trips/{tripId}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingView book(@PathVariable Long tripId, @Valid @RequestBody(required = false) SeatRequest body,
                            @AuthenticationPrincipal AuthPrincipal me) {
        int seats = body == null || body.seats() == null ? 1 : body.seats();
        String label = body == null ? null : body.groupLabel();
        return bookings.requestSeats(tripId, me.userId(), seats, label);
    }

    @GetMapping("/bookings/me")
    public List<BookingView> mine(@AuthenticationPrincipal AuthPrincipal me) {
        return bookings.mine(me.userId());
    }

    @GetMapping("/bookings/{id}")
    public BookingView get(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal me) {
        return bookings.get(id, me);
    }

    @PostMapping("/bookings/{id}/cancel")
    public CancelResponse cancel(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal me) {
        return bookings.cancel(id, me);
    }

    @PostMapping("/bookings/{id}/check-in")
    public BookingView checkIn(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal me) {
        return bookings.checkIn(id, me);
    }
}
