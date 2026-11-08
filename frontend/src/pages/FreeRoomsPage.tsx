import { useSearchParams } from 'react-router'
import { freeRoomsPath, type Building, type FreeRoomsResponse, type Status } from '../api'
import { Chips } from '../components/Chips'
import { RoomList } from '../components/RoomList'
import { StatusNotice } from '../components/StatusNotice'
import { campusNow, formatAgo, formatCampusDateTime, formatDuration } from '../format'
import { formatDistance } from '../geo'
import { nearestBuildingMetres, rankRooms } from '../ranking'
import { useApi } from '../useApi'
import { useLocation, type LocationState } from '../useLocation'
import { recordNearMe } from '../usage'

const MIN_OPTIONS = [
  { value: '0', label: 'Any length' },
  { value: '30', label: '30m+' },
  { value: '60', label: '1h+' },
  { value: '120', label: '2h+' },
]

/**
 * When the timetable doesn't apply, "0 rooms free" would be a claim we can't make.
 * These statuses get a headline that says what's going on instead of a count.
 */
const HEADLINE: Partial<Record<Status, string>> = {
  EXAMS: 'Exam period',
  CLOSED: 'University closed',
  OUTSIDE_HOURS: 'Outside building hours',
  OUTSIDE_TERM: 'No classes in session',
}

/**
 * Filters live in the URL (?building=LAS&min=60&at=...), not in component state: the back
 * button works, a refresh keeps them, and a link to "free rooms in LAS for 1h+" can be shared.
 */
export function FreeRoomsPage() {
  const [params, setParams] = useSearchParams()
  const building = params.get('building') ?? ''
  const min = params.get('min') ?? '0'
  const at = params.get('at') ?? ''

  const buildings = useApi<Building[]>('/api/buildings')
  const free = useApi<FreeRoomsResponse>(
    freeRoomsPath({ building: building || undefined, at: at || undefined, minMinutes: Number(min) }),
    { refreshMs: at ? undefined : 60_000 }, // "now" mode keeps itself current
  )

  // "Near me" is component state, never URL state: a location must not end up in a shared link.
  const location = useLocation()
  const here = location.state.kind === 'ready' ? location.state.point : null

  const update = (key: string, value: string) => {
    const next = new URLSearchParams(params)
    if (value && value !== '0') next.set(key, value)
    else next.delete(key)
    setParams(next, { replace: true })
  }

  const data = free.data
  return (
    <>
      <section className="filters" aria-label="Filters">
        <Chips
          label="Building"
          value={building}
          onChange={(v) => update('building', v)}
          options={[{ value: '', label: 'All' }, ...(buildings.data ?? []).map((b) => ({ value: b.code, label: b.code }))]}
        />
        <Chips label="Free for at least" value={min} onChange={(v) => update('min', v)} options={MIN_OPTIONS} />
        <div className="when">
          <label className="when__toggle">
            <input type="checkbox" checked={!at} onChange={(e) => update('at', e.target.checked ? '' : defaultPickTime())} />
            Now
          </label>
          {at && (
            <input
              type="datetime-local"
              aria-label="Check another time (campus time)"
              value={at}
              onChange={(e) => update('at', e.target.value)}
            />
          )}
          <button
            type="button"
            className="chip near"
            aria-pressed={location.state.kind !== 'off'}
            onClick={() => {
              if (location.state.kind === 'off') {
                location.start()
                recordNearMe() // just "near me was used", never where
              } else {
                location.stop()
              }
            }}
          >
            Near me
          </button>
        </div>
        <LocationStatus state={location.state} buildings={buildings.data ?? []} />
      </section>

      {free.error && !data ? (
        <section className="state" role="alert">
          <p>Couldn't load rooms: {free.error.message}</p>
          <button type="button" className="button" onClick={free.reload}>Try again</button>
        </section>
      ) : !data ? (
        <SkeletonList />
      ) : (
        <section aria-live="polite" aria-busy={free.loading}>
          <div className="summary">
            <h1>
              {HEADLINE[data.status] ?? (
                <>
                  {data.rooms.length} {data.rooms.length === 1 ? 'room' : 'rooms'} free
                  {building && <> in {building}</>}
                </>
              )}
            </h1>
            <p className="muted">
              {formatCampusDateTime(data.at)}
              {data.dataUpdatedAt && <> · timetable checked {formatAgo(data.dataUpdatedAt)}</>}
            </p>
          </div>
          <StatusNotice status={data.status} notice={data.notice} />
          {data.rooms.length > 0 ? (
            <RoomList rooms={rankRooms(data.rooms, buildings.data ?? [], here)} date={data.date} />
          ) : (
            <EmptyState status={data.status} min={Number(min)} building={building} />
          )}
        </section>
      )}
    </>
  )
}

function EmptyState({ status, min, building }: { status: Status; min: number; building: string }) {
  if (status !== 'OK' && status !== 'NO_CLASSES') return null // the notice already explains why
  return (
    <p className="state">
      No rooms free{min > 0 && <> for {formatDuration(min)}+</>}{building && <> in {building}</>} right now.
      {(min > 0 || building) && ' Try a shorter time or another building.'}
    </p>
  )
}

/** One line explaining where "near me" is at, including the privacy promise. */
function LocationStatus({ state, buildings }: { state: LocationState; buildings: Building[] }) {
  switch (state.kind) {
    case 'off':
      return null
    case 'locating':
      return <p className="hint" role="status">Finding you… Your location stays on this device.</p>
    case 'denied':
      return <p className="hint hint--warn" role="status">Location is blocked for this site, so rooms aren't sorted by distance.</p>
    case 'unavailable':
      return <p className="hint hint--warn" role="status">{state.message}</p>
    case 'ready': {
      const nearest = nearestBuildingMetres(buildings, state.point)
      if (nearest !== null && nearest > 3000) {
        return (
          <p className="hint hint--warn" role="status">
            You seem to be off campus (nearest building {formatDistance(nearest)} away).
          </p>
        )
      }
      return <p className="hint" role="status">Sorted by distance. Your location stays on this device.</p>
    }
  }
}

function SkeletonList() {
  return (
    <ul className="rooms" aria-label="Loading rooms">
      {Array.from({ length: 6 }, (_, i) => (
        <li key={i}><span className="room room--skeleton" /></li>
      ))}
    </ul>
  )
}

/** When switching off "Now", start the picker at the current campus time. */
function defaultPickTime(): string {
  const { date, time } = campusNow()
  return `${date}T${time}`
}
