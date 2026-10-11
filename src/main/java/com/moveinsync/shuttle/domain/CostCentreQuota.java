package com.moveinsync.shuttle.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "cost_centre_quota",
        uniqueConstraints = @UniqueConstraint(name = "uk_quota_trip_cc", columnNames = {"trip_id", "cost_centre"}))
public class CostCentreQuota {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "cost_centre", nullable = false, length = 50)
    private String costCentre;

    @Column(nullable = false)
    private int seatQuota;

    protected CostCentreQuota() {}

    public CostCentreQuota(Long tripId, String costCentre, int seatQuota) {
        this.tripId = tripId;
        this.costCentre = costCentre;
        this.seatQuota = seatQuota;
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public String getCostCentre() { return costCentre; }
    public int getSeatQuota() { return seatQuota; }
    public void setSeatQuota(int seatQuota) { this.seatQuota = seatQuota; }
}
