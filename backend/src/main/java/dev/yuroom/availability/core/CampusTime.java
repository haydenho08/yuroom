package dev.yuroom.availability.core;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Campus time zone and parsing of user-supplied moments. */
public final class CampusTime {

    public static final ZoneId ZONE = ZoneId.of("America/Toronto");

    private CampusTime() {
    }

    /**
     * Parses an API {@code at} value into campus time. Accepted:
     * <ul>
     *   <li>{@code 2026-11-09T14:00} — local campus time (what a person means by "2pm Monday")</li>
     *   <li>{@code 2026-11-09T14:00-05:00} or {@code 2026-11-09T19:00:00Z} — an exact instant</li>
     * </ul>
     * A local time that doesn't exist (02:30 on spring-forward night) moves forward by the gap;
     * an ambiguous one (01:30 on fall-back night) uses the earlier offset, as {@link ZonedDateTime#of} does.
     *
     * @throws IllegalArgumentException with a user-facing message
     */
    public static ZonedDateTime parseAt(String text) {
        String s = text.strip();
        try {
            return OffsetDateTime.parse(s).atZoneSameInstant(ZONE);
        } catch (DateTimeException notOffset) {
            try {
                return ZonedDateTime.of(LocalDateTime.parse(s), ZONE);
            } catch (DateTimeException notLocal) {
                throw new IllegalArgumentException("'" + text + "' is not a date-time like 2026-11-09T14:00"
                        + " or 2026-11-09T14:00-05:00");
            }
        }
    }

    public static ZonedDateTime toCampus(Instant instant) {
        return instant.atZone(ZONE);
    }
}
