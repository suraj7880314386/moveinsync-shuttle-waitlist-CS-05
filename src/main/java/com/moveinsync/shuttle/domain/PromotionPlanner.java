package com.moveinsync.shuttle.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
public final class PromotionPlanner {
    private final int maxSkips;

    public PromotionPlanner(int maxSkips) { this.maxSkips = maxSkips; }

    public PassResult plan(List<Booking> waitlisted, SeatLedger ledger, Instant now, boolean autoCheckIn) {
        List<Booking> ordered = new ArrayList<>(waitlisted);
        ordered.sort(WaitlistOrder.COMPARATOR);

        List<Booking> promoted = new ArrayList<>();
        List<Booking> overtaken = new ArrayList<>();
        int quotaBlocked = 0;
        int capacityBlocked = 0;
        boolean reservation = false;

        for (Booking candidate : ordered) {
            if (ledger.freeSeats() <= 0) {
                break;
            }
            if (!ledger.fitsQuota(candidate.getCostCentre(), candidate.getSeats())) {
                quotaBlocked++;
                continue;
            }
            if (!ledger.fitsCapacity(candidate.getSeats())) {
                if (candidate.getSkipCount() >= maxSkips) {
                    reservation = true;
                    break;
                }
                capacityBlocked++;
                overtaken.add(candidate);
                continue;
            }
            ledger.allocate(candidate.getCostCentre(), candidate.getSeats());
            candidate.confirm(now, autoCheckIn);
            promoted.add(candidate);
            overtaken.forEach(Booking::incrementSkipCount);
            overtaken.clear();
        }
        return new PassResult(promoted, quotaBlocked, capacityBlocked, reservation);
    }
}
