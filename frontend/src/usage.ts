/**
 * Privacy-friendly usage counting. The server only ever receives event names like "visit"; it keeps
 * a number per day and nothing else (see dev.yuroom.usage.UsageService).
 *
 * "Is this a new visitor today / this week?" is decided here, in the browser, by remembering one
 * thing: the campus date of this browser's last visit. That date is never sent anywhere. No cookie,
 * no ID, no fingerprint, so there's nothing to link one visit to another on the server.
 */
import { campusNow } from './format'

export type UsageEventName = 'visit' | 'daily_visitor' | 'weekly_visitor' | 'near_me'

const LAST_VISIT_KEY = 'yuroom.lastVisit'

/** "2026-11-11" (Wed) -> "2026-11-09" (Mon). Pure date maths in UTC so no zone can shift the day. */
export function mondayOf(isoDate: string): string {
  const [y, m, d] = isoDate.split('-').map(Number)
  const date = new Date(Date.UTC(y!, m! - 1, d!))
  const sinceMonday = (date.getUTCDay() + 6) % 7
  date.setUTCDate(date.getUTCDate() - sinceMonday)
  return date.toISOString().slice(0, 10)
}

/** Which events a visit on {@code today} produces, given this browser's previous visit date. */
export function visitEvents(today: string, lastVisit: string | null): UsageEventName[] {
  const events: UsageEventName[] = ['visit']
  if (lastVisit !== today) events.push('daily_visitor')
  if (lastVisit === null || mondayOf(lastVisit) !== mondayOf(today)) events.push('weekly_visitor')
  return events
}

/** People who asked their browser not to be tracked aren't counted, even anonymously. Nor are bots we can spot. */
function optedOut(): boolean {
  const nav = navigator as Navigator & { globalPrivacyControl?: boolean }
  return nav.globalPrivacyControl === true || nav.doNotTrack === '1' || nav.webdriver === true
}

function send(events: UsageEventName[]): void {
  if (optedOut()) return
  // Fire and forget: counting must never slow down or break the app.
  fetch('/api/usage', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ events }),
    keepalive: true,
  }).catch(() => undefined)
}

let visitRecorded = false
let nearMeRecorded = false

/** Once per page load. If storage is blocked we count the visit but claim no new visitor (undercount, never overcount). */
export function recordVisit(now: Date = new Date()): void {
  if (visitRecorded) return
  visitRecorded = true
  const today = campusNow(now).date
  let events: UsageEventName[] = ['visit']
  try {
    events = visitEvents(today, localStorage.getItem(LAST_VISIT_KEY))
    localStorage.setItem(LAST_VISIT_KEY, today)
  } catch {
    // private mode / storage disabled: keep just "visit"
  }
  send(events)
}

/** Counts that Near me was used (once per page load). Never anything about where. */
export function recordNearMe(): void {
  if (nearMeRecorded) return
  nearMeRecorded = true
  send(['near_me'])
}

export function resetUsageForTests(): void {
  visitRecorded = false
  nearMeRecorded = false
}
