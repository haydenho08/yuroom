package dev.yuroom.timetable;

import java.time.LocalTime;

/** Read model for availability queries: only what the engine needs, no entity loading. */
public record MeetingSlot(Long roomId, LocalTime start, LocalTime end) {
}
