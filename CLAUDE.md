# YURoom — project memory

YURoom shows York University students which classrooms have no class scheduled right now
and how long they stay free. Built by Hayden Ho (1st-year SWE, York) as a resume project
for Summer 2027 SWE internships. Goal: show Java + Spring Boot and ship a real product
York students use. Sibling projects: LockIn (Python/FastAPI/React), Statblox (TypeScript/Redis).

## How we work
- Build in stages. After each stage, STOP and walk Hayden through what was built and why,
  professor-style: concepts, tradeoffs, and the interview questions they answer.
  Do not start the next stage until Hayden says so.
- Write a short teaching walkthrough per stage in `docs/stage-N.md`.
- Actually run things: Docker, Gradle build + tests until green, start the app end to end.
- Environment: Windows, PowerShell, VS Code, Docker Desktop.

## Git rules
- Commit as `Hayden Ho <hydnho@gmail.com>` (set via repo-local `git config`).
- NEVER add Co-Authored-By or any Claude/AI attribution lines to commit messages or PRs.
- Small, meaningful commits.
- GitHub username: haydenho08. ASK before creating or pushing to any remote repo.

## Tech stack
- Backend: Java 21, Spring Boot 3, Spring Data JPA, Flyway, Jsoup (if HTML parsing), Gradle
- DB: PostgreSQL 16 via docker-compose
- Frontend: React + TypeScript (Vite), mobile-first
- Tests: JUnit 5, Testcontainers (Postgres), Vitest

## Engineering standards
- Layered backend: controller → service → repository. DTOs separate from entities.
  Validate inputs. Consistent error responses.
- Free-time logic is a pure, Spring-free, heavily tested service.
- All time logic uses java.time in America/Toronto (DST-safe).
- Explain tradeoffs as they arise: compute-on-request vs precompute, data freshness,
  why user location stays in the browser.

## Local dev
- JDK: Temurin 21 at `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot` (set JAVA_HOME if
  `java` isn't on PATH yet). Gradle wrapper pinned to 8.14.3; Spring Boot 3.5.x.
- Postgres runs on **localhost:5433** (5432 is used by Hayden's studyflow-db; don't touch it).
- `docker compose up -d --wait`, then in `backend/`: `gradlew test`, `gradlew bootRun`.
- Tests run with `user.timezone=Pacific/Auckland` on purpose; never "fix" a test by changing that.
- Killing a `gradlew bootRun` task can leave the java.exe holding port 8080; stop it by port.
- Production image locally: `docker compose --profile full up -d --build --wait` (app on :8080, 512 MB limit,
  same DB volume as dev). Stop it before `bootRun` (both use 8080).

## Stages
0. **Check the data first** — DONE (see `docs/stage-0.md`). Outcome: do NOT scrape.
1. **Data model + ingestion** — DONE (see `docs/stage-1.md`). Term, Building, Room, Meeting entities; scheduled ingestion job
   (rate-limited, cached, respects robots.txt, at most daily, generic UA "YURoom student project",
   never Hayden's email). Store raw + parsed data. Parser tested against saved sample files,
   never the live site. Flyway, docker-compose, .env.example, .gitignore.
2. **"Is it free?" engine + REST API** — DONE (see `docs/stage-2.md`). Calendar dates live in
   `backend/src/main/resources/calendar/academic-calendar.csv` (re-check yearly vs Registrar). `GET /api/buildings`,
   `GET /api/rooms/free?building=&at=&minMinutes=` (free until + minutes remaining),
   `GET /api/rooms/{id}/schedule?date=`. Edge cases: outside term, reading week, holidays,
   exam period (timetable doesn't apply — say so), early/late hours. Justify indexes.
3. **Frontend (mobile-first)** — DONE (see `docs/stage-3.md`). `frontend/`: `npm run dev` (proxies
   /api to :8080), `npm test`, `npm run lint`, `npm run typecheck`. TypeScript pinned to 6.0.x
   (typescript-eslint peer range). Vitest runs with TZ=Pacific/Auckland on purpose. building picker or all; free rooms sorted by free duration
   ("Free until 2:30 · 1h 45m"); room detail with day schedule. Footer:
   "Student-built, not affiliated with York University." No York logo/official branding.
4. **Near me + crowd reports** — DONE (see `docs/stage-4.md`). Building coords in
   `backend/src/main/resources/buildings/buildings.csv` (OSM, ODbL attribution). Reports table has NO
   reporter columns; rate limits are in-memory with HMAC keys (rotating secret). Trusted
   X-Forwarded-For handling was added in Stage 5 (prod profile).
   Original spec: hand-curated building coordinates, distance sort in the browser
   (location never sent to/stored on server). Anonymous "actually occupied" reports,
   expire ~60 min, rate-limited, shown as a warning.
5. **Polish + launch** — DONE (see `docs/stage-5.md`, `docs/deploy.md`, `docs/launch-plan.md`).
   One Docker image (React build served by Spring, `SpaWebConfig`); `prod` profile trusts X-Forwarded-For
   via Tomcat RemoteIpValve (private-range proxies only); security headers filter (CSP etc.);
   `postgres://` DATABASE_URL auto-translated; analytics = `usage_daily(day,event,count)` only, public at
   `/stats`; `GET /api/info` demoData banner (image defaults DEMO_DATA=true). Demo timetable is generated:
   `java scripts/GenerateDemoTimetable.java > data/sample/timetable-demo.csv`. CI in `.github/workflows/ci.yml`.
   Recommended host: Heroku via GitHub Student Pack (Hayden must create accounts/deploy). NOT deployed,
   no remote repo yet. Launch only after Registrar permission + real data.

## Data-source decisions (from Stage 0)
- York Courses site (w2prod.sis.yorku.ca) is behind a Cloudflare bot challenge — even
  robots.txt returns 403 to non-browser clients. Do not attempt to bypass it.
- yorku.ca terms: personal non-commercial use only; no reproduction/redistribution without
  York's prior written consent; York name/marks are trademarks.
- Visual Schedule Builder requires Passport York login — never automate it.
- Plan: ask the Registrar for permission / an export. Meanwhile ingestion goes through a
  `TimetableSource` interface with an admin CSV-import implementation. Parser fixtures are
  synthetic (real saved York pages, if any, stay out of git).
- Public API exposes "booked HH:MM–HH:MM" only, not course codes (safety/privacy).
