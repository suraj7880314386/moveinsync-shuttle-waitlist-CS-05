package com.moveinsync.shuttle.domain;

import java.util.HashMap;
import java.util.Map;
public final class SeatLedger {
    private final int capacity;
    private int confirmedSeats;
    private final Map<String, Integer> usedByCostCentre;
    private final Map<String, Integer> quotaByCostCentre;

    public SeatLedger(int capacity, int confirmedSeats, Map<String, Integer> usedByCostCentre,
                      Map<String, Integer> quotaByCostCentre) {
        this.capacity = capacity;
        this.confirmedSeats = confirmedSeats;
        this.usedByCostCentre = new HashMap<>(usedByCostCentre);
        this.quotaByCostCentre = new HashMap<>(quotaByCostCentre);
    }

    public int capacity() { return capacity; }
    public int confirmedSeats() { return confirmedSeats; }
    public int freeSeats() { return capacity - confirmedSeats; }
    public int used(String costCentre) { return usedByCostCentre.getOrDefault(costCentre, 0); }
    public Integer quotaOf(String costCentre) { return quotaByCostCentre.get(costCentre); }
    public Map<String, Integer> quotas() { return Map.copyOf(quotaByCostCentre); }

    public boolean fitsCapacity(int seats) { return seats <= freeSeats(); }
    public boolean fitsQuota(String costCentre, int seats) {
        Integer quota = quotaByCostCentre.get(costCentre);
        return quota == null || used(costCentre) + seats <= quota;
    }

    public void allocate(String costCentre, int seats) {
        confirmedSeats += seats;
        usedByCostCentre.merge(costCentre, seats, Integer::sum);
    }
}
