package dev.yuroom.availability.core;

import java.time.LocalDate;

/** A term's teaching dates, inclusive. Plain data copied from the database. */
public record TermSpan(String code, LocalDate start, LocalDate end) {

    public boolean covers(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
