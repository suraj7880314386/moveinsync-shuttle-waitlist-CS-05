package com.moveinsync.shuttle.domain;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;
public final class WaitlistOrder {
    private WaitlistOrder() {}

    public static final Comparator<Booking> COMPARATOR = Comparator
            .comparing((Booking b) -> b.getRequestedAt().truncatedTo(ChronoUnit.SECONDS))
            .thenComparingInt((Booking b) -> b.getTier().rank())
            .thenComparingLong(Booking::getSequenceNo);
}
