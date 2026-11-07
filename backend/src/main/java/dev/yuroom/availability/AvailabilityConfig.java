package dev.yuroom.availability;

import dev.yuroom.availability.core.AcademicCalendar;
import dev.yuroom.availability.core.CalendarCsvReader;
import dev.yuroom.availability.core.CampusTime;
import dev.yuroom.availability.core.FreeTimeCalculator;
import dev.yuroom.availability.core.RoomAvailability;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/** Wires the pure core into Spring. The core classes themselves never import Spring. */
@Configuration
class AvailabilityConfig {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityConfig.class);

    @Bean
    RoomAvailability roomAvailability(AvailabilityProperties props) {
        return new RoomAvailability(new FreeTimeCalculator(CampusTime.ZONE, props.openTime(), props.closeTime()));
    }

    /**
     * The calendar is ~a dozen rows that change once a year, read on every request: it lives in
     * memory, loaded from a versioned file at startup. A bad file stops the app from starting.
     */
    @Bean
    AcademicCalendar academicCalendar(AvailabilityProperties props, ResourceLoader resources) throws IOException {
        try (Reader in = new InputStreamReader(resources.getResource(props.calendar()).getInputStream(),
                StandardCharsets.UTF_8)) {
            AcademicCalendar calendar = new AcademicCalendar(CalendarCsvReader.read(in));
            log.info("Loaded {} academic calendar periods from {}", calendar.periods().size(), props.calendar());
            return calendar;
        }
    }
}
