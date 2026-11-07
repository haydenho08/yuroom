import { Link } from 'react-router'
import { formatClock, formatDuration } from '../format'
import { formatDistance, walkingMinutes } from '../geo'
import type { RankedRoom } from '../ranking'
import { ReportWarning } from './ReportWarning'

/** Bars are scaled against 4 hours: beyond that, "a long time" is all that matters. */
const BAR_FULL_MINUTES = 240

export function RoomList({ rooms, date }: { rooms: RankedRoom[]; date: string }) {
  return (
    <ul className="rooms">
      {rooms.map((r) => (
        <li key={r.roomId}>
          <Link to={`/rooms/${r.roomId}?date=${date}`} className={`room${r.occupiedReports ? ' room--reported' : ''}`}>
            <span className="room__top">
              <span className="room__name">
                <span className="room__building">{r.building}</span> {r.room}
              </span>
              {r.distanceMetres !== null && (
                <span className="room__distance">
                  {formatDistance(r.distanceMetres)} · {walkingMinutes(r.distanceMetres)} min walk
                </span>
              )}
            </span>
            <span className="room__free">
              Free until {formatClock(r.freeUntil)}
              {r.freeUntilReason === 'CLOSING' && <span className="muted"> (closing)</span>}
              {' · '}
              <strong className={r.minutesFree < 30 ? 'short' : undefined}>{formatDuration(r.minutesFree)}</strong>
            </span>
            {r.occupiedReports && <ReportWarning reports={r.occupiedReports} compact />}
            <span className="bar" aria-hidden="true">
              <span style={{ width: `${Math.min(100, (r.minutesFree / BAR_FULL_MINUTES) * 100)}%` }} />
            </span>
          </Link>
        </li>
      ))}
    </ul>
  )
}
