package dev.yuroom.usage;

import java.util.Arrays;
import java.util.Optional;

/**
 * The complete list of things YURoom counts. Each is just a number per day; there is no event log.
 * Adding a metric means adding it here, in code review, not quietly logging something new.
 */
public enum UsageEvent {
    /** The app was opened. */
    VISIT("visit", true),
    /** First visit today from this browser (the browser remembers only the date of its last visit). */
    DAILY_VISITOR("daily_visitor", true),
    /** First visit this week (Monday-start, campus time) from this browser. */
    WEEKLY_VISITOR("weekly_visitor", true),
    /** Someone turned on Near me. Says nothing about where they were. */
    NEAR_ME("near_me", true),
    /** An occupancy report was accepted. Counted by the server; clients can't send it. */
    REPORT("report", false);

    private final String wireName;
    private final boolean clientMaySend;

    UsageEvent(String wireName, boolean clientMaySend) {
        this.wireName = wireName;
        this.clientMaySend = clientMaySend;
    }

    public String wireName() {
        return wireName;
    }

    public boolean clientMaySend() {
        return clientMaySend;
    }

    public static Optional<UsageEvent> fromWire(String name) {
        return Arrays.stream(values()).filter(e -> e.wireName.equals(name)).findFirst();
    }
}
