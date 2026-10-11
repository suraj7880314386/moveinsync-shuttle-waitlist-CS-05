package com.moveinsync.shuttle.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromotionPlannerTest {
    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private final PromotionPlanner planner = new PromotionPlanner(3);

    private static Booking b(long seq, String cc, int seats) {
        return new Booking(1L, seq, cc, Tier.STANDARD, seats, null, T0.plusSeconds(seq), seq);
    }

    private static List<Long> seqs(PassResult r) {
        return r.promoted().stream().map(Booking::getSequenceNo).toList();
    }

    @Test
    void groupOfTwoSkippedWhenOnlyOneSeatFrees() {
        SeatLedger ledger = new SeatLedger(40, 39, Map.of("ENG", 39), Map.of());
        Booking group = b(1, "ENG", 2);
        Booking single = b(2, "ENG", 1);
        PassResult r = planner.plan(List.of(group, single), ledger, T0, false);
        assertEquals(List.of(2L), seqs(r));
        assertEquals(BookingStatus.WAITLISTED, group.getStatus());
        assertEquals(1, group.getSkipCount());
        assertEquals(40, ledger.confirmedSeats());
    }

    @Test
    void groupPromotedAllOrNothingWhenEnoughSeats() {
        SeatLedger ledger = new SeatLedger(10, 8, Map.of(), Map.of());
        Booking group = b(1, "ENG", 2);
        PassResult r = planner.plan(List.of(group), ledger, T0, false);
        assertEquals(BookingStatus.CONFIRMED, group.getStatus());
        assertEquals(10, ledger.confirmedSeats());
        assertEquals(1, r.promoted().size());
    }

    @Test
    void freeSeatBlockedByCostCentreQuotaGoesToNextEligible() {
        SeatLedger ledger = new SeatLedger(10, 8, Map.of("ENG", 1), Map.of("ENG", 1));
        Booking eng = b(1, "ENG", 1);
        Booking ops = b(2, "OPS", 1);
        PassResult r = planner.plan(List.of(eng, ops), ledger, T0, false);
        assertEquals(List.of(2L), seqs(r));
        assertEquals(1, r.quotaBlocked());
        assertEquals(BookingStatus.WAITLISTED, eng.getStatus());
    }

    @Test
    void starvingGroupReservesFreeSeats() {
        SeatLedger ledger = new SeatLedger(10, 9, Map.of(), Map.of());
        Booking group = b(1, "ENG", 3);
        for (int i = 0; i < 3; i++) group.incrementSkipCount();
        Booking single = b(2, "ENG", 1);
        PassResult r = planner.plan(List.of(group, single), ledger, T0, false);
        assertTrue(r.promoted().isEmpty());
        assertTrue(r.reservationApplied());
        assertEquals(BookingStatus.WAITLISTED, single.getStatus());
    }

    @Test
    void skipCountOnlyGrowsWhenSomeoneBehindWasPromoted() {
        SeatLedger ledger = new SeatLedger(10, 9, Map.of(), Map.of());
        Booking group = b(1, "ENG", 3);
        planner.plan(List.of(group), ledger, T0, false); // nobody behind -> not overtaken
        assertEquals(0, group.getSkipCount());
    }

    @Test
    void autoCheckInAfterCutoff() {
        SeatLedger ledger = new SeatLedger(2, 0, Map.of(), Map.of());
        Booking s = b(1, "ENG", 1);
        planner.plan(List.of(s), ledger, T0, true);
        assertTrue(s.isCheckedIn());
    }
}
