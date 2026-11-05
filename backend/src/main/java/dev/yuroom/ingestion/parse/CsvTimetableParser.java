package dev.yuroom.ingestion.parse;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Parses the YURoom timetable CSV format (documented in docs/timetable-csv-format.md).
 *
 * <p>Pure Java: no Spring, no database, so it is tested directly against fixture files.
 * It is deliberately forgiving about presentation (case, whitespace, BOM, "8:30" vs "08:30",
 * "TR" vs one row per day, end time vs duration) and strict about meaning (unknown days,
 * end before start, meetings crossing midnight are rejected with a line number).
 */
public final class CsvTimetableParser implements TimetableParser {

    static final List<String> REQUIRED_COLUMNS =
            List.of("term_code", "building", "room", "day", "start_time", "start_date", "end_date");

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm[:ss]", Locale.ROOT);
    private static final Set<String> NO_ROOM = Set.of("", "ONLINE", "TBA", "N/A", "REMOTE");

    private final Charset charset;

    public CsvTimetableParser(Charset charset) {
        this.charset = Objects.requireNonNull(charset);
    }

    @Override
    public ParseResult parse(byte[] content) {
        String text = new String(content, charset);
        if (text.startsWith("﻿")) { // Excel likes to prepend a byte-order mark
            text = text.substring(1);
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true)
                .setCommentMarker('#')
                .get();

        try (CSVParser csv = CSVParser.parse(new StringReader(text), format)) {
            Map<String, Integer> header = normalizeHeader(csv.getHeaderMap());
            checkColumns(header);

            List<ParsedMeeting> meetings = new ArrayList<>();
            List<ParseResult.RowError> errors = new ArrayList<>();
            int rows = 0;
            int skipped = 0;

            for (CSVRecord record : csv) {
                rows++;
                Row row = new Row(record, header);
                try {
                    if (NO_ROOM.contains(row.upper("building")) || NO_ROOM.contains(row.upper("room"))) {
                        skipped++;
                        continue;
                    }
                    meetings.addAll(toMeetings(row));
                } catch (IllegalArgumentException | DateTimeException e) {
                    errors.add(new ParseResult.RowError(csv.getCurrentLineNumber(), e.getMessage()));
                }
            }
            return new ParseResult(meetings, errors, rows, skipped);
        } catch (IOException | UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
            // Commons CSV signals structural problems (bad quoting, duplicate headers) this way.
            throw new TimetableParseException("unreadable CSV: " + e.getMessage(), e);
        }
    }

    private static List<ParsedMeeting> toMeetings(Row row) {
        String term = row.required("term_code").toUpperCase(Locale.ROOT);
        String building = row.required("building").toUpperCase(Locale.ROOT);
        String room = row.required("room").toUpperCase(Locale.ROOT);
        Set<DayOfWeek> days = parseDays(row.required("day"));
        LocalTime start = parseTime(row.required("start_time"), "start_time");
        LocalTime end = endTime(row, start);
        LocalDate startDate = parseDate(row.required("start_date"), "start_date");
        LocalDate endDate = parseDate(row.required("end_date"), "end_date");

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end time " + end + " is not after start time " + start);
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("end_date " + endDate + " is before start_date " + startDate);
        }

        List<ParsedMeeting> result = new ArrayList<>(days.size());
        for (DayOfWeek day : days) {
            result.add(new ParsedMeeting(term, building, row.optional("campus"), room, day, start, end,
                    startDate, endDate, row.optional("course_code"), row.optional("section"),
                    upperOrNull(row.optional("meeting_type"))));
        }
        return result;
    }

    /** Either an explicit end_time or a duration_minutes (York's timetable uses durations). */
    private static LocalTime endTime(Row row, LocalTime start) {
        String endText = row.optional("end_time");
        if (endText != null) {
            return parseTime(endText, "end_time");
        }
        String durationText = row.optional("duration_minutes");
        if (durationText == null) {
            throw new IllegalArgumentException("row needs end_time or duration_minutes");
        }
        int minutes;
        try {
            minutes = Integer.parseInt(durationText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("duration_minutes '" + durationText + "' is not a whole number");
        }
        if (minutes <= 0) {
            throw new IllegalArgumentException("duration_minutes must be positive, got " + minutes);
        }
        int endMinuteOfDay = start.getHour() * 60 + start.getMinute() + minutes;
        if (endMinuteOfDay > 24 * 60 - 1) {
            throw new IllegalArgumentException("meeting starting " + start + " for " + minutes
                    + " min crosses midnight");
        }
        return start.plusMinutes(minutes);
    }

    /**
     * Accepts full or short names ("MON", "Thursday") or York-style letter codes, where a run of
     * letters means several days: "MWF", "TR". Letters: M T W R(Thu) F S(Sat) U(Sun).
     */
    static Set<DayOfWeek> parseDays(String text) {
        String s = text.trim().toUpperCase(Locale.ROOT);
        DayOfWeek named = switch (s) {
            case "MON", "MONDAY" -> DayOfWeek.MONDAY;
            case "TUE", "TUES", "TUESDAY" -> DayOfWeek.TUESDAY;
            case "WED", "WEDNESDAY" -> DayOfWeek.WEDNESDAY;
            case "THU", "THUR", "THURS", "THURSDAY" -> DayOfWeek.THURSDAY;
            case "FRI", "FRIDAY" -> DayOfWeek.FRIDAY;
            case "SAT", "SATURDAY" -> DayOfWeek.SATURDAY;
            case "SUN", "SUNDAY" -> DayOfWeek.SUNDAY;
            default -> null;
        };
        if (named != null) {
            return EnumSet.of(named);
        }
        if (s.isEmpty()) {
            throw new IllegalArgumentException("day is empty");
        }
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (char c : s.toCharArray()) {
            DayOfWeek d = switch (c) {
                case 'M' -> DayOfWeek.MONDAY;
                case 'T' -> DayOfWeek.TUESDAY;
                case 'W' -> DayOfWeek.WEDNESDAY;
                case 'R' -> DayOfWeek.THURSDAY;
                case 'F' -> DayOfWeek.FRIDAY;
                case 'S' -> DayOfWeek.SATURDAY;
                case 'U' -> DayOfWeek.SUNDAY;
                default -> throw new IllegalArgumentException("unknown day '" + text + "'");
            };
            days.add(d);
        }
        return days;
    }

    private static LocalTime parseTime(String text, String column) {
        try {
            return LocalTime.parse(text, TIME);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(column + " '" + text + "' is not a time like 8:30 or 14:00");
        }
    }

    private static LocalDate parseDate(String text, String column) {
        try {
            return LocalDate.parse(text); // ISO-8601, yyyy-MM-dd
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(column + " '" + text + "' is not a date like 2026-09-09");
        }
    }

    private static Map<String, Integer> normalizeHeader(Map<String, Integer> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new TimetableParseException("file has no header row");
        }
        return raw.entrySet().stream().collect(Collectors.toMap(
                e -> e.getKey().trim().toLowerCase(Locale.ROOT), Map.Entry::getValue, (a, b) -> a));
    }

    private static void checkColumns(Map<String, Integer> header) {
        List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !header.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw new TimetableParseException("missing required column(s): " + String.join(", ", missing));
        }
        if (!header.containsKey("end_time") && !header.containsKey("duration_minutes")) {
            throw new TimetableParseException("need an end_time or duration_minutes column");
        }
    }

    private static String upperOrNull(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }

    /** Column access by (normalized) header name, tolerant of short rows. */
    private record Row(CSVRecord record, Map<String, Integer> header) {

        String optional(String column) {
            Integer index = header.get(column);
            if (index == null || index >= record.size()) {
                return null;
            }
            String value = record.get(index).trim();
            return value.isEmpty() ? null : value;
        }

        String required(String column) {
            String value = optional(column);
            if (value == null) {
                throw new IllegalArgumentException(column + " is empty");
            }
            return value;
        }

        String upper(String column) {
            String value = optional(column);
            return value == null ? "" : value.toUpperCase(Locale.ROOT);
        }
    }
}
