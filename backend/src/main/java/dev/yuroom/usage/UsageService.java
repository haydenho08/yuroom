package dev.yuroom.usage;

import dev.yuroom.availability.core.CampusTime;
import dev.yuroom.reports.ClientKeys;
import dev.yuroom.reports.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Privacy-friendly usage numbers: daily counters and nothing else.
 *
 * <p>What is stored: "on 2026-11-09 there were 412 visits and 230 daily visitors". What is not:
 * IP addresses, user agents, cookies, user IDs, page paths, timestamps of individual visits. The
 * browser decides whether it's a new daily/weekly visitor by remembering only the date of its own
 * last visit (in localStorage, never sent). So nobody, including us, can follow a person across days.
 *
 * <p>The client address is used once, in memory, to rate-limit the endpoint (same rotating-HMAC keys
 * as reports), so a script can't inflate the numbers from one connection.
 */
@Service
public class UsageService {

    static final int EVENTS_PER_CLIENT_PER_HOUR = 30;

    private final UsageCounterRepository counters;
    private final Clock clock;
    private final ClientKeys clientKeys;
    private final RateLimiter limiter;

    public UsageService(UsageCounterRepository counters, Clock clock) {
        this.counters = counters;
        this.clock = clock;
        this.clientKeys = new ClientKeys(clock, Duration.ofHours(24));
        this.limiter = new RateLimiter(EVENTS_PER_CLIENT_PER_HOUR, Duration.ofHours(1), clock, 100_000);
    }

    /** Counts events sent by a browser. False if this client is over its limit (nothing counted). */
    @Transactional
    public boolean recordFromClient(Set<UsageEvent> events, String clientAddress) {
        if (events.stream().anyMatch(e -> !e.clientMaySend())) {
            throw new IllegalArgumentException("clients may not send " + events);
        }
        String key = clientKeys.keyFor(clientAddress);
        for (int i = 0; i < events.size(); i++) {
            if (!limiter.tryAcquire(key).allowed()) {
                return false;
            }
        }
        LocalDate today = today();
        events.forEach(e -> counters.increment(today, e));
        return true;
    }

    /** Events only the server can observe (e.g. an accepted report). */
    @Transactional
    public void recordServerEvent(UsageEvent event) {
        counters.increment(today(), event);
    }

    @Transactional(readOnly = true)
    public List<WeeklyUsage.Week> weekly(int weeks) {
        LocalDate today = today();
        LocalDate from = WeeklyUsage.weekStart(today).minusWeeks(weeks - 1L);
        return WeeklyUsage.summarize(counters.since(from), today, weeks);
    }

    /** "Today" is the campus date, so a week runs Monday to Sunday in Toronto whatever the server zone. */
    private LocalDate today() {
        return CampusTime.toCampus(clock.instant()).toLocalDate();
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void pruneIdleKeys() {
        limiter.prune();
    }
}
