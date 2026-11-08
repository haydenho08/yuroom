package dev.yuroom.usage;

import dev.yuroom.usage.UsageCounterRepository.DailyCount;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure roll-up of daily counters into weeks (Monday start, campus dates). No Spring, no clock. */
public final class WeeklyUsage {

    /**
     * One week of numbers.
     *
     * @param weeklyVisitors   browsers that opened the app at least once this week ("WAU", see caveats)
     * @param avgDailyVisitors daily visitors averaged over the days of the week so far
     * @param complete         false for the current, still-running week
     */
    public record Week(LocalDate weekStart, long visits, long weeklyVisitors, double avgDailyVisitors,
                       long nearMeUses, long reports, boolean complete) {
    }

    private WeeklyUsage() {
    }

    public static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** The {@code weeks} most recent weeks up to and including {@code today}'s, oldest first, gaps as zeros. */
    public static List<Week> summarize(List<DailyCount> counts, LocalDate today, int weeks) {
        LocalDate currentWeek = weekStart(today);
        LocalDate firstWeek = currentWeek.minusWeeks(weeks - 1L);

        Map<LocalDate, Map<UsageEvent, Long>> totals = new TreeMap<>();
        for (LocalDate w = firstWeek; !w.isAfter(currentWeek); w = w.plusWeeks(1)) {
            totals.put(w, new EnumMap<>(UsageEvent.class));
        }
        for (DailyCount c : counts) {
            Map<UsageEvent, Long> week = totals.get(weekStart(c.day()));
            if (week != null && !c.day().isAfter(today)) {
                week.merge(c.event(), c.count(), Long::sum);
            }
        }

        List<Week> out = new ArrayList<>();
        totals.forEach((start, t) -> {
            boolean complete = start.isBefore(currentWeek);
            long daysSoFar = complete ? 7 : ChronoUnit.DAYS.between(start, today) + 1;
            double avgDaily = Math.round(t.getOrDefault(UsageEvent.DAILY_VISITOR, 0L) * 10.0 / daysSoFar) / 10.0;
            out.add(new Week(start,
                    t.getOrDefault(UsageEvent.VISIT, 0L),
                    t.getOrDefault(UsageEvent.WEEKLY_VISITOR, 0L),
                    avgDaily,
                    t.getOrDefault(UsageEvent.NEAR_ME, 0L),
                    t.getOrDefault(UsageEvent.REPORT, 0L),
                    complete));
        });
        return out;
    }
}
