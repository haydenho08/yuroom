# YURoom timetable CSV format

This is the format the `FILE` ingestion source reads. It is also the format we'd ask the
Registrar to export (see `docs/registrar-request.md`). One row is one weekly class meeting.

```csv
term_code,campus,building,room,day,start_time,end_time,duration_minutes,start_date,end_date,course_code,section,meeting_type
2026FA,Keele,LAS,A,MW,8:30,,90,2026-09-09,2026-12-08,DEMO 1001,A,LECT
```

| Column | Required | Example | Notes |
|---|---|---|---|
| `term_code` | yes | `2026FA` | Any stable code. Term dates are taken from the span of its meetings. |
| `campus` | no | `Keele` | Stored on the building the first time it's seen. |
| `building` | yes | `LAS` | Building acronym. `ONLINE`, `TBA`, `REMOTE`, `N/A` or blank: the row is skipped (no physical room). |
| `room` | yes | `1006` | Blank/`TBA`: skipped. |
| `day` | yes | `MWF`, `R`, `Thu` | Letters `M T W R(Thu) F S(Sat) U(Sun)`. Several letters means several days. Full or short names also work. |
| `start_time` | yes | `8:30`, `14:00` | 24-hour local time, America/Toronto. |
| `end_time` | one of these two | `10:00` | Must be after `start_time`. |
| `duration_minutes` | one of these two | `90` | Used when `end_time` is blank. Meetings may not cross midnight. |
| `start_date`, `end_date` | yes | `2026-09-09` | ISO dates. The first and last date the meeting runs. |
| `course_code`, `section`, `meeting_type` | no | `DEMO 1001`, `A`, `LECT` | Stored for data quality. **Never exposed by the public API.** |

**General rules**
- UTF-8 by default (a BOM is fine). The charset can be changed with `yuroom.ingestion.charset`.
- The header row is required. Column order doesn't matter, and header names are case-insensitive.
- Lines starting with `#` are comments.
- Standard CSV quoting: `"DEMO, ""quoted"""`.

**What happens to bad rows:** each one is rejected with its line number and a reason, and the
good rows are kept. If no rows are valid, or more than `max-reject-ratio` (default 20%) are
rejected, the whole file is refused and the previous timetable keeps being served.
