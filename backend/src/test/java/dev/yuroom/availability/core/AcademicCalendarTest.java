package dev.yuroom.availability.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Uses the real bundled 2026-27 calendar file, so these tests also check that file. */
class AcademicCalendarTest {

    private static final List<TermSpan> TERMS = List.of(
            new TermSpan("2026FA", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 12, 8)),
            new TermSpan("2027WI", LocalDate.of(2027, 1, 4), LocalDate.of(2027, 4, 5)));

    private static final AcademicCalendar CALENDAR = new AcademicCalendar(bundledPeriods());

    static List<CalendarPeriod> bundledPeriods() {
        try (Reader in = new InputStreamReader(
                AcademicCalendarTest.class.getResourceAsStream("/calendar/academic-calendar.csv"), StandardCharsets.UTF_8)) {
            return CalendarCsvReader.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static DayContext on(String date) {
        return CALENDAR.classify(LocalDate.parse(date), TERMS);
    }

    @Test
    void bundledFileHasAllRegistrarRows() {
        assertThat(CALENDAR.periods()).hasSize(12);
    }

    @ParameterizedTest(name = "{0} -> {1} ({2})")
    @CsvSource(delimiter = '|', textBlock = """
            2026-11-09 | CLASSES      | 2026FA | ordinary Monday
            2026-09-09 | CLASSES      | 2026FA | first day of term (inclusive)
            2026-12-08 | CLASSES      | 2026FA | last day of classes (inclusive)
            2026-11-07 | CLASSES      | 2026FA | Saturday in term: weekend classes exist
            2026-10-14 | NO_CLASSES   | 2026FA | Fall Reading Week
            2026-10-12 | CLOSED       | 2026FA | Thanksgiving beats Reading Week
            2026-12-09 | NO_CLASSES   |        | study day after the last class
            2026-12-15 | EXAMS        |        | exam day after term teaching dates
            2026-12-28 | CLOSED       |        | winter break
            2026-09-07 | CLOSED       |        | Labour Day, before term
            2026-08-15 | OUTSIDE_TERM |        | summer, no data
            2027-02-15 | CLOSED       | 2027WI | Family Day inside Winter Reading Week
            2027-02-17 | NO_CLASSES   | 2027WI | Winter Reading Week
            2027-03-26 | CLOSED       | 2027WI | Good Friday
            2027-04-20 | EXAMS        |        | winter exams
            """)
    void classifiesTheYear(String date, DayStatus expected, String term, String why) {
        DayContext day = on(date);
        assertThat(day.status()).as(why).isEqualTo(expected);
        assertThat(day.termCode()).as(why).isEqualTo(term);
    }

    @Test
    void makeUpDayRunsTheFridayTimetableOnAMonday() {
        DayContext day = on("2027-04-05");
        assertThat(LocalDate.of(2027, 4, 5).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(day.status()).isEqualTo(DayStatus.CLASSES);
        assertThat(day.effectiveDay()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(day.label()).contains("Make-up day");
    }

    @Test
    void ordinaryDaysUseTheirOwnWeekday() {
        assertThat(on("2026-11-12").effectiveDay()).isEqualTo(DayOfWeek.THURSDAY);
        assertThat(on("2026-11-12").label()).isNull();
    }

    @Test
    void makeUpDayWithoutTimetableDataIsOutsideTerm() {
        DayContext day = new AcademicCalendar(bundledPeriods()).classify(LocalDate.of(2027, 4, 5), List.of());
        assertThat(day.status()).isEqualTo(DayStatus.OUTSIDE_TERM);
    }

    @Test
    void readerReportsLineNumbersForMistakes() {
        String bad = """
                kind,start_date,end_date,label,follows_day
                CLOSED,2026-09-07,2026-09-07,Labour Day,
                HOLIDAYS,2026-10-12,2026-10-12,Thanksgiving,
                """;
        assertThatThrownBy(() -> CalendarCsvReader.read(new StringReader(bad)))
                .hasMessageStartingWith("calendar line 3:");
    }

    @Test
    void readerRejectsMakeUpDayWithoutFollowsDay() {
        String bad = "kind,start_date,end_date,label,follows_day\nMAKEUP_DAY,2027-04-05,2027-04-05,Make-up,\n";
        assertThatThrownBy(() -> CalendarCsvReader.read(new StringReader(bad)))
                .hasMessageContaining("follows_day is required");
    }

    @Test
    void readerRejectsBackwardsDatesAndBadHeader() {
        assertThatThrownBy(() -> CalendarCsvReader.read(new StringReader(
                "kind,start_date,end_date,label,follows_day\nCLOSED,2026-10-12,2026-10-01,Oops,\n")))
                .hasMessageContaining("before start");
        assertThatThrownBy(() -> CalendarCsvReader.read(new StringReader("kind,start,end\n")))
                .hasMessageContaining("unexpected header");
        assertThatThrownBy(() -> CalendarCsvReader.read(new StringReader("# only a comment\n")))
                .hasMessageContaining("empty");
    }
}
