package com.moveinsync.shuttle.service;

import com.moveinsync.shuttle.domain.BookingStatus;
import com.moveinsync.shuttle.domain.SeatLedger;
import com.moveinsync.shuttle.domain.Trip;
import com.moveinsync.shuttle.repository.BookingRepository;
import com.moveinsync.shuttle.repository.CostCentreQuotaRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SeatLedgerLoader {
    private final BookingRepository bookings;
    private final CostCentreQuotaRepository quotas;

    public SeatLedgerLoader(BookingRepository bookings, CostCentreQuotaRepository quotas) {
        this.bookings = bookings;
        this.quotas = quotas;
    }

    public SeatLedger load(Trip trip) {
        Map<String, Integer> used = new HashMap<>();
        int total = 0;
        for (Object[] row : bookings.sumSeatsByCostCentre(trip.getId(), BookingStatus.CONFIRMED)) {
            int seats = ((Number) row[1]).intValue();
            used.put((String) row[0], seats);
            total += seats;
        }
        Map<String, Integer> quota = new HashMap<>();
        quotas.findByTripId(trip.getId()).forEach(q -> quota.put(q.getCostCentre(), q.getSeatQuota()));
        return new SeatLedger(trip.getCapacity(), total, used, quota);
    }
}
