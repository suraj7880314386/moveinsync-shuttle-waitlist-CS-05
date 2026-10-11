package com.moveinsync.shuttle.dto;

import com.moveinsync.shuttle.domain.*;
import java.time.Instant;
import java.util.List;

public final class Views {
    private Views() {}

    public record UserView(Long id, String username, Role role, String costCentre, Tier tier, String phone) {
        public static UserView from(AppUser u) {
            return new UserView(u.getId(), u.getUsername(), u.getRole(), u.getCostCentre(), u.getTier(), u.getPhone());
        }
    }

    public record AuthResponse(String token, String tokenType, long expiresInMinutes, Long userId, String username, Role role) {}

    public record TripView(Long id, String routeName, Instant departureTime, Instant checkInCutoff, int capacity) {
        public static TripView from(Trip t) {
            return new TripView(t.getId(), t.getRouteName(), t.getDepartureTime(), t.getCheckInCutoff(), t.getCapacity());
        }
    }

    public record BookingView(Long bookingId, Long tripId, Long userId, String costCentre, Tier tier, int seats,
                              String groupLabel, BookingStatus status, Integer waitlistPosition, boolean checkedIn,
                              boolean promotedFromWaitlist, Instant requestedAt, Instant confirmedAt,
                              CancelReason cancelReason) {
        public static BookingView from(Booking b, Integer waitlistPosition) {
            return new BookingView(b.getId(), b.getTripId(), b.getUserId(), b.getCostCentre(), b.getTier(), b.getSeats(),
                    b.getGroupLabel(), b.getStatus(), waitlistPosition, b.isCheckedIn(), b.isPromotedFromWaitlist(),
                    b.getRequestedAt(), b.getConfirmedAt(), b.getCancelReason());
        }
    }

    public record QuotaUsage(String costCentre, int quota, int used, int remaining) {}

    public record AvailabilityView(Long tripId, String routeName, int capacity, int confirmedSeats, int freeSeats,
                                   long waitlistedBookings, long waitlistedSeats, List<QuotaUsage> quotas,
                                   Instant computedAt) {}

    public record WaitlistEntryView(int position, Long bookingId, Long userId, String costCentre, Tier tier,
                                    int seats, String groupLabel, Instant requestedAt, int skipCount) {}

    public record CancelResponse(BookingView cancelled, List<BookingView> promoted) {}

    public record ReclaimResponse(int reclaimedBookings, int reclaimedSeats, List<BookingView> promoted) {}

    public record ReconcileResult(int tripsChecked, int promotions, int violations) {}

    public record BackupResult(String file) {}

    public record NotificationView(Long id, Long userId, String message, NotificationStatus status, int attempts,
                                   Instant createdAt, Instant sentAt, String lastError) {
        public static NotificationView from(Notification n) {
            return new NotificationView(n.getId(), n.getUserId(), n.getMessage(), n.getStatus(), n.getAttempts(),
                    n.getCreatedAt(), n.getSentAt(), n.getLastError());
        }
    }
}
