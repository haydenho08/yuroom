package dev.yuroom.availability.core;

import java.time.DayOfWeek;

/**
 * Everything the calendar says about one date.
 *
 * @param effectiveDay whose weekly timetable applies: normally the date's own weekday, but a
 *                     make-up day can run another day's schedule (e.g. Monday on a Friday schedule)
 * @param termCode     the term in session, or null
 * @param label        the calendar entry that decided the status (e.g. "Fall Reading Week"), or null
 */
public record DayContext(DayStatus status, DayOfWeek effectiveDay, String termCode, String label) {

    public boolean timetableApplies() {
        return status == DayStatus.CLASSES;
    }
}
