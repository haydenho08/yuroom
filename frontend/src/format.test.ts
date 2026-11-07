import { describe, expect, it } from 'vitest'
import {
  addDays, campusNow, formatAgo, formatCampusDateTime, formatClock, formatDate, formatDuration, formatRange,
} from './format'

describe('formatDuration', () => {
  it.each([
    [0, '0m'], [45, '45m'], [60, '1h'], [105, '1h 45m'], [795, '13h 15m'], [59.9, '59m'], [-5, '0m'],
  ])('%s minutes -> %s', (minutes, expected) => {
    expect(formatDuration(minutes)).toBe(expected)
  })
})

describe('formatClock', () => {
  it.each([
    ['14:30', '2:30 pm'], ['23:00', '11 pm'], ['07:05', '7:05 am'], ['12:00', '12 pm'], ['00:15', '12:15 am'],
  ])('%s -> %s', (input, expected) => {
    expect(formatClock(input)).toBe(expected)
  })
})

describe('formatRange', () => {
  it('drops the repeated am/pm', () => expect(formatRange('08:30', '10:00')).toBe('8:30–10 am'))
  it('keeps both when crossing noon', () => expect(formatRange('11:00', '13:30')).toBe('11 am–1:30 pm'))
})

describe('campus dates and times ignore the device time zone', () => {
  // vitest runs with TZ=Pacific/Auckland (see vite.config.ts), 18 hours ahead of Toronto.
  it('formats the wall clock sent by the server', () => {
    expect(formatCampusDateTime('2026-11-09T23:30:00-05:00')).toBe('Mon, Nov 9 · 11:30 pm')
  })

  it('formats plain dates without shifting the day', () => {
    expect(formatDate('2026-11-09')).toBe('Mon, Nov 9')
  })

  it('adds days across DST and month ends', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01')
    expect(addDays('2026-11-01', 1)).toBe('2026-11-02')
    expect(addDays('2027-01-01', -1)).toBe('2026-12-31')
  })

  it('computes campus "now" from an instant', () => {
    // 03:30 UTC on Nov 10 is 22:30 on Nov 9 in Toronto (and 16:30 Nov 10 in Auckland).
    expect(campusNow(new Date('2026-11-10T03:30:00Z'))).toEqual({ date: '2026-11-09', time: '22:30' })
    // Summer: Toronto is UTC-4.
    expect(campusNow(new Date('2026-07-01T12:00:00Z'))).toEqual({ date: '2026-07-01', time: '08:00' })
  })
})

describe('formatAgo', () => {
  const now = new Date('2026-11-09T15:00:00Z')
  it.each([
    ['2026-11-09T14:59:30Z', 'just now'],
    ['2026-11-09T14:40:00Z', '20 min ago'],
    ['2026-11-09T12:00:00Z', '3h ago'],
    ['2026-11-06T15:00:00Z', '3 days ago'],
  ])('%s -> %s', (instant, expected) => {
    expect(formatAgo(instant, now)).toBe(expected)
  })
})
