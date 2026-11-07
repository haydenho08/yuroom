package dev.yuroom.availability.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CampusTimeTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            // local campus time is taken as-is
            "2026-11-09T14:00,             2026-11-09T14:00-05:00[America/Toronto]",
            "2026-07-09T14:00,             2026-07-09T14:00-04:00[America/Toronto]",
            // instants are converted to campus time
            "2026-11-09T19:00:00Z,         2026-11-09T14:00-05:00[America/Toronto]",
            "2026-11-09T14:00-05:00,       2026-11-09T14:00-05:00[America/Toronto]",
            "2026-11-10T09:00+09:00,       2026-11-09T19:00-05:00[America/Toronto]",
            // 02:30 doesn't exist on spring-forward night: moves forward by the gap
            "2027-03-14T02:30,             2027-03-14T03:30-04:00[America/Toronto]",
            // 01:30 happens twice on fall-back night: the earlier (daylight) one is used
            "2026-11-01T01:30,             2026-11-01T01:30-04:00[America/Toronto]",
    })
    void parsesAtIntoCampusTime(String input, String expected) {
        assertThat(CampusTime.parseAt(input)).isEqualTo(ZonedDateTime.parse(expected));
    }

    @Test
    void rejectsGarbageWithAHelpfulMessage() {
        assertThatThrownBy(() -> CampusTime.parseAt("tomorrow at 2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2026-11-09T14:00");
        assertThatThrownBy(() -> CampusTime.parseAt("2026-11-09")).isInstanceOf(IllegalArgumentException.class);
    }
}
