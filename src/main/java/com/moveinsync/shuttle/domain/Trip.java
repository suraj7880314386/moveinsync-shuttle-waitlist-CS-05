package com.moveinsync.shuttle.domain;

import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name = "trip")
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String routeName;

    @Column(nullable = false)
    private Instant departureTime;

    @Column(nullable = false)
    private Instant checkInCutoff;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private long nextSequence = 1L;

    protected Trip() {}

    public Trip(String routeName, Instant departureTime, Instant checkInCutoff, int capacity) {
        this.routeName = routeName;
        this.departureTime = departureTime;
        this.checkInCutoff = checkInCutoff;
        this.capacity = capacity;
    }

    public long allocateSequence() { return nextSequence++; }

    public Long getId() { return id; }
    public String getRouteName() { return routeName; }
    public Instant getDepartureTime() { return departureTime; }
    public Instant getCheckInCutoff() { return checkInCutoff; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
}
