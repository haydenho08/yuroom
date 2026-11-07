package dev.yuroom.availability.core;

import java.time.LocalTime;

/** What a room is doing at one instant. */
public sealed interface RoomState {

    /** Why a free window ends. */
    enum Until { NEXT_BOOKING, CLOSING }

    /** Free from now until {@code until}; {@code minutes} is real elapsed time (DST-aware). */
    record Free(LocalTime until, Until reason, long minutes) implements RoomState {
    }

    /** Booked until {@code until} (end of the merged booking block). */
    record Busy(LocalTime until) implements RoomState {
    }

    /** Outside the hours we're willing to make claims about. */
    record OutsideHours() implements RoomState {
    }
}
