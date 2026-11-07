import { Link } from 'react-router'
import type { FreeRoom } from '../api'
import { formatClock, formatDuration } from '../format'

/** Bars are scaled against 4 hours: beyond that, "a long time" is all that matters. */
const BAR_FULL_MINUTES = 240

export function RoomList({ rooms, date }: { rooms: FreeRoom[]; date: string }) {
  return (
    <ul className="rooms">
      {rooms.map((r) => (
        <li key={r.roomId}>
          <Link to={`/rooms/${r.roomId}?date=${date}`} className="room">
            <span className="room__name">
              <span className="room__building">{r.building}</span> {r.room}
            </span>
            <span className="room__free">
              Free until {formatClock(r.freeUntil)}
              {r.freeUntilReason === 'CLOSING' && <span className="muted"> (closing)</span>}
              {' · '}
              <strong className={r.minutesFree < 30 ? 'short' : undefined}>{formatDuration(r.minutesFree)}</strong>
            </span>
            <span className="bar" aria-hidden="true">
              <span style={{ width: `${Math.min(100, (r.minutesFree / BAR_FULL_MINUTES) * 100)}%` }} />
            </span>
          </Link>
        </li>
      ))}
    </ul>
  )
}
