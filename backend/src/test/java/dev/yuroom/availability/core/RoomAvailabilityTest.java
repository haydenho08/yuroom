package dev.yuroom.availability.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.yuroom.availability.core.RoomAvailability.FreeRoom;
import dev.yuroom.availability.core.RoomAvailability.RoomRef;
import dev.yuroom.availability.core.RoomAvailability.Status;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomAvailabilityTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 9);
    private static final RoomRef LAS_A = new RoomRef(1, "LAS", "A");
    private static final RoomRef LAS_B = new RoomRef(2, "LAS", "B");
    private static final RoomRef CLH_A = new RoomRef(3, "CLH", "A");
    private static final RoomRef ACW_1 = new RoomRef(4, "ACW", "109");
    private static final List<RoomRef> ROOMS = List.of(LAS_A, LAS_B, CLH_A, ACW_1);
    private static final Map<Long, List<TimeRange>> BOOKINGS = Map.of(
            1L, List.of(TimeRange.of("08:30", "11:30")),
            2L, List.of(TimeRange.of("13:00", "14:00")),
            3L, List.of(TimeRange.of("10:00", "11:00")),
            4L, List.of(TimeRange.of("13:00", "14:00")));

    private final RoomAvailability availability = new RoomAvailability(
            new FreeTimeCalculator(CampusTime.ZONE, LocalTime.of(7, 0), LocalTime.of(23, 0)));

    private static DayContext day(DayStatus status, String label) {
        return new DayContext(status, DayOfWeek.MONDAY, status == DayStatus.CLASSES ? "2026FA" : null, label);
    }

    private RoomAvailability.FreeRoomsResult at(String time, DayContext day, int minMinutes) {
        return availability.freeRooms(DATE, LocalTime.parse(time), day, ROOMS, BOOKINGS, minMinutes);
    }

    @Test
    void listsOnlyFreeRoomsLongestFirstWithNameAsTieBreak() {
        var result = at("09:00", day(DayStatus.CLASSES, null), 0);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.notice()).isNull();
        assertThat(result.rooms()).extracting(FreeRoom::room).containsExactly(ACW_1, LAS_B, CLH_A);
        assertThat(result.rooms()).extracting(FreeRoom::minutes).containsExactly(240L, 240L, 60L);
    }

    @Test
    void minMinutesFiltersShortWindows() {
        assertThat(at("09:00", day(DayStatus.CLASSES, null), 61).rooms())
                .extracting(FreeRoom::room).containsExactly(ACW_1, LAS_B);
        assertThat(at("09:00", day(DayStatus.CLASSES, null), 60).rooms()).hasSize(3);
    }

    @Test
    void noClassesMeansEveryRoomIsFreeUntilClosing() {
        var result = at("09:00", day(DayStatus.NO_CLASSES, "Fall Reading Week"), 0);

        assertThat(result.status()).isEqualTo(Status.NO_CLASSES);
        assertThat(result.rooms()).hasSize(4).allSatisfy(f -> {
            assertThat(f.until()).isEqualTo(LocalTime.of(23, 0));
            assertThat(f.reason()).isEqualTo(RoomState.Until.CLOSING);
        });
        assertThat(result.notice()).startsWith("Fall Reading Week: no classes");
    }

    @Test
    void examsClosuresAndOutsideTermReturnNoRoomsAndExplainWhy() {
        var exams = at("09:00", day(DayStatus.EXAMS, "Fall examinations"), 0);
        assertThat(exams.status()).isEqualTo(Status.EXAMS);
        assertThat(exams.rooms()).isEmpty();
        assertThat(exams.notice()).contains("doesn't apply during exams");

        var closed = at("09:00", day(DayStatus.CLOSED, "Thanksgiving Day"), 0);
        assertThat(closed.status()).isEqualTo(Status.CLOSED);
        assertThat(closed.notice()).isEqualTo("Thanksgiving Day: the university is closed.");

        var summer = at("09:00", day(DayStatus.OUTSIDE_TERM, null), 0);
        assertThat(summer.status()).isEqualTo(Status.OUTSIDE_TERM);
        assertThat(summer.notice()).contains("2026-11-09");
    }

    @Test
    void lateNightAndEarlyMorningAreOutsideHours() {
        var night = at("23:30", day(DayStatus.CLASSES, null), 0);
        assertThat(night.status()).isEqualTo(Status.OUTSIDE_HOURS);
        assertThat(night.rooms()).isEmpty();
        assertThat(night.notice()).contains("07:00–23:00");

        assertThat(at("06:45", day(DayStatus.CLASSES, null), 0).status()).isEqualTo(Status.OUTSIDE_HOURS);
        assertThat(at("06:45", day(DayStatus.NO_CLASSES, "Reading Week"), 0).status()).isEqualTo(Status.OUTSIDE_HOURS);
    }

    @Test
    void closureBeatsOutsideHoursSoTheMoreUsefulReasonIsShown() {
        assertThat(at("02:00", day(DayStatus.CLOSED, "Winter break"), 0).status()).isEqualTo(Status.CLOSED);
    }

    @Test
    void makeUpDayExplainsWhichTimetableApplies() {
        DayContext makeup = new DayContext(DayStatus.CLASSES, DayOfWeek.FRIDAY, "2027WI", "Make-up day (Friday timetable)");
        assertThat(availability.freeRooms(DATE, LocalTime.of(9, 0), makeup, ROOMS, BOOKINGS, 0).notice())
                .isEqualTo("Make-up day (Friday timetable): rooms follow the Friday timetable.");
    }

    @Test
    void dayScheduleShowsMergedBookingsAndGaps() {
        var schedule = availability.daySchedule(DATE, day(DayStatus.CLASSES, null),
                List.of(TimeRange.of("10:00", "11:30"), TimeRange.of("08:30", "10:00")));

        assertThat(schedule.booked()).containsExactly(TimeRange.of("08:30", "11:30"));
        assertThat(schedule.free()).containsExactly(TimeRange.of("07:00", "08:30"), TimeRange.of("11:30", "23:00"));
    }

    @Test
    void dayScheduleDuringExamsHasNoBookingsAndNoFreeTime() {
        var schedule = availability.daySchedule(DATE, day(DayStatus.EXAMS, "Fall examinations"),
                List.of(TimeRange.of("10:00", "11:30")));
        assertThat(schedule.booked()).isEmpty();
        assertThat(schedule.free()).isEmpty();
        assertThat(schedule.status()).isEqualTo(Status.EXAMS);
    }

    @Test
    void dayScheduleDuringReadingWeekIsAllFree() {
        var schedule = availability.daySchedule(DATE, day(DayStatus.NO_CLASSES, "Fall Reading Week"), List.of());
        assertThat(schedule.free()).containsExactly(TimeRange.of("07:00", "23:00"));
    }
}
