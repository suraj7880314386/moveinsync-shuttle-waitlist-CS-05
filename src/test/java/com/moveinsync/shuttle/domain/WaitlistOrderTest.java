package com.moveinsync.shuttle.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WaitlistOrderTest {
    private static Booking b(long seq, Tier tier, String time) {
        return new Booking(1L, seq, "ENG", tier, 1, null, Instant.parse(time), seq);
    }

    private static List<Long> order(Booking... bookings) {
        List<Booking> list = new ArrayList<>(List.of(bookings));
        list.sort(WaitlistOrder.COMPARATOR);
        return list.stream().map(Booking::getSequenceNo).toList();
    }

    @Test
    void fifoByRequestTime() {
        Booking late = b(1, Tier.PLATINUM, "2026-01-01T10:00:05Z");
        Booking early = b(2, Tier.STANDARD, "2026-01-01T10:00:01Z");
        assertEquals(List.of(2L, 1L), order(late, early)); // tier never beats an earlier second
    }

    @Test
    void tierBreaksTiesWithinTheSameSecond() {
        Booking standard = b(1, Tier.STANDARD, "2026-01-01T10:00:00.200Z");
        Booking platinum = b(2, Tier.PLATINUM, "2026-01-01T10:00:00.700Z");
        Booking gold = b(3, Tier.GOLD, "2026-01-01T10:00:00.900Z");
        assertEquals(List.of(2L, 3L, 1L), order(standard, platinum, gold));
    }

    @Test
    void sequenceIsFinalTiebreaker() {
        Booking first = b(1, Tier.GOLD, "2026-01-01T10:00:00Z");
        Booking second = b(2, Tier.GOLD, "2026-01-01T10:00:00Z");
        assertEquals(List.of(1L, 2L), order(second, first));
    }

    @Test
    void lowTierIsNotStarvedByLaterHighTier() {
        Booking standard = b(1, Tier.STANDARD, "2026-01-01T10:00:00Z");
        Booking laterPlatinum = b(2, Tier.PLATINUM, "2026-01-01T10:00:01Z");
        assertEquals(List.of(1L, 2L), order(laterPlatinum, standard));
    }
}
