/** Types mirror the backend DTOs in dev.yuroom.api.ApiDtos. */

export type Status = 'OK' | 'NO_CLASSES' | 'OUTSIDE_HOURS' | 'CLOSED' | 'EXAMS' | 'OUTSIDE_TERM'

export interface Building {
  code: string
  name: string | null
  campus: string | null
  latitude: number | null
  longitude: number | null
  roomCount: number
}

/** Anonymous "this room is actually occupied" reports in effect right now. */
export interface OccupiedReports {
  count: number
  lastReportedAt: string
}

export interface Hours {
  open: string
  close: string
}

export interface FreeRoom {
  roomId: number
  building: string
  room: string
  freeUntil: string
  freeUntilReason: 'NEXT_BOOKING' | 'CLOSING'
  minutesFree: number
  occupiedReports: OccupiedReports | null
}

export interface FreeRoomsResponse {
  at: string
  date: string
  status: Status
  notice: string | null
  term: string | null
  timetableDay: string | null
  hours: Hours
  dataUpdatedAt: string | null
  rooms: FreeRoom[]
}

export interface Range {
  start: string
  end: string
}

export interface RoomScheduleResponse {
  roomId: number
  building: string
  room: string
  date: string
  status: Status
  notice: string | null
  term: string | null
  timetableDay: string | null
  hours: Hours
  booked: Range[]
  free: Range[]
  dataUpdatedAt: string | null
  occupiedReports: OccupiedReports | null
}

/** An HTTP error, carrying the RFC 9457 problem "detail" when the server sent one. */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  return send<T>(path, { signal, headers: { Accept: 'application/json' } })
}

export interface ReportCreated {
  roomId: number
  expiresAt: string
  activeReports: number
  lastReportedAt: string
}

/** POST an occupancy report. Deliberately sends no body: nothing about the reporter is needed. */
export function postReport(roomId: number): Promise<ReportCreated> {
  return send<ReportCreated>(`/api/rooms/${roomId}/reports`, { method: 'POST', headers: { Accept: 'application/json' } })
}

async function send<T>(path: string, init: RequestInit): Promise<T> {
  const res = await fetch(path, init)
  if (!res.ok) {
    let detail = `Request failed (${res.status})`
    try {
      const problem = (await res.json()) as { detail?: string }
      if (problem.detail) detail = problem.detail
    } catch {
      // not JSON (e.g. proxy error page): keep the generic message
    }
    throw new ApiError(res.status, detail)
  }
  return (await res.json()) as T
}

export function freeRoomsPath(params: { building?: string; at?: string; minMinutes?: number }): string {
  const q = new URLSearchParams()
  if (params.building) q.set('building', params.building)
  if (params.at) q.set('at', params.at)
  if (params.minMinutes) q.set('minMinutes', String(params.minMinutes))
  const qs = q.toString()
  return `/api/rooms/free${qs ? `?${qs}` : ''}`
}

export function schedulePath(roomId: string | number, date?: string): string {
  return `/api/rooms/${encodeURIComponent(String(roomId))}/schedule${date ? `?date=${date}` : ''}`
}

/** GET /api/info: facts about this deployment the UI must be upfront about. */
export interface SiteInfo {
  demoData: boolean
}

export interface UsageWeek {
  weekStart: string
  visits: number
  weeklyVisitors: number
  avgDailyVisitors: number
  nearMeUses: number
  reports: number
  complete: boolean
}

export interface WeeklyUsageResponse {
  timeZone: string
  note: string
  weeks: UsageWeek[]
}
