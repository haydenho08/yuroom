package dev.yuroom.availability.core;

import java.time.LocalTime;
import java.util.Objects;

/** A half-open wall-clock interval [start, end) on a single day. */
public record TimeRange(LocalTime start, LocalTime end) implements Comparable<TimeRange> {

    public TimeRange {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("range must end after it starts: " + start + "-" + end);
        }
    }

    public static TimeRange of(String start, String end) {
        return new TimeRange(LocalTime.parse(start), LocalTime.parse(end));
    }

    /** Half-open: a class 10:00-11:00 contains 10:00 but not 11:00. */
    public boolean contains(LocalTime t) {
        return !t.isBefore(start) && t.isBefore(end);
    }

    @Override
    public int compareTo(TimeRange o) {
        int c = start.compareTo(o.start);
        return c != 0 ? c : end.compareTo(o.end);
    }
}
