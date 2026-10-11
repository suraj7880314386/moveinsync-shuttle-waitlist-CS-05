package com.moveinsync.shuttle.domain;

import java.util.List;

public record PassResult(List<Booking> promoted, int quotaBlocked, int capacityBlocked, boolean reservationApplied) {}
