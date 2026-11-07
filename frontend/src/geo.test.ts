import { describe, expect, it } from 'vitest'
import { distanceMetres, formatDistance, walkingMinutes } from './geo'
import { nearestBuildingMetres, rankRooms } from './ranking'
import { BUILDINGS, freeResponse } from './test/fakeApi'

const VARI_HALL = { latitude: 43.772689, longitude: -79.503413 }
const LAS = { latitude: 43.773932, longitude: -79.50525 }
const YORK_HALL_GLENDON = { latitude: 43.727099, longitude: -79.378702 }

describe('distanceMetres', () => {
  it('is zero to itself and symmetric', () => {
    expect(distanceMetres(LAS, LAS)).toBe(0)
    expect(distanceMetres(LAS, VARI_HALL)).toBeCloseTo(distanceMetres(VARI_HALL, LAS), 6)
  })

  it('matches known campus distances', () => {
    expect(distanceMetres(VARI_HALL, LAS)).toBeGreaterThan(190)
    expect(distanceMetres(VARI_HALL, LAS)).toBeLessThan(220)
    // Keele to Glendon is about 11 km in a straight line.
    expect(distanceMetres(VARI_HALL, YORK_HALL_GLENDON) / 1000).toBeCloseTo(11.1, 0)
  })
})

describe('walking and formatting', () => {
  it.each([[0, 1], [80, 2], [200, 4], [1000, 17]])('%s m -> %s min', (m, min) => {
    expect(walkingMinutes(m)).toBe(min)
  })

  it.each([[4, '10 m'], [351, '350 m'], [999, '1000 m'], [1234, '1.2 km']])('%s -> %s', (m, text) => {
    expect(formatDistance(m)).toBe(text)
  })
})

describe('rankRooms', () => {
  const rooms = freeResponse().rooms // LAS C (615 min), CLH A (105), LAS B (15)

  it('keeps the server order when there is no location', () => {
    const ranked = rankRooms(rooms, BUILDINGS, null)
    expect(ranked.map((r) => r.room)).toEqual(['C', 'A', 'B'])
    expect(ranked.every((r) => r.distanceMetres === null)).toBe(true)
  })

  it('sorts by distance, keeping longest-free first within a building', () => {
    const standingAtClh = { latitude: 43.77317, longitude: -79.50518 }
    const ranked = rankRooms(rooms, BUILDINGS, standingAtClh)
    expect(ranked.map((r) => `${r.building} ${r.room}`)).toEqual(['CLH A', 'LAS C', 'LAS B'])
    expect(ranked[0]!.distanceMetres).toBeLessThan(5)
  })

  it('keeps reported rooms last even when they are closest', () => {
    const withReport = rooms.map((r) => r.roomId === 7
      ? { ...r, occupiedReports: { count: 1, lastReportedAt: '2026-11-09T17:40:00Z' } } : r)
    const ranked = rankRooms(withReport, BUILDINGS, { latitude: 43.77317, longitude: -79.50518 })
    expect(ranked.map((r) => r.room)).toEqual(['C', 'B', 'A'])
  })

  it('puts buildings without coordinates after located ones', () => {
    const unknown = { ...rooms[0]!, roomId: 99, building: 'ZZZ', room: '1' }
    const ranked = rankRooms([unknown, ...rooms], BUILDINGS, LAS)
    expect(ranked.at(-1)!.building).toBe('ZZZ')
    expect(ranked.at(-1)!.distanceMetres).toBeNull()
  })

  it('knows when you are nowhere near campus', () => {
    expect(nearestBuildingMetres(BUILDINGS, YORK_HALL_GLENDON)! / 1000).toBeGreaterThan(10)
    expect(nearestBuildingMetres(BUILDINGS, LAS)).toBe(0)
  })
})
