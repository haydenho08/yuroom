package dev.yuroom.availability.core;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The heart of "is it free?". Pure functions over wall-clock intervals: no Spring, no database,
 * no "now". Everything it needs is passed in, so every edge case is a one-line unit test.
 *
 * <p>Wall-clock times are compared as {@link LocalTime} (that's how timetables are written), but
 * <b>durations</b> are computed through {@link ZonedDateTime} in the campus zone, so "free from
 * 01:00 to 03:00" is 3 hours on the night clocks fall back and 1 hour on the night they spring forward.
 */
public final class FreeTimeCalculator {

    private final ZoneId zone;
    private final LocalTime open;
    private final LocalTime close;

    /**
     * @param open  earliest time we report rooms as free (buildings' usual opening)
     * @param close time we stop reporting; a free window never extends past it
     */
    public FreeTimeCalculator(ZoneId zone, LocalTime open, LocalTime close) {
        this.zone = Objects.requireNonNull(zone);
        this.open = Objects.requireNonNull(open);
        this.close = Objects.requireNonNull(close);
        if (!close.isAfter(open)) {
            throw new IllegalArgumentException("close " + close + " must be after open " + open);
        }
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalTime open() {
        return open;
    }

    public LocalTime close() {
        return close;
    }

    public boolean withinHours(LocalTime t) {
        return !t.isBefore(open) && t.isBefore(close);
    }

    /**
     * Sorts and merges overlapping <i>and touching</i> ranges: 8:30-10:00 + 10:00-11:30 becomes
     * 8:30-11:30, because a room with back-to-back classes is never free in between.
     */
    public static List<TimeRange> merge(Collection<TimeRange> ranges) {
        List<TimeRange> sorted = ranges.stream().sorted().toList();
        List<TimeRange> merged = new ArrayList<>();
        for (TimeRange r : sorted) {
            if (!merged.isEmpty() && !r.start().isAfter(merged.getLast().end())) {
                TimeRange last = merged.removeLast();
                LocalTime end = r.end().isAfter(last.end()) ? r.end() : last.end();
                merged.add(new TimeRange(last.start(), end));
            } else {
                merged.add(r);
            }
        }
        return List.copyOf(merged);
    }

    /** State of a room at {@code time} on {@code date}, given that day's bookings (any order). */
    public RoomState stateAt(LocalDate date, LocalTime time, Collection<TimeRange> bookings) {
        if (!withinHours(time)) {
            return new RoomState.OutsideHours();
        }
        for (TimeRange b : merge(bookings)) {
            if (b.contains(time)) {
                return new RoomState.Busy(b.end());
            }
            if (b.start().isAfter(time)) {
                LocalTime until = b.start().isBefore(close) ? b.start() : close;
                RoomState.Until reason = b.start().isBefore(close) ? RoomState.Until.NEXT_BOOKING : RoomState.Until.CLOSING;
                return new RoomState.Free(until, reason, minutesBetween(date, time, until));
            }
        }
        return new RoomState.Free(close, RoomState.Until.CLOSING, minutesBetween(date, time, close));
    }

    /** The gaps between bookings, clipped to opening hours. */
    public List<TimeRange> freeRanges(Collection<TimeRange> bookings) {
        List<TimeRange> free = new ArrayList<>();
        LocalTime cursor = open;
        for (TimeRange b : merge(bookings)) {
            if (!b.end().isAfter(cursor)) {
                continue; // entirely before the cursor (e.g. a 06:00-07:00 class)
            }
            if (!b.start().isBefore(close)) {
                break; // starts after closing
            }
            if (b.start().isAfter(cursor)) {
                free.add(new TimeRange(cursor, b.start()));
            }
            cursor = b.end();
            if (!cursor.isBefore(close)) {
                return List.copyOf(free);
            }
        }
        if (cursor.isBefore(close)) {
            free.add(new TimeRange(cursor, close));
        }
        return List.copyOf(free);
    }

    /**
     * Real elapsed whole minutes between two wall-clock times on {@code date} in the campus zone.
     * On DST-change days this differs from simple subtraction. Partial minutes are dropped
     * (9:45:30 to 12:00 is 134 minutes), so we never overstate how long a room is free.
     */
    public long minutesBetween(LocalDate date, LocalTime from, LocalTime to) {
        ZonedDateTime a = ZonedDateTime.of(date, from, zone);
        ZonedDateTime b = ZonedDateTime.of(date, to, zone);
        return Duration.between(a, b).toMinutes();
    }
}
