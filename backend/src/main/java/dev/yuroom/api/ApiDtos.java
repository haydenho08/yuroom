package dev.yuroom.api;

import dev.yuroom.availability.core.TimeRange;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The API's public shapes. Separate from JPA entities on purpose: the wire format can't leak
 * internal fields (course codes stay in the database), and refactoring entities can't
 * silently break clients.
 */
public final class ApiDtos {

    private ApiDtos() {
    }

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    static String hm(LocalTime t) {
        return HM.format(t);
    }

    public record BuildingDto(String code, String name, String campus, long roomCount) {
    }

    public record HoursDto(String open, String close) {
    }

    public record RangeDto(String start, String end) {
        static RangeDto of(TimeRange r) {
            return new RangeDto(hm(r.start()), hm(r.end()));
        }
    }

    /**
     * @param freeUntilReason NEXT_BOOKING (a class starts) or CLOSING (end of usual hours)
     */
    public record FreeRoomDto(long roomId, String building, String room, String freeUntil,
                              String freeUntilReason, long minutesFree) {
    }

    /**
     * @param status        OK, NO_CLASSES, OUTSIDE_HOURS, CLOSED, EXAMS or OUTSIDE_TERM
     * @param notice        human-readable caveat to show above the list, or null
     * @param timetableDay  whose weekly timetable applies (differs from the weekday on make-up days)
     * @param dataUpdatedAt when the timetable was last confirmed current
     */
    public record FreeRoomsResponse(OffsetDateTime at, LocalDate date, String status, String notice, String term,
                                    String timetableDay, HoursDto hours, Instant dataUpdatedAt,
                                    List<FreeRoomDto> rooms) {
    }

    public record RoomScheduleResponse(long roomId, String building, String room, LocalDate date, String status,
                                       String notice, String term, String timetableDay, HoursDto hours,
                                       List<RangeDto> booked, List<RangeDto> free, Instant dataUpdatedAt) {
    }
}
