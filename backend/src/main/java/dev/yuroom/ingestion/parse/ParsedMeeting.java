package dev.yuroom.ingestion.parse;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A meeting as read from a source file, before it is matched to database rows.
 * Plain data: no JPA, no Spring. Optional fields are {@code null} when absent.
 */
public record ParsedMeeting(
        String termCode,
        String buildingCode,
        String campus,
        String roomNumber,
        DayOfWeek day,
        LocalTime start,
        LocalTime end,
        LocalDate startDate,
        LocalDate endDate,
        String courseCode,
        String section,
        String meetingType) {
}
