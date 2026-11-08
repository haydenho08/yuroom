package dev.yuroom.usage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

/**
 * Daily counters, one row per (day, event). Plain JDBC because the core operation is an atomic
 * upsert ({@code INSERT ... ON CONFLICT DO UPDATE count = count + 1}), which JPA has no good way to
 * say. Two visits at the same instant can't lose an increment: Postgres serialises them on the row.
 */
@Repository
public class UsageCounterRepository {

    public record DailyCount(LocalDate day, UsageEvent event, long count) {
    }

    private final JdbcTemplate jdbc;

    public UsageCounterRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void increment(LocalDate day, UsageEvent event) {
        jdbc.update("""
                insert into usage_daily (day, event, count) values (?, ?, 1)
                on conflict (day, event) do update set count = usage_daily.count + 1
                """, day, event.wireName());
    }

    /** Counters from {@code fromInclusive} on. Rows for events this version no longer knows are skipped. */
    public List<DailyCount> since(LocalDate fromInclusive) {
        List<DailyCount> out = new ArrayList<>();
        jdbc.query("select day, event, count from usage_daily where day >= ? order by day", (RowCallbackHandler) rs -> {
            Optional<UsageEvent> event = UsageEvent.fromWire(rs.getString("event"));
            if (event.isPresent()) {
                out.add(new DailyCount(rs.getObject("day", LocalDate.class), event.get(), rs.getLong("count")));
            }
        }, fromInclusive);
        return out;
    }
}
