-- Indexes for the two read paths of the "is it free?" API.
-- Both are shaped by their WHERE clauses: equality columns first, then the range column,
-- and INCLUDE the columns the query returns so Postgres can answer from the index alone
-- (an index-only scan) without visiting the table.

-- GET /api/rooms/free:
--   where day_of_week = :day and start_date <= :date and end_date >= :date
-- day_of_week (equality) narrows to ~1/5 of rows; start_date is the range that can still use
-- the B-tree; end_date is checked from the index entry. room_id/start/end are returned.
create index meeting_day_dates_idx
    on meeting (day_of_week, start_date)
    include (end_date, room_id, start_time, end_time);

-- GET /api/rooms/{id}/schedule:
--   where room_id = :room and day_of_week = :day and start_date <= :date and end_date >= :date
-- Two equality columns, so very selective. Its leading room_id also serves as the index for
-- the meeting.room_id foreign key (fast joins, and fast checks when a room is deleted).
create index meeting_room_day_idx
    on meeting (room_id, day_of_week, start_date)
    include (end_date, start_time, end_time);
