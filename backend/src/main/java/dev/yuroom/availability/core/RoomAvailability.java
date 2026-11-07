package dev.yuroom.availability.core;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Pure orchestration: given what the calendar says about a day, the opening hours, and each
 * room's bookings, answer the two product questions. Also decides the user-facing notice, so
 * every "the timetable doesn't apply" message comes from one tested place.
 */
public final class RoomAvailability {

    /** Overall answer for a moment in time. */
    public enum Status { OK, NO_CLASSES, OUTSIDE_HOURS, CLOSED, EXAMS, OUTSIDE_TERM }

    public record RoomRef(long id, String building, String number) {
    }

    public record FreeRoom(RoomRef room, LocalTime until, RoomState.Until reason, long minutes) {
    }

    public record FreeRoomsResult(Status status, DayContext day, String notice, List<FreeRoom> rooms) {
    }

    public record DaySchedule(Status status, DayContext day, String notice, List<TimeRange> booked,
                              List<TimeRange> free) {
    }

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final Comparator<FreeRoom> LONGEST_FIRST = Comparator
            .comparingLong(FreeRoom::minutes).reversed()
            .thenComparing(f -> f.room().building())
            .thenComparing(f -> f.room().number());

    private final FreeTimeCalculator calc;

    public RoomAvailability(FreeTimeCalculator calc) {
        this.calc = Objects.requireNonNull(calc);
    }

    public FreeTimeCalculator calculator() {
        return calc;
    }

    /**
     * @param bookings bookings for {@code day.effectiveDay()} active on {@code date}, keyed by room id;
     *                 rooms with no entry have no bookings. Ignored unless the timetable applies.
     */
    public FreeRoomsResult freeRooms(LocalDate date, LocalTime time, DayContext day, Collection<RoomRef> rooms,
                                     Map<Long, List<TimeRange>> bookings, int minMinutes) {
        Status status = statusFor(day);
        if (status != Status.OK && status != Status.NO_CLASSES) {
            return new FreeRoomsResult(status, day, notice(status, day, date), List.of());
        }
        if (!calc.withinHours(time)) {
            return new FreeRoomsResult(Status.OUTSIDE_HOURS, day, notice(Status.OUTSIDE_HOURS, day, date), List.of());
        }

        List<FreeRoom> free = new ArrayList<>();
        for (RoomRef room : rooms) {
            List<TimeRange> roomBookings = status == Status.OK ? bookings.getOrDefault(room.id(), List.of()) : List.of();
            if (calc.stateAt(date, time, roomBookings) instanceof RoomState.Free f && f.minutes() >= minMinutes) {
                free.add(new FreeRoom(room, f.until(), f.reason(), f.minutes()));
            }
        }
        free.sort(LONGEST_FIRST);
        return new FreeRoomsResult(status, day, notice(status, day, date), List.copyOf(free));
    }

    /** One room's day. Bookings are shown merged, without any course details. */
    public DaySchedule daySchedule(LocalDate date, DayContext day, List<TimeRange> bookings) {
        Status status = statusFor(day);
        String notice = notice(status, day, date);
        return switch (status) {
            case OK -> new DaySchedule(status, day, notice, FreeTimeCalculator.merge(bookings), calc.freeRanges(bookings));
            case NO_CLASSES -> new DaySchedule(status, day, notice, List.of(), calc.freeRanges(List.of()));
            default -> new DaySchedule(status, day, notice, List.of(), List.of());
        };
    }

    static Status statusFor(DayContext day) {
        return switch (day.status()) {
            case CLASSES -> Status.OK;
            case NO_CLASSES -> Status.NO_CLASSES;
            case CLOSED -> Status.CLOSED;
            case EXAMS -> Status.EXAMS;
            case OUTSIDE_TERM -> Status.OUTSIDE_TERM;
        };
    }

    String notice(Status status, DayContext day, LocalDate date) {
        String hours = HM.format(calc.open()) + "–" + HM.format(calc.close());
        return switch (status) {
            case OK -> day.label() == null ? null
                    : day.label() + ": rooms follow the " + capitalize(day.effectiveDay().name()) + " timetable.";
            case NO_CLASSES -> day.label() + ": no classes are scheduled, so rooms are unbooked all day."
                    + " Buildings may have reduced hours.";
            case OUTSIDE_HOURS -> "Outside usual building hours (" + hours + "). Many buildings are locked.";
            case CLOSED -> day.label() + ": the university is closed.";
            case EXAMS -> day.label() + ": the class timetable doesn't apply during exams, and rooms may be"
                    + " used for exams, so we can't tell which rooms are free.";
            case OUTSIDE_TERM -> "No classes are in session on " + date + ", so there's no timetable to check.";
        };
    }

    private static String capitalize(String upper) {
        return upper.charAt(0) + upper.substring(1).toLowerCase(Locale.ROOT);
    }
}
