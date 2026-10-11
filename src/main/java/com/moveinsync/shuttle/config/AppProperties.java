package com.moveinsync.shuttle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Booking booking, Notifications notifications, Backup backup,
                            Sms sms, Seed seed) {

    public record Jwt(String secret, @DefaultValue("120") long expirationMinutes) {}

    public record Booking(@DefaultValue("10") int maxGroupSize,
                          @DefaultValue("3") int maxSkipsBeforeReservation) {}

    public record Notifications(@DefaultValue("5") int maxAttempts) {}

    public record Backup(@DefaultValue("true") boolean enabled,
                         @DefaultValue("./backups") String directory) {}

    public record Sms(@DefaultValue("0.0") double failureProbability) {}

    public record Seed(@DefaultValue("true") boolean enabled) {}
}
