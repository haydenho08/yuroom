# Launch plan

**Rule zero:** don't promote YURoom to students while it runs on demo data. A post on r/yorku that
leads people to a room based on made-up classes burns the one first impression we get. The order is
**permission → real data → launch.**

## Phase 0: now (demo data)
- Deploy the demo (see [deploy.md](deploy.md)) so there's a live link for the resume and interviews.
  The banner says it's demo data.
- Send the [Registrar request](registrar-request.md). Follow up after about 10 business days if there's no answer.
- Hallway-test with 3 to 5 friends on their own phones: "find a room to study in for an hour". Watch,
  don't explain. Note where they hesitate. Fix the top two issues.
- Don't post publicly yet.

## Phase 1: permission granted, real data in
- Load real data through the approved channel, set `DEMO_DATA=false`, and spot-check 10 rooms against
  the official timetable and in person (walk past them).
- Re-check `academic-calendar.csv` against the Registrar's calendar for the current year.
- Verify rate limiting behind the host's proxy (deploy.md, "verify it").
- Pick the launch week: **weeks 2 to 4 of a term**, Monday or Tuesday, before 11 am. Not reading week or exams:
  the timetable doesn't apply then, and the app (correctly) says so, which is a dull first visit.

## Phase 2: launch (one channel at a time, so each one's effect shows on /stats)
| Day | Channel | Notes |
|---|---|---|
| Mon | **r/yorku** | Check the subreddit rules on self-promotion first; message the mods if unsure. A text post with a screenshot, not just a link |
| Tue–Wed | **Course Discords** (EECS 1012/1022/2030, etc.) | Ask a TA or mod before posting; post in #off-topic or #resources, not announcements |
| Thu | **CSHub** (York CS community) | Developer angle: how it's built, open to feedback |
| Following week | **GDG York** | Offer a 5-minute lightning talk: "Building a privacy-first campus app with Spring Boot" |

### Draft post (r/yorku)
> **I made a free site that shows which classrooms are empty right now (no login)**
>
> Tired of wandering around looking for somewhere to study, I built YURoom: it lists classrooms with
> no class scheduled right now, sorted by how long they stay free ("free until 2:30, 1h 45m"). You can
> filter by building, sort by what's near you (your location stays on your phone), and flag a room
> that's actually occupied so the next person knows.
>
> It's based on the class timetable, so rooms can still be in use for events or full of people
> studying. Student-built, not affiliated with York. Feedback very welcome: [link]

## Measuring it honestly
- **Weekly visitors** (from `/stats`) is the headline number. Wording for a resume or interview:
  "~N weekly active users (privacy-preserving count of unique browsers per week)". Don't say "users"
  without that qualifier: one person on a laptop and a phone counts twice, and private windows count every visit.
- Track the **retention signal**: weekly visitors in weeks 3 and 4 after launch vs launch week. A spike that
  dies tells you something different from a steady base.
- **Reports per week** show whether the crowd feature is used. **Near me per visit** shows whether it earns its place.
- Collect feedback through GitHub Issues or a short form linked in the footer, with no personal data required.

## Risks and responses
| Risk | Response |
|---|---|
| York asks us to stop or change something | Comply immediately; the disclaimer and no-branding rules are there to avoid this |
| A room shown as free is locked or booked | Reports handle it in the moment; the footer sets expectations |
| Report spam | Rate limits + 1-hour expiry + rooms never hidden; if needed, raise the threshold to show a warning only after 2 reports |
| Traffic spike | One instance handles it (compute-on-request with covering indexes); scale the dyno vertically before anything fancier |
