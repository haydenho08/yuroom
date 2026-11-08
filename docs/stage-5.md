# Stage 5: Polish and launch readiness

**Goal:** turn a project that works on my laptop into something that can run in public: one deployable
image, automated checks on every push, honest usage numbers, and docs a recruiter can skim in 60 seconds.

## 1. One image, one origin
```mermaid
flowchart LR
    N["node:22-alpine<br/>npm ci, npm run build"] -->|dist/| G["temurin:21-jdk<br/>gradlew bootJar<br/>(dist → static/)"]
    G -->|layers| R["temurin:21-jre-alpine<br/>non-root, health check<br/>~265 MB RAM"]
```
- **Multi-stage build:** the final image has a JRE and our jar only, with no Node, JDK, Gradle or sources.
- **Layered jar** (`-Djarmode=tools extract --layers`): the dependencies layer rarely changes, so a redeploy
  pushes only the few-MB application layer.
- **The SPA is served by Spring** (`SpaWebConfig`): `/assets/*` files have content hashes, so they're cached
  for a year (`immutable`); `index.html` is `no-cache`, so deploys show up immediately; client routes like
  `/rooms/42` fall back to `index.html`; unknown `/api/*` URLs stay JSON 404s.
- **Why one origin?** No CORS, one TLS certificate, one thing to deploy and monitor. The tradeoff is that UI
  and API scale together, which is fine at this size.
- **Memory:** `MaxRAMPercentage=60` + SerialGC. Measured in `docker compose` under a 512 MB limit: about 265 MB.

## 2. Hosting-agnostic configuration
- `DatabaseUrlEnvironmentPostProcessor` turns `postgres://user:pass@host/db` (what Heroku, Render and
  Railway give you) into JDBC settings before the DataSource exists. It's a pure function with unit tests, and the
  local `docker compose --profile full` uses exactly that format, so it's exercised end to end.
- `PORT`, `DEMO_DATA` and `DB_POOL_SIZE` come from the environment. No secrets are in the repo.

## 3. The proxy problem (unfinished business from Stage 4)
Behind a host's proxy, `getRemoteAddr()` is the proxy, so every student would share one rate limit.
`server.forward-headers-strategy: native` makes Tomcat's `RemoteIpValve` read `X-Forwarded-For`, but:
1. only from **trusted** proxy addresses (private ranges), and
2. **from the right**: the proxy appends the real client last, so whatever a client wrote further left is ignored.

`RealTomcatIntegrationTest` runs a real Tomcat (MockMvc skips valves) and proves that
`X-Forwarded-For: 6.6.6.6, 192.0.2.20` is still rate-limited as `192.0.2.20`, and that
`X-Forwarded-Proto: https` turns on HSTS.

## 4. Security headers
On every response: a strict **Content-Security-Policy** (only our own scripts, styles and API; no inline
script; `frame-ancestors 'none'`), `nosniff`, **`Referrer-Policy: no-referrer`** (links out don't reveal
which room page you were on), and a `Permissions-Policy` allowing geolocation only for our origin. I checked
in a real browser that the built app runs with no CSP violations: React sets style via the CSSOM, which CSP allows.

## 5. Privacy-friendly analytics
**The question:** how can I honestly say "N weekly active users" without tracking anyone?

| Approach | Stores | Problem |
|---|---|---|
| Google Analytics | Cookies, IDs, everything | Third party, a cookie banner, overkill |
| Log every visit with a hashed IP | One row per visit | IP hashes are reversible (Stage 4) and it's a per-person log |
| Daily-rotating salted hash (Plausible-style) | Hash per day | Can count daily uniques, **not weekly** (the salt changes) |
| **Client-side "first visit this week" flag** (chosen) | `(day, event, count)` | Counts browsers, not people (documented caveat) |

- The browser keeps **one value**, the campus date of its last visit, in `localStorage`, and never sends it.
  On load it sends just event names: `visit`, plus `daily_visitor` / `weekly_visitor` if this is its first
  visit today or this ISO week. `near_me` is sent when Near me is used, and the server counts `report` itself.
- The server **upserts counters** (`INSERT … ON CONFLICT DO UPDATE count = count + 1`, which is atomic and
  race-free). The whole analytics schema is three columns, and a test asserts that.
- **Abuse:** event names come from an allowlist (`report` is server-only), at most 4 per request, and 30 events
  per client per hour (the Stage 4 rate limiter and rotating-HMAC keys, reused). Bots are mostly excluded
  because counting needs JavaScript, and `navigator.webdriver` browsers are skipped.
- **Respect:** Global Privacy Control / Do Not Track mean we don't count you at all. Blocked storage means we count the
  visit but never claim a new visitor, so errors undercount and never overcount.
- **Public:** `/stats` shows the weekly table with the caveats. If a number goes on a resume, anyone can check it.

## 6. Honest demo data
York's real timetable needs permission (Stage 0), so the deployed demo uses a **seeded, synthetic
timetable** (`scripts/GenerateDemoTimetable.java`: 63 rooms in real buildings, 2,522 meetings, "DEMO nnnn"
courses), run with `java File.java` and no build. `GET /api/info` → `{"demoData": true}` puts a banner on every
page. The image defaults to `DEMO_DATA=true`, so you have to opt in to claim the data is real.

## 7. CI/CD
`.github/workflows/ci.yml`, on every push and PR:
- **backend:** `./gradlew build` (javac `-Xlint:all`, 185 tests against a real Postgres via Testcontainers, run in Auckland time)
- **frontend:** `npm ci`, lint, typecheck, 72 tests, production build
- **docker:** builds the image (with a GitHub Actions layer cache), only after both jobs pass

It also uses least-privilege `permissions: contents: read`, cancels stale runs, and uploads the test report on failure.
Dependabot opens weekly update PRs, which CI then checks. I validated the workflow with `actionlint` and ran each step locally.

## Verified end to end
- `docker compose --profile full up --build`: the image starts healthy on the `prod` profile and connects through a `postgres://` URL.
- curl: `/` and `/rooms/5` return the app shell (`no-cache`); hashed assets are `immutable` and gzipped; `/api/nope` gives a JSON 404;
  every response has the CSP and the other security headers; `POST /api/usage` → 204 and `/api/usage/weekly` reflects it.
- In the browser at phone size: demo banner, room list, room page and `/stats`, with no console errors.

## Concepts & interview questions
| Concept | Question it answers |
|---|---|
| Multi-stage Docker builds, layered jars | "How do you containerize a Spring Boot app? How big is the image, and why?" |
| JVM in containers (`MaxRAMPercentage`) | "Your app gets OOM-killed on a 512 MB instance. Why?" |
| Reverse proxies, X-Forwarded-For trust | "How do you get the client IP behind a load balancer, and how can that be spoofed?" |
| CSP, Referrer-Policy, HSTS | "What security headers do you set, and what does each prevent?" |
| Cache-busting with content hashes | "How do you cache aggressively and still deploy instantly?" |
| Privacy-preserving analytics | "How do you measure usage without tracking users?" |
| Atomic upsert counters | "Two requests increment the same counter at once. What happens?" |
| CI design (fail fast, least privilege, caching) | "Walk me through your CI pipeline." |
| 12-factor config | "How does the same artifact run locally and in production?" |
| Honest metrics | "How many users do you have? How do you know?" |
