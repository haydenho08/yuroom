package dev.yuroom.availability.core;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A date range from the official academic calendar (inclusive on both ends).
 *
 * @param followsDay only for {@link Kind#MAKEUP_DAY}: whose timetable runs that day
 */
public record CalendarPeriod(Kind kind, LocalDate start, LocalDate end, String label, DayOfWeek followsDay) {

    /** Declared in precedence order: when periods overlap, the earlier kind wins. */
    public enum Kind { CLOSED, EXAMS, NO_CLASSES, MAKEUP_DAY }

    public CalendarPeriod {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        Objects.requireNonNull(label);
        if (end.isBefore(start)) {
            throw new IllegalArgumentException(label + ": end " + end + " is before start " + start);
        }
        if ((kind == Kind.MAKEUP_DAY) != (followsDay != null)) {
            throw new IllegalArgumentException(label + ": follows_day is required for MAKEUP_DAY and only for it");
        }
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
