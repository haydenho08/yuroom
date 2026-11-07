/**
 * Pure display helpers. Campus times arrive from the API as wall-clock strings ("14:30",
 * "2026-11-09T14:30:00-05:00"). We format the wall-clock part directly and never let the
 * browser convert it, because a phone set to another time zone would otherwise show the wrong times.
 */

const CAMPUS_ZONE = 'America/Toronto'

/** 105 -> "1h 45m", 60 -> "1h", 45 -> "45m". */
export function formatDuration(minutes: number): string {
  const m = Math.max(0, Math.floor(minutes))
  const h = Math.floor(m / 60)
  const rest = m % 60
  if (h === 0) return `${rest}m`
  return rest === 0 ? `${h}h` : `${h}h ${rest}m`
}

/** "14:30" -> "2:30 pm", "23:00" -> "11 pm", "00:15" -> "12:15 am". */
export function formatClock(hhmm: string): string {
  const [hText, mText] = hhmm.split(':')
  const h = Number(hText)
  const m = Number(mText)
  const suffix = h < 12 ? 'am' : 'pm'
  const h12 = h % 12 === 0 ? 12 : h % 12
  return m === 0 ? `${h12} ${suffix}` : `${h12}:${String(m).padStart(2, '0')} ${suffix}`
}

/** "08:30","10:00" -> "8:30–10 am"; spans noon -> "11 am–1:30 pm". */
export function formatRange(start: string, end: string): string {
  const s = formatClock(start)
  const e = formatClock(end)
  const sameHalf = s.slice(-2) === e.slice(-2)
  return sameHalf ? `${s.slice(0, -3)}–${e}` : `${s}–${e}`
}

/** "2026-11-09" -> "Mon, Nov 9" (calendar maths in UTC so no zone can shift the day). */
export function formatDate(isoDate: string): string {
  const [y, mo, d] = isoDate.split('-').map(Number)
  return new Date(Date.UTC(y!, mo! - 1, d!)).toLocaleDateString('en-CA', {
    weekday: 'short', month: 'short', day: 'numeric', timeZone: 'UTC',
  })
}

/** "2026-11-09T14:30:00-05:00" -> "Mon, Nov 9 · 2:30 pm" (the campus wall clock, as sent). */
export function formatCampusDateTime(offsetDateTime: string): string {
  return `${formatDate(offsetDateTime.slice(0, 10))} · ${formatClock(offsetDateTime.slice(11, 16))}`
}

/** "2026-11-09" + 1 -> "2026-11-10" (UTC maths: no DST surprises). */
export function addDays(isoDate: string, days: number): string {
  const [y, mo, d] = isoDate.split('-').map(Number)
  return new Date(Date.UTC(y!, mo! - 1, d! + days)).toISOString().slice(0, 10)
}

/** How long ago an instant was, for "timetable checked 3h ago". */
export function formatAgo(instant: string, now: Date = new Date()): string {
  const minutes = Math.floor((now.getTime() - new Date(instant).getTime()) / 60_000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 48) return `${hours}h ago`
  return `${Math.floor(hours / 24)} days ago`
}

/** Current campus date and time, whatever zone the phone is in. */
export function campusNow(now: Date = new Date()): { date: string; time: string } {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: CAMPUS_ZONE, year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(now)
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? ''
  return { date: `${get('year')}-${get('month')}-${get('day')}`, time: `${get('hour')}:${get('minute')}` }
}
