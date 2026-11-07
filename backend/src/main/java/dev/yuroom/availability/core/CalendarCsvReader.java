package dev.yuroom.availability.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the hand-maintained academic calendar file:
 * {@code kind,start_date,end_date,label,follows_day}. Lines starting with '#' are comments.
 * Any mistake fails loudly with a line number: this file decides whether we tell students
 * a room is free, so a typo must stop the app from starting rather than give wrong answers.
 */
public final class CalendarCsvReader {

    private CalendarCsvReader() {
    }

    public static List<CalendarPeriod> read(Reader source) throws IOException {
        List<CalendarPeriod> periods = new ArrayList<>();
        BufferedReader in = new BufferedReader(source);
        String line;
        int lineNo = 0;
        boolean headerSeen = false;
        while ((line = in.readLine()) != null) {
            lineNo++;
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (!headerSeen) {
                if (!trimmed.equals("kind,start_date,end_date,label,follows_day")) {
                    throw new IllegalArgumentException("calendar line " + lineNo + ": unexpected header '" + trimmed + "'");
                }
                headerSeen = true;
                continue;
            }
            String[] f = trimmed.split(",", -1);
            if (f.length != 5) {
                throw new IllegalArgumentException("calendar line " + lineNo + ": expected 5 fields, got " + f.length);
            }
            try {
                CalendarPeriod.Kind kind = CalendarPeriod.Kind.valueOf(f[0].strip().toUpperCase(Locale.ROOT));
                DayOfWeek follows = f[4].isBlank() ? null : DayOfWeek.valueOf(f[4].strip().toUpperCase(Locale.ROOT));
                periods.add(new CalendarPeriod(kind, LocalDate.parse(f[1].strip()), LocalDate.parse(f[2].strip()),
                        f[3].strip(), follows));
            } catch (IllegalArgumentException | DateTimeException e) {
                throw new IllegalArgumentException("calendar line " + lineNo + ": " + e.getMessage(), e);
            }
        }
        if (!headerSeen) {
            throw new IllegalArgumentException("calendar file is empty");
        }
        return List.copyOf(periods);
    }
}
