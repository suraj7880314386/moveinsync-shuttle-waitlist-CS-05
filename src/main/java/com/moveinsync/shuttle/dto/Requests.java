package com.moveinsync.shuttle.dto;

import com.moveinsync.shuttle.domain.Role;
import com.moveinsync.shuttle.domain.Tier;
import jakarta.validation.constraints.*;
import java.time.Instant;

public final class Requests {
    private Requests() {}

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "phone must be 10-15 digits with optional leading +") String phone,
            @NotBlank @Size(max = 50) String costCentre,
            Tier tier) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "phone must be 10-15 digits with optional leading +") String phone,
            @NotBlank @Size(max = 50) String costCentre,
            Tier tier,
            @NotNull Role role) {}

    public record CreateTripRequest(
            @NotBlank @Size(max = 100) String routeName,
            @NotNull @Future Instant departureTime,
            @NotNull Instant checkInCutoff,
            @Min(1) @Max(200) int capacity) {}

    public record UpdateCapacityRequest(@Min(1) @Max(200) int capacity) {}

    public record SetQuotaRequest(@NotBlank @Size(max = 50) String costCentre, @Min(0) int seatQuota) {}

    public record SeatRequest(@Min(1) @Max(50) Integer seats, @Size(max = 60) String groupLabel) {}
}
