package dev.yuroom.availability.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.yuroom.availability.core.RoomState.Until;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FreeTimeCalculatorTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9); // ordinary day, EST
    private final FreeTimeCalculator calc =
            new FreeTimeCalculator(CampusTime.ZONE, LocalTime.of(7, 0), LocalTime.of(23, 0));

    private static TimeRange r(String start, String end) {
        return TimeRange.of(start, end);
    }

    private static LocalTime t(String hm) {
        return LocalTime.parse(hm);
    }

    @Nested
    class Merge {

        @Test
        void emptyStaysEmpty() {
            assertThat(FreeTimeCalculator.merge(List.of())).isEmpty();
        }

        @Test
        void sortsUnorderedInput() {
            assertThat(FreeTimeCalculator.merge(List.of(r("13:00", "14:00"), r("08:30", "10:00"))))
                    .containsExactly(r("08:30", "10:00"), r("13:00", "14:00"));
        }

        @Test
        void mergesOverlapping() {
            assertThat(FreeTimeCalculator.merge(List.of(r("09:00", "11:00"), r("10:00", "12:00"))))
                    .containsExactly(r("09:00", "12:00"));
        }

        @Test
        void mergesTouchingBackToBackClasses() {
            assertThat(FreeTimeCalculator.merge(List.of(r("08:30", "10:00"), r("10:00", "11:30"))))
                    .containsExactly(r("08:30", "11:30"));
        }

        @Test
        void absorbsContainedAndDuplicateRanges() {
            assertThat(FreeTimeCalculator.merge(List.of(
                    r("09:00", "17:00"), r("10:00", "11:00"), r("09:00", "17:00"))))
                    .containsExactly(r("09:00", "17:00"));
        }

        @Test
        void keepsOneMinuteGap() {
            assertThat(FreeTimeCalculator.merge(List.of(r("08:30", "10:00"), r("10:01", "11:00")))).hasSize(2);
        }
    }

    @Nested
    class StateAt {

        private final List<TimeRange> day = List.of(r("08:30", "10:00"), r("10:00", "11:30"), r("13:00", "14:00"));

        @Test
        void beforeFirstClassIsFreeUntilItStarts() {
            assertThat(calc.stateAt(MONDAY, t("07:15"), day))
                    .isEqualTo(new RoomState.Free(t("08:30"), Until.NEXT_BOOKING, 75));
        }

        @Test
        void atTheExactStartOfAClassTheRoomIsBusy() {
            assertThat(calc.stateAt(MONDAY, t("08:30"), day)).isEqualTo(new RoomState.Busy(t("11:30")));
        }

        @Test
        void busyUntilTheEndOfBackToBackClassesNotTheFirstOne() {
            assertThat(calc.stateAt(MONDAY, t("09:59"), day)).isEqualTo(new RoomState.Busy(t("11:30")));
        }

        @Test
        void atTheExactEndOfAClassTheRoomIsFree() {
            assertThat(calc.stateAt(MONDAY, t("11:30"), day))
                    .isEqualTo(new RoomState.Free(t("13:00"), Until.NEXT_BOOKING, 90));
        }

        @Test
        void afterLastClassIsFreeUntilClosing() {
            assertThat(calc.stateAt(MONDAY, t("14:00"), day))
                    .isEqualTo(new RoomState.Free(t("23:00"), Until.CLOSING, 540));
        }

        @Test
        void noBookingsMeansFreeUntilClosing() {
            assertThat(calc.stateAt(MONDAY, t("07:00"), List.of()))
                    .isEqualTo(new RoomState.Free(t("23:00"), Until.CLOSING, 960));
        }

        @Test
        void inputOrderDoesNotMatter() {
            assertThat(calc.stateAt(MONDAY, t("12:00"), day.reversed()))
                    .isEqualTo(calc.stateAt(MONDAY, t("12:00"), day));
        }

        @Test
        void partialMinutesAreDroppedNeverRoundedUp() {
            assertThat(calc.stateAt(MONDAY, LocalTime.of(11, 45, 30), day))
                    .isEqualTo(new RoomState.Free(t("13:00"), Until.NEXT_BOOKING, 74));
        }

        @Test
        void aClassStartingAfterClosingDoesNotExtendTheWindow() {
            assertThat(calc.stateAt(MONDAY, t("22:00"), List.of(r("23:15", "23:45"))))
                    .isEqualTo(new RoomState.Free(t("23:00"), Until.CLOSING, 60));
        }

        @Test
        void aClassStartingExactlyAtClosingCountsAsClosing() {
            assertThat(calc.stateAt(MONDAY, t("22:00"), List.of(r("23:00", "23:30"))))
                    .isEqualTo(new RoomState.Free(t("23:00"), Until.CLOSING, 60));
        }

        @Test
        void earlyClassThatStartedBeforeOpeningStillBlocksTheRoom() {
            assertThat(calc.stateAt(MONDAY, t("07:00"), List.of(r("06:30", "08:00"))))
                    .isEqualTo(new RoomState.Busy(t("08:00")));
        }

        @ParameterizedTest(name = "{0} is outside hours")
        @CsvSource({"00:00", "03:30", "06:59", "23:00", "23:59"})
        void outsideHours(String time) {
            assertThat(calc.stateAt(MONDAY, t(time), List.of())).isInstanceOf(RoomState.OutsideHours.class);
        }

        @Test
        void oneMinuteBeforeClosingIsFreeForOneMinute() {
            assertThat(calc.stateAt(MONDAY, t("22:59"), List.of()))
                    .isEqualTo(new RoomState.Free(t("23:00"), Until.CLOSING, 1));
        }
    }

    @Nested
    class FreeRanges {

        @Test
        void noBookingsIsTheWholeDay() {
            assertThat(calc.freeRanges(List.of())).containsExactly(r("07:00", "23:00"));
        }

        @Test
        void gapsBetweenMergedBookings() {
            assertThat(calc.freeRanges(List.of(r("10:00", "11:30"), r("08:30", "10:00"), r("13:00", "14:00"))))
                    .containsExactly(r("07:00", "08:30"), r("11:30", "13:00"), r("14:00", "23:00"));
        }

        @Test
        void bookingsCrossingOpeningAndClosingAreClipped() {
            assertThat(calc.freeRanges(List.of(r("06:00", "08:00"), r("21:00", "23:30"))))
                    .containsExactly(r("08:00", "21:00"));
        }

        @Test
        void bookingsEntirelyOutsideHoursAreIgnored() {
            assertThat(calc.freeRanges(List.of(r("05:00", "06:30"), r("23:00", "23:59"))))
                    .containsExactly(r("07:00", "23:00"));
        }

        @Test
        void fullyBookedDayHasNoGaps() {
            assertThat(calc.freeRanges(List.of(r("06:00", "23:30")))).isEmpty();
        }
    }

    /**
     * America/Toronto: DST ended 2026-11-01 at 02:00 (clocks back to 01:00) and starts
     * 2027-03-14 at 02:00 (clocks forward to 03:00). Uses all-day hours to cross the change.
     */
    @Nested
    class DaylightSaving {

        private final FreeTimeCalculator allDay =
                new FreeTimeCalculator(CampusTime.ZONE, LocalTime.MIDNIGHT, LocalTime.of(23, 59));

        @Test
        void fallBackNightHasAnExtraHour() {
            assertThat(allDay.minutesBetween(LocalDate.of(2026, 11, 1), t("00:30"), t("03:00"))).isEqualTo(210);
        }

        @Test
        void springForwardNightLosesAnHour() {
            assertThat(allDay.minutesBetween(LocalDate.of(2027, 3, 14), t("01:00"), t("03:00"))).isEqualTo(60);
        }

        @Test
        void ordinaryNightIsPlainSubtraction() {
            assertThat(allDay.minutesBetween(LocalDate.of(2026, 11, 8), t("01:00"), t("03:00"))).isEqualTo(120);
        }

        @Test
        void freeWindowAcrossFallBackReportsRealMinutes() {
            assertThat(allDay.stateAt(LocalDate.of(2026, 11, 1), t("00:30"), List.of(r("03:00", "04:00"))))
                    .isEqualTo(new RoomState.Free(t("03:00"), Until.NEXT_BOOKING, 210));
        }

        @Test
        void daytimeWindowsOnDstDaysAreUnaffected() {
            assertThat(calc.stateAt(LocalDate.of(2026, 11, 1), t("09:00"), List.of(r("10:00", "11:00"))))
                    .isEqualTo(new RoomState.Free(t("10:00"), Until.NEXT_BOOKING, 60));
        }
    }

    @Test
    void rejectsInvertedHours() {
        assertThatThrownBy(() -> new FreeTimeCalculator(CampusTime.ZONE, t("23:00"), t("07:00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timeRangeRejectsEmptyOrBackwardRanges() {
        assertThatThrownBy(() -> r("10:00", "10:00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> r("10:00", "09:00")).isInstanceOf(IllegalArgumentException.class);
    }
}
