# Stage 0 — Check the data before writing code

**Date:** 2026-11-05 · **Outcome:** don't scrape; ask the Registrar; design for a swappable data source.

## Why this stage exists
A data product is only as legitimate as its data. Before modelling anything we checked
*whether we're allowed to collect the data*, *whether it's public*, and *what it doesn't tell us*.

## Findings

### (a) Is room + day + time public?
- **York Courses Website** (`w2prod.sis.yorku.ca`): per the Registrar's FAQ, sections list
  building + room and day codes (M T W R F). Passport York login is only needed for seat
  availability.
- **Visual Schedule Builder**: requires Passport York login → off limits for automation.
- **No official API or export** exists publicly.

### (b) robots.txt and terms of use
- Every request to the courses site, **including `/robots.txt`**, returned HTTP 403 with a
  Cloudflare "Just a moment…" managed challenge. Bots can't even read the rules — a clear
  "no automated clients" signal. Bypassing bot protection is off the table.
- `www.yorku.ca/robots.txt` disallows nothing, but doesn't govern the courses subdomain.
- Terms (`yorku.ca/about/privacy-legal`): personal, non-commercial use; no reproduction,
  modification or redistribution without York's prior written consent; no framing;
  York's name and marks are trademarks. A public app republishing the timetable = redistribution.
- Safety concern (unverified community claim): room numbers shouldn't be visible to
  non-York people. Regardless of policy, "course X is in room Y at 10:00" reveals where
  groups of students will be.

### (c) Format (from secondary sources — verify with a real saved page)
- Legacy WebObjects app; HTML with nested tables; old character encoding; a static
  timetable file regenerated nightly, occasionally missing rows.
- Hierarchy: term → faculty/subject → course → section → meeting group (LECT/TUTR/LAB)
  → meeting rows (day code, start time, duration, campus, `BUILDING ROOM`).
- Online/async sections have no location.

### (d) What's missing
- Exam schedules (separate, published later; class timetable doesn't apply during exams)
- Event/ad-hoc room bookings (internal, not public)
- Building hours, locked rooms, card-access labs
- Room changes in the first two weeks of term
- Term dates live elsewhere. 2026–27: classes start Sep 9; Fall Reading Week Oct 10–16;
  Thanksgiving Oct 12; fall classes end Dec 8; Fall Study Day Dec 9.

## Decision
1. Email the Registrar asking for permission and ideally a nightly CSV/JSON export.
   Offer to show only "booked 10:00–11:30", never course codes.
2. Put ingestion behind a `TimetableSource` interface (adapter pattern). First implementation:
   admin CSV import. A Registrar export or approved fetcher plugs in later.
3. Parser tests use synthetic fixtures shaped like the real structure; real pages stay out of git.
4. Fallback if refused: crowdsourced timetables + occupancy reports.

## Concepts & interview questions
| Concept | Question it answers |
|---|---|
| Data provenance / ToS review | "Where did your data come from, and were you allowed to use it?" |
| Respecting technical signals | "The site blocked bots — what did you do?" (Stopped and asked the owner.) |
| Adapter / ports-and-adapters | "How would you change your data source without rewriting the app?" |
| Data minimization | "What personal or sensitive data does your app expose?" |
| Knowing your data's gaps | "When is your app wrong, and how does it tell the user?" |
