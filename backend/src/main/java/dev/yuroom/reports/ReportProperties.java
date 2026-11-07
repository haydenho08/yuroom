package dev.yuroom.reports;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code yuroom.reports.*}
 *
 * @param ttl              how long a report shows as a warning
 * @param perRoomWindow    one client can report the same room once per this window
 * @param perClientLimit   max reports by one client (any rooms) per {@code perClientWindow}
 */
@Validated
@ConfigurationProperties("yuroom.reports")
public record ReportProperties(
        @DefaultValue("60m") @NotNull Duration ttl,
        @DefaultValue("30m") @NotNull Duration perRoomWindow,
        @DefaultValue("10") @Min(1) int perClientLimit,
        @DefaultValue("1h") @NotNull Duration perClientWindow,
        @DefaultValue("24h") @NotNull Duration keyRotation,
        @DefaultValue("100000") @Min(1) int maxTrackedClients) {
}
