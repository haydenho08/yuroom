package dev.yuroom.availability.core;

/** Does the class timetable tell us anything about this date? */
public enum DayStatus {
    /** Normal teaching day: rooms are free unless a class is booked. */
    CLASSES,
    /** Reading week / study day: university open, no classes, so rooms are unbooked all day. */
    NO_CLASSES,
    /** Holiday or closure: university closed. */
    CLOSED,
    /** Exam period: the class timetable doesn't apply and rooms may be used for exams. */
    EXAMS,
    /** Between terms: no timetable in effect. */
    OUTSIDE_TERM
}
