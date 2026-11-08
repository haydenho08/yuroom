package dev.yuroom.usage;

import static org.assertj.core.api.Assertions.assertThat;

import dev.yuroom.usage.UsageCounterRepository.DailyCount;
import dev.yuroom.usage.WeeklyUsage.Week;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyUsageTest {

    static final LocalDate WED_NOV_11 = LocalDate.of(2026, 11, 11);

    static DailyCount c(String day, UsageEvent e, long n) {
        return new DailyCount(LocalDate.parse(day), e, n);
    }

    @Test
    void weeksStartOnMonday() {
        assertThat(WeeklyUsage.weekStart(LocalDate.of(2026, 11, 9))).isEqualTo("2026-11-09"); // Monday
        assertThat(WeeklyUsage.weekStart(LocalDate.of(2026, 11, 15))).isEqualTo("2026-11-09"); // Sunday
        assertThat(WeeklyUsage.weekStart(LocalDate.of(2026, 11, 16))).isEqualTo("2026-11-16");
    }

    @Test
    void rollsDaysIntoWeeksWithGapsAsZero() {
        List<Week> weeks = WeeklyUsage.summarize(List.of(
                c("2026-10-26", UsageEvent.VISIT, 5),
                c("2026-11-01", UsageEvent.VISIT, 2),          // Sunday, same week as Oct 26
                c("2026-11-01", UsageEvent.WEEKLY_VISITOR, 3),
                c("2026-11-10", UsageEvent.VISIT, 10),
                c("2026-11-10", UsageEvent.REPORT, 1),
                c("2026-11-11", UsageEvent.NEAR_ME, 4)), WED_NOV_11, 3);

        assertThat(weeks).extracting(Week::weekStart).containsExactly(
                LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9));
        assertThat(weeks.get(0).visits()).isEqualTo(7);
        assertThat(weeks.get(0).weeklyVisitors()).isEqualTo(3);
        assertThat(weeks.get(1).visits()).isZero();
        assertThat(weeks.get(2)).extracting(Week::visits, Week::reports, Week::nearMeUses).containsExactly(10L, 1L, 4L);
    }

    @Test
    void averagesDailyVisitorsOverDaysSoFarInTheCurrentWeek() {
        List<Week> weeks = WeeklyUsage.summarize(List.of(
                c("2026-11-02", UsageEvent.DAILY_VISITOR, 70),
                c("2026-11-09", UsageEvent.DAILY_VISITOR, 20),
                c("2026-11-10", UsageEvent.DAILY_VISITOR, 10),
                c("2026-11-11", UsageEvent.DAILY_VISITOR, 30)), WED_NOV_11, 2);

        assertThat(weeks.get(0).avgDailyVisitors()).isEqualTo(10.0); // 70 / 7, complete week
        assertThat(weeks.get(0).complete()).isTrue();
        assertThat(weeks.get(1).avgDailyVisitors()).isEqualTo(20.0); // 60 / 3 days so far (Mon-Wed)
        assertThat(weeks.get(1).complete()).isFalse();
    }

    @Test
    void ignoresCountsOutsideTheRequestedWeeks() {
        List<Week> weeks = WeeklyUsage.summarize(List.of(
                c("2026-01-05", UsageEvent.VISIT, 99),
                c("2026-11-12", UsageEvent.VISIT, 99)), WED_NOV_11, 1); // past and future
        assertThat(weeks).singleElement().extracting(Week::visits).isEqualTo(0L);
    }
}
