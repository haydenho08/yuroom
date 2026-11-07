# Stage 3 — Mobile-first frontend

**Goal:** a student walking between buildings opens YURoom on their phone and has an answer
in seconds: which rooms are free, for how long, and what a room's day looks like.

## Stack and why
| Choice | Why |
|---|---|
| React 19 + TypeScript, Vite 8 | Fast dev server; strict TS catches API-shape mistakes at compile time |
| React Router 8 | URL-driven pages (`/`, `/rooms/:id`) and query-string filters |
| Plain CSS with custom properties | ~5 KB, no build plugin, dark mode via `prefers-color-scheme` |
| A 40-line `useApi` hook instead of a data library | Every line is explainable. TanStack Query is the upgrade if caching needs grow |
| Vitest + Testing Library + jsdom | Tests what the user sees (roles, text), not component internals |
| TypeScript **6.0**, not 7.0 | `typescript-eslint` supports TS < 6.1. Picking versions by peer ranges beats "always latest" |

Production bundle: **85 KB gzipped**, all JS and CSS.

## Screens
- **Free rooms (`/`)**: building chips (All / LAS / CLH…), a minimum-length filter (30m+, 1h+, 2h+),
  "Now" or a picked time. The list is ranked by the server, longest-free first:
  **"LAS C — Free until 11 pm (closing) · 10h 15m"**, with a bar scaled to 4h. Windows under
  30 minutes are shown in amber, since nobody wants to walk over for 15 minutes of study.
- **Room (`/rooms/:id?date=`)**: the day as alternating Free/Class blocks, taller for longer ones
  (clamped), with "now" highlighted when it's today, plus previous/next day.
- **Footer on every page:** "Student-built, not affiliated with York University." No York logo, crest
  or red. The palette is a neutral teal.

## Design decisions worth explaining

**Filters live in the URL** (`?building=LAS&min=60&at=…`), not in `useState`. The back button works, a
refresh keeps your filters, and "free rooms in LAS for 1h+" is a link you can paste in a course Discord.

**Campus time is never converted by the browser.** The API sends wall-clock strings
(`"14:30"`, `"2026-11-09T14:30:00-05:00"`). We format them as text and never pass them through `new Date()`,
because a phone set to another zone would shift every time. Tests run with `TZ=Pacific/Auckland`,
the same trick as the backend. "Now" for the date picker uses `Intl.DateTimeFormat` with
`timeZone: 'America/Toronto'`.

**Keeping answers fresh.** In "Now" mode the list refetches every 60 s and whenever the tab becomes
visible again (phones freeze background tabs, and "free until 2:30" from an hour ago is misleading).
Old results stay on screen, dimmed, while new ones load. There's no flash of an empty list.

**Race conditions.** Each fetch gets an `AbortController`, and changing a filter cancels the old request,
so a slow old response can't overwrite a newer one. React StrictMode's dev-only double mount shows
this working: the first requests appear as `ERR_ABORTED` in the network log.

**Loading is derived, not stored.** The new React Hooks lint rule (`set-state-in-effect`) rejected
`setLoading(true)` inside the effect. The fix: `loading = wantedRequest !== settledRequest`. One less
piece of state, one less render, and no way for `loading` to get out of sync.

**Honest UI.** Found while checking it in the browser: on an exam day the heading said
"0 rooms free", a claim the backend had just said it can't make. Now non-OK statuses get a
headline ("Exam period", "University closed", "Outside building hours") and the server's notice.
The empty state ("Try a shorter time or another building") only appears when filters caused it.

**Mobile-first CSS.** The base styles target a 360 px phone, with one `min-width: 40rem` breakpoint. Tap
targets are at least 44 px, the filters are sticky, chips scroll sideways, `100dvh` handles mobile
browser bars, and `env(safe-area-inset-*)` handles notches. `prefers-reduced-motion` turns off the pulse.

**Accessibility.** Real `<button>`s with `aria-pressed` for chips, `role="status"` for notices and
`role="alert"` for errors, `aria-live`/`aria-busy` on the results, visible focus rings, and semantic
lists. Tests query by role, which doubles as an accessibility check.

## Dev setup
```powershell
docker compose up -d --wait
cd backend;  .\gradlew.bat bootRun          # API on :8080
cd frontend; npm install; npm run dev       # UI on :5173, proxies /api to :8080
npm test; npm run lint; npm run typecheck; npm run build
```
The Vite proxy keeps the browser on one origin, so there's no CORS configuration.

## Verified
- 34 Vitest tests, ESLint clean, `tsc` strict clean, production build OK.
- Checked in a 375×812 phone viewport, light and dark, against the real backend: filters, room page,
  exam day, "Now" mode.
- `npm audit`: production dependencies have **0** vulnerabilities. 10 moderate ones are in `minimatch`
  via ESLint (dev only, glob patterns we control). `audit fix --force` would downgrade ESLint, so it was not applied.

## Concepts & interview questions
| Concept | Question it answers |
|---|---|
| URL as state | "Where do you keep UI state, and why?" |
| AbortController, stale responses | "How do you handle race conditions in data fetching?" |
| Derived vs stored state | "Why did the linter complain, and what did you change?" |
| Time zones on the client | "What happens if a user's phone is in another time zone?" |
| Stale-while-revalidate, visibility refresh | "How do you keep data fresh on mobile?" |
| Testing Library philosophy | "How do you test React components?" |
| Mobile-first, tap targets, safe areas | "How did you design for phones?" |
| Honest empty and error states | "How do you handle the cases where your app can't answer?" |
| Dependency choices via peer ranges | "How do you choose library versions?" |
