package dev.yuroom.ingestion.parse;

import static dev.yuroom.testing.Fixtures.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Runs against saved fixture files in src/test/resources/fixtures — never the live site. */
class CsvTimetableParserTest {

    private final CsvTimetableParser parser = new CsvTimetableParser(StandardCharsets.UTF_8);

    @Nested
    class ValidFile {

        private final ParseResult result = parser.parse(fixture("valid.csv"));

        @Test
        void countsRowsMeetingsAndSkips() {
            assertThat(result.rowsRead()).isEqualTo(6);
            assertThat(result.rowsSkipped()).as("ONLINE row has no room").isEqualTo(1);
            assertThat(result.errors()).isEmpty();
            assertThat(result.meetings()).as("MW and TR expand to two meetings each").hasSize(7);
        }

        @Test
        void expandsMultiDayCodesAndComputesEndFromDuration() {
            List<ParsedMeeting> mw = result.meetings().stream()
                    .filter(m -> "DEMO 1001".equals(m.courseCode())).toList();
            assertThat(mw).extracting(ParsedMeeting::day)
                    .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
            assertThat(mw).allSatisfy(m -> {
                assertThat(m.start()).isEqualTo(LocalTime.of(8, 30));
                assertThat(m.end()).isEqualTo(LocalTime.of(10, 0));
            });
        }

        @Test
        void normalizesCaseAndKeepsOptionalFieldsNullable() {
            ParsedMeeting first = result.meetings().getFirst();
            assertThat(first.buildingCode()).isEqualTo("LAS");
            assertThat(first.roomNumber()).isEqualTo("A");
            assertThat(first.meetingType()).isEqualTo("LECT");

            ParsedMeeting friday = result.meetings().stream()
                    .filter(m -> m.day() == DayOfWeek.FRIDAY).findFirst().orElseThrow();
            assertThat(friday.campus()).isNull();
            assertThat(friday.courseCode()).isNull();
            assertThat(friday.start()).isEqualTo(LocalTime.of(7, 0));
            assertThat(friday.startDate()).isEqualTo(LocalDate.of(2026, 9, 11));
        }

        @Test
        void handlesQuotedFieldsWithCommasAndQuotes() {
            assertThat(result.meetings()).extracting(ParsedMeeting::courseCode).contains("DEMO, \"quoted\"");
        }
    }

    @Test
    void reportsEachBadRowWithItsLineNumberAndKeepsTheGoodOnes() {
        ParseResult result = parser.parse(fixture("with-errors.csv"));

        assertThat(result.rowsRead()).isEqualTo(11);
        assertThat(result.meetings()).hasSize(2);
        assertThat(result.errors()).hasSize(9);
        assertThat(result.errors()).extracting(ParseResult.RowError::line)
                .containsExactly(3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
        assertThat(result.errors()).extracting(ParseResult.RowError::reason).containsExactly(
                "unknown day 'X'",
                "start_time '25:00' is not a time like 8:30 or 14:00",
                "end time 09:00 is not after start time 10:00",
                "meeting starting 23:00 for 120 min crosses midnight",
                "row needs end_time or duration_minutes",
                "duration_minutes must be positive, got -5",
                "end_date 2026-09-09 is before start_date 2026-12-08",
                "start_date '09/09/2026' is not a date like 2026-09-09",
                "term_code is empty");
        assertThat(result.rejectRatio()).isEqualTo(9.0 / 11);
    }

    @Test
    void rejectsFileMissingRequiredColumns() {
        assertThatThrownBy(() -> parser.parse(fixture("missing-columns.csv")))
                .isInstanceOf(TimetableParseException.class)
                .hasMessage("missing required column(s): room, start_date, end_date");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> parser.parse(new byte[0]))
                .isInstanceOf(TimetableParseException.class)
                .hasMessageContaining("no header");
    }

    @Test
    void stripsUtf8ByteOrderMark() {
        byte[] withBom = ("﻿" + "term_code,building,room,day,start_time,end_time,start_date,end_date\n"
                + "2026FA,LAS,A,M,9:00,10:00,2026-09-09,2026-12-08\n").getBytes(StandardCharsets.UTF_8);
        ParseResult result = parser.parse(withBom);
        assertThat(result.errors()).isEmpty();
        assertThat(result.meetings()).hasSize(1);
    }

    @Test
    void decodesLegacyWindows1252WhenConfigured() {
        // York's timetable is reportedly served in a legacy encoding; the charset is configurable.
        Charset cp1252 = Charset.forName("windows-1252");
        byte[] legacy = ("term_code,building,room,day,start_time,end_time,start_date,end_date,course_code\n"
                + "2026FA,LAS,A,M,9:00,10:00,2026-09-09,2026-12-08,FRAN 1000 Français\n").getBytes(cp1252);

        ParsedMeeting m = new CsvTimetableParser(cp1252).parse(legacy).meetings().getFirst();
        assertThat(m.courseCode()).isEqualTo("FRAN 1000 Français");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "M, MONDAY", "R, THURSDAY", "S, SATURDAY", "U, SUNDAY",
            "tue, TUESDAY", "Thurs, THURSDAY", "SUN, SUNDAY", "' w ', WEDNESDAY"
    })
    void parsesSingleDays(String text, DayOfWeek expected) {
        assertThat(CsvTimetableParser.parseDays(text)).containsExactly(expected);
    }

    @Test
    void parsesYorkStyleMultiDayCodes() {
        assertThat(CsvTimetableParser.parseDays("MWF"))
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        assertThat(CsvTimetableParser.parseDays("TR")).containsExactly(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
    }

}
