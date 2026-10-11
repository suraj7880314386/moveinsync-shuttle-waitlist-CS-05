package com.moveinsync.shuttle.service;

import com.moveinsync.shuttle.domain.Booking;
import com.moveinsync.shuttle.domain.BookingStatus;
import com.moveinsync.shuttle.domain.WaitlistOrder;
import com.moveinsync.shuttle.repository.BookingRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WaitlistQueryService {
    private final BookingRepository bookings;

    public WaitlistQueryService(BookingRepository bookings) {
        this.bookings = bookings;
    }
    public List<Booking> ordered(Long tripId) {
        return bookings.findByTripIdAndStatus(tripId, BookingStatus.WAITLISTED).stream()
                .sorted(WaitlistOrder.COMPARATOR).toList();
    }

    public Integer positionOf(Long tripId, Long bookingId) {
        List<Booking> list = ordered(tripId);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(bookingId)) {
                return i + 1;
            }
        }
        return null;
    }
}
