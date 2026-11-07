package dev.yuroom.availability.core;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Classifies a date using the official calendar plus the terms we have timetable data for.
 *
 * <p>Precedence (first match wins): closure → exams → no-classes → make-up day → term in session
 * → outside term. So Thanksgiving inside Fall Reading Week is CLOSED, and an exam day after the
 * last class (outside the term's teaching dates) is still EXAMS, not "outside term".
 */
public final class AcademicCalendar {

    private final List<CalendarPeriod> periods;

    public AcademicCalendar(Collection<CalendarPeriod> periods) {
        this.periods = periods.stream()
                .sorted(Comparator.comparing(CalendarPeriod::kind).thenComparing(CalendarPeriod::start))
                .toList();
    }

    public List<CalendarPeriod> periods() {
        return periods;
    }

    public DayContext classify(LocalDate date, Collection<TermSpan> terms) {
        String termCode = terms.stream().filter(t -> t.covers(date))
                .max(Comparator.comparing(TermSpan::start)) // latest-starting term if they overlap
                .map(TermSpan::code).orElse(null);

        Optional<CalendarPeriod> period = periods.stream().filter(p -> p.covers(date)).findFirst();
        if (period.isPresent()) {
            CalendarPeriod p = period.get();
            return switch (p.kind()) {
                case CLOSED -> new DayContext(DayStatus.CLOSED, date.getDayOfWeek(), termCode, p.label());
                case EXAMS -> new DayContext(DayStatus.EXAMS, date.getDayOfWeek(), termCode, p.label());
                case NO_CLASSES -> new DayContext(DayStatus.NO_CLASSES, date.getDayOfWeek(), termCode, p.label());
                case MAKEUP_DAY -> termCode == null
                        ? new DayContext(DayStatus.OUTSIDE_TERM, date.getDayOfWeek(), null, null)
                        : new DayContext(DayStatus.CLASSES, p.followsDay(), termCode, p.label());
            };
        }
        if (termCode == null) {
            return new DayContext(DayStatus.OUTSIDE_TERM, date.getDayOfWeek(), null, null);
        }
        return new DayContext(DayStatus.CLASSES, date.getDayOfWeek(), termCode, null);
    }
}
