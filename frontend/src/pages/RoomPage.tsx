import { Link, useParams, useSearchParams } from 'react-router'
import { schedulePath, type Range, type RoomScheduleResponse } from '../api'
import { StatusNotice } from '../components/StatusNotice'
import { addDays, campusNow, formatDate, formatDuration, formatRange } from '../format'
import { useApi } from '../useApi'

interface Block extends Range {
  kind: 'free' | 'booked'
}

function minutes(hhmm: string): number {
  const [h, m] = hhmm.split(':').map(Number)
  return h! * 60 + m!
}

/** Interleave booked and free blocks in time order, clipped to opening hours. */
function blocks(s: RoomScheduleResponse): Block[] {
  const open = minutes(s.hours.open)
  const close = minutes(s.hours.close)
  const booked = s.booked
    .filter((b) => minutes(b.end) > open && minutes(b.start) < close)
    .map((b) => ({
      kind: 'booked' as const,
      start: minutes(b.start) < open ? s.hours.open : b.start,
      end: minutes(b.end) > close ? s.hours.close : b.end,
    }))
  const free = s.free.map((f) => ({ kind: 'free' as const, ...f }))
  return [...booked, ...free].sort((a, b) => minutes(a.start) - minutes(b.start))
}

export function RoomPage() {
  const { id = '' } = useParams()
  const [params] = useSearchParams()
  const date = params.get('date') ?? undefined
  const { data, error } = useApi<RoomScheduleResponse>(schedulePath(id, date))

  if (error && !data) {
    return (
      <section className="state" role="alert">
        <p>{error.message}</p>
        <Link to="/" className="button">Back to free rooms</Link>
      </section>
    )
  }
  if (!data) return <p className="state" aria-busy="true">Loading…</p>

  const now = campusNow()
  const isToday = data.date === now.date
  const timeline = blocks(data)

  return (
    <article>
      <Link to="/" className="back">← Free rooms</Link>
      <h1 className="room-title">
        <span className="room__building">{data.building}</span> {data.room}
      </h1>

      <nav className="daynav" aria-label="Day">
        <Link to={`?date=${addDays(data.date, -1)}`} aria-label="Previous day" replace>‹</Link>
        <span>{isToday ? `Today · ${formatDate(data.date)}` : formatDate(data.date)}</span>
        <Link to={`?date=${addDays(data.date, 1)}`} aria-label="Next day" replace>›</Link>
      </nav>

      <StatusNotice status={data.status} notice={data.notice} />

      {timeline.length > 0 && (
        <ol className="timeline">
          {timeline.map((b) => {
            const length = minutes(b.end) - minutes(b.start)
            const current = isToday && minutes(b.start) <= minutes(now.time) && minutes(now.time) < minutes(b.end)
            return (
              <li
                key={`${b.kind}-${b.start}`}
                className={`block block--${b.kind}${current ? ' block--now' : ''}`}
                // Taller for longer blocks, but clamped so a 13-hour free stretch doesn't fill the screen.
                style={{ minHeight: `${Math.min(10, 3.25 + length / 40)}rem` }}
              >
                <span className="block__label">{b.kind === 'free' ? 'Free' : 'Class'}</span>
                <span>{formatRange(b.start, b.end)}</span>
                <span className="muted">{formatDuration(length)}</span>
                {current && <span className="block__now">now</span>}
              </li>
            )
          })}
        </ol>
      )}
    </article>
  )
}
