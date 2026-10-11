package com.moveinsync.shuttle.domain;

import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name = "booking", indexes = {
        @Index(name = "idx_booking_trip_status", columnList = "trip_id,status"),
        @Index(name = "idx_booking_user", columnList = "user_id")})
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "cost_centre", nullable = false, length = 50)
    private String costCentre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tier tier;

    @Column(nullable = false)
    private int seats;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    @Column(length = 60)
    private String groupLabel;

    @Column(nullable = false)
    private Instant requestedAt;

    @Column(nullable = false)
    private long sequenceNo;
    
    @Column(nullable = false)
    private int skipCount;

    @Column(nullable = false)
    private boolean checkedIn;

    @Column(nullable = false)
    private boolean promotedFromWaitlist;

    private Instant confirmedAt;
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CancelReason cancelReason;

    protected Booking() {}

    public Booking(Long tripId, Long userId, String costCentre, Tier tier, int seats, String groupLabel,
                   Instant requestedAt, long sequenceNo) {
        this.tripId = tripId;
        this.userId = userId;
        this.costCentre = costCentre;
        this.tier = tier;
        this.seats = seats;
        this.groupLabel = groupLabel;
        this.requestedAt = requestedAt;
        this.sequenceNo = sequenceNo;
        this.status = BookingStatus.WAITLISTED;
    }

    public void confirm(Instant now, boolean autoCheckIn) {
        this.status = BookingStatus.CONFIRMED;
        this.confirmedAt = now;
        this.checkedIn = autoCheckIn;
    }

    public void cancel(CancelReason reason, Instant now) {
        this.status = BookingStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = now;
    }

    public void checkIn() { this.checkedIn = true; }
    public void incrementSkipCount() { this.skipCount++; }
    public void markPromotedFromWaitlist() { this.promotedFromWaitlist = true; }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public Long getUserId() { return userId; }
    public String getCostCentre() { return costCentre; }
    public Tier getTier() { return tier; }
    public int getSeats() { return seats; }
    public BookingStatus getStatus() { return status; }
    public String getGroupLabel() { return groupLabel; }
    public Instant getRequestedAt() { return requestedAt; }
    public long getSequenceNo() { return sequenceNo; }
    public int getSkipCount() { return skipCount; }
    public boolean isCheckedIn() { return checkedIn; }
    public boolean isPromotedFromWaitlist() { return promotedFromWaitlist; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public CancelReason getCancelReason() { return cancelReason; }
}
