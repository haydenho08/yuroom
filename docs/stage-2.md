# Stage 2 — The "is it free?" engine + REST API

**Goal:** answer "which rooms are free right now, and until when?" correctly on every kind of
day: normal, reading week, holiday, exams, make-up day, late at night, and DST changes.
When we can't know, say so instead of guessing.

## Architecture

```mermaid
flowchart LR
    C[RoomController / BuildingController<br/>parse + validate + map DTOs] --> S[AvailabilityService<br/>@Transactional readOnly]
    S -->|terms, rooms, meeting slots| DB[(Postgres)]
    S --> CAL[AcademicCalendar<br/>in memory, from calendar CSV]
    S --> RA[RoomAvailability<br/>pure]
    RA --> FTC[FreeTimeCalculator<br/>pure]
    C -.errors.-> E[ApiExceptionHandler<br/>RFC 9457 problem+json]
```

Everything in `availability/core` is **plain Java with no Spring imports**. The service loads
data and calls the core; the controller only handles HTTP.

## Endpoints

| Endpoint | Returns |
|---|---|
| `GET /api/buildings` | `[{code, name, campus, roomCount}]` sorted by code |
| `GET /api/rooms/free?building=&at=&minMinutes=` | `{at, date, status, notice, term, timetableDay, hours, dataUpdatedAt, rooms:[{roomId, building, room, freeUntil, freeUntilReason, minutesFree}]}`, longest-free first |
| `GET /api/rooms/{id}/schedule?date=` | `{…, booked:[{start,end}], free:[{start,end}]}` |

`at` accepts campus local time (`2026-11-09T14:00`) or an instant (`…Z`, `…-05:00`).
**Course codes never leave the server.** A test asserts this.

## How a date is classified (`AcademicCalendar`)
Official dates live in `src/main/resources/calendar/academic-calendar.csv`, copied from the
Registrar. When periods overlap, the first match wins:

1. **CLOSED** (Labour Day, Thanksgiving, winter break, Family Day, Good Friday): no rooms
2. **EXAMS**: no rooms, *"the class timetable doesn't apply during exams…"*
3. **NO_CLASSES** (reading weeks, study days): every room free all day
4. **MAKEUP_DAY**: Monday 2027-04-05 runs the **Friday** timetable
5. inside a term's dates: **CLASSES**
6. otherwise **OUTSIDE_TERM**

Then hours: outside 07:00–23:00 means **OUTSIDE_HOURS**. Each status comes with a user-facing
`notice`, all generated in one tested place.

## The free-time algorithm (`FreeTimeCalculator`)
1. **Merge** that day's bookings: sort, then join overlapping *or touching* intervals
   (8:30–10:00 + 10:00–11:30 → 8:30–11:30). Back-to-back classes leave no gap.
2. **Half-open intervals** `[start, end)`: at exactly 10:00 a 10:00 class has started, and at
   11:30 the 11:30-ending class is over. (A mutation test, flipping this rule, made 2 tests fail.)
3. **Free until** = the next booking's start, capped at closing time (`NEXT_BOOKING` vs `CLOSING`).
4. **Minutes** are computed through `ZonedDateTime` in America/Toronto, not by subtracting
   `LocalTime`s. Free 00:30→03:00 is 210 minutes on fall-back night (2026-11-01) and
   01:00→03:00 is 60 minutes on spring-forward night (2027-03-14). Partial minutes are dropped,
   so we never overstate.

Complexity: O(n log n) per room for the sort, where n is that room's bookings that day (usually under 10).

## Indexes (measured, not guessed)
I loaded 90k synthetic meetings and 1,500 rooms in a rolled-back transaction (York has fewer):

| Query | Index | With index | Seq scan |
|---|---|---|---|
| free rooms: `day_of_week = ? and start_date <= ? and end_date >= ?` | `meeting_day_dates_idx (day_of_week, start_date) include (end_date, room_id, start_time, end_time)` | **2.7 ms** | 12.0 ms |
| room schedule: `room_id = ? and day_of_week = ? and …` | `meeting_room_day_idx (room_id, day_of_week, start_date) include (…)` | **0.4 ms** | — |

Why these column orders:
- **Equality columns go first, then one range column.** A B-tree can use one range per index, so
  `end_date` goes in `INCLUDE` and is filtered from the index entry.
- **`INCLUDE` makes the index "covering".** In steady state (after VACUUM sets the visibility map),
  Postgres can do an *index-only scan*. In the demo the rows were fresh and uncommitted, so it
  used a bitmap heap scan instead. That's still fast and still uses the index.
- `meeting_room_day_idx` also serves as the index on the `meeting.room_id` foreign key. Postgres
  doesn't create indexes on foreign keys automatically.
- **Honest caveat:** at York's size, even the 12 ms seq scan would be acceptable. The index matters
  more for tail latency under concurrent load, and the 4× gap grows with data.

## Tradeoff: compute on request vs precompute
We compute on every request.
- **For on request:** the input is small (one weekday's slots, ~a few thousand rows, ~3 ms). The answer
  depends on `at` down to the minute, so a precomputed table would need a row per room per minute or
  per gap, and every ingestion and calendar change would have to rebuild it. Crowd reports (Stage 4)
  change minute to minute anyway.
- **When to precompute or cache:** if traffic grows, a short-lived cache keyed by `(date, building)`
  holding the merged bookings, invalidated when ingestion succeeds, removes the DB hit. It isn't
  needed yet, and it's easy to add because the core is pure.

## Data freshness
Every response includes `dataUpdatedAt`, the last time ingestion confirmed the timetable
(SUCCESS or UNCHANGED). The UI can show "timetable checked 3 hours ago" and flag stale data.

## Errors
Every error is `application/problem+json` (RFC 9457), including Spring's own:
```json
{"type":"about:blank","title":"Invalid request parameter","status":400,
 "detail":"minMinutes: must be less than or equal to 1440","instance":"/api/rooms/free",
 "errors":[{"parameter":"minMinutes","message":"must be less than or equal to 1440"}]}
```
400 for invalid params or types, 404 for an unknown building or room or URL, 500 with no internals leaked (logged server-side).

## Known limitations
- **Building hours** are one global 07:00–23:00 window. Real hours vary by building and on weekends.
- **Event and ad-hoc bookings** aren't in the timetable. Stage 4's crowd reports address this.
- **The calendar file is updated by hand each year.** If the next year's rows are missing, dates fall to OUTSIDE_TERM, which is safe because we don't claim rooms are free.

## Tests (138 total, all against Postgres in a non-Toronto JVM zone)
- `FreeTimeCalculatorTest` (35): merging, half-open boundaries, closing caps, early classes, DST minutes
- `AcademicCalendarTest` (22): the real calendar file, every kind of day in 2026–27, file validation
- `RoomAvailabilityTest` (10): status precedence, sorting, minMinutes, notices
- `CampusTimeTest` (8): `at` parsing, including DST gaps and overlaps
- `RoomApiIntegrationTest` (12): full HTTP stack on Testcontainers, error shapes, no course-code leaks

## Concepts & interview questions
| Concept | Question it answers |
|---|---|
| Pure core / functional core, imperative shell | "How did you make the business logic testable?" |
| Interval merging, half-open intervals | Classic algorithm question: "merge overlapping intervals" |
| `LocalTime` vs `ZonedDateTime` vs `Instant` | "How do you handle time zones and DST?" |
| Composite + covering indexes, EXPLAIN ANALYZE | "How did you choose your indexes? Prove they help." |
| Compute vs precompute vs cache | "Why not precompute availability?" |
| DTOs vs entities | "How do you avoid leaking internal data through your API?" |
| RFC 9457 problem details | "How do you design API errors?" |
| Rule precedence (calendar) | "How do you handle conflicting rules?" |
| Mutation testing | "How do you know your tests actually test something?" |
