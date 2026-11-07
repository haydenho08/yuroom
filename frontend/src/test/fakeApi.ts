import { vi } from 'vitest'
import type { Building, FreeRoomsResponse, RoomScheduleResponse } from '../api'

export const BUILDINGS: Building[] = [
  { code: 'CLH', name: null, campus: 'Keele', roomCount: 1 },
  { code: 'LAS', name: null, campus: 'Keele', roomCount: 3 },
]

export function freeResponse(overrides: Partial<FreeRoomsResponse> = {}): FreeRoomsResponse {
  return {
    at: '2026-11-09T12:45:00-05:00',
    date: '2026-11-09',
    status: 'OK',
    notice: null,
    term: '2026FA',
    timetableDay: 'MONDAY',
    hours: { open: '07:00', close: '23:00' },
    dataUpdatedAt: new Date(Date.now() - 3 * 3_600_000).toISOString(),
    rooms: [
      { roomId: 3, building: 'LAS', room: 'C', freeUntil: '23:00', freeUntilReason: 'CLOSING', minutesFree: 615 },
      { roomId: 7, building: 'CLH', room: 'A', freeUntil: '14:30', freeUntilReason: 'NEXT_BOOKING', minutesFree: 105 },
      { roomId: 2, building: 'LAS', room: 'B', freeUntil: '13:00', freeUntilReason: 'NEXT_BOOKING', minutesFree: 15 },
    ],
    ...overrides,
  }
}

export function scheduleResponse(overrides: Partial<RoomScheduleResponse> = {}): RoomScheduleResponse {
  return {
    roomId: 1,
    building: 'LAS',
    room: 'A',
    date: '2026-11-09',
    status: 'OK',
    notice: null,
    term: '2026FA',
    timetableDay: 'MONDAY',
    hours: { open: '07:00', close: '23:00' },
    booked: [{ start: '08:30', end: '11:30' }, { start: '13:00', end: '14:00' }],
    free: [{ start: '07:00', end: '08:30' }, { start: '11:30', end: '13:00' }, { start: '14:00', end: '23:00' }],
    dataUpdatedAt: null,
    ...overrides,
  }
}

type Route = (url: URL) => { status?: number; body: unknown } | undefined

/** Replaces fetch with a tiny router; returns the mock so tests can inspect requested URLs. */
export function fakeFetch(route: Route) {
  const mock = vi.fn(async (input: RequestInfo | URL) => {
    const url = new URL(String(input), 'http://localhost')
    const res = route(url) ?? { status: 404, body: { detail: `no fake for ${url.pathname}` } }
    return new Response(JSON.stringify(res.body), {
      status: res.status ?? 200,
      headers: { 'Content-Type': 'application/json' },
    })
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

export function requestedUrls(mock: ReturnType<typeof fakeFetch>): string[] {
  return mock.mock.calls.map(([input]) => String(input))
}
