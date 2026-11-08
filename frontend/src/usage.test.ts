import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch, everythingSent } from './test/fakeApi'
import { mondayOf, recordNearMe, recordVisit, resetUsageForTests, visitEvents } from './usage'

describe('visitEvents', () => {
  it('weeks start on Monday', () => {
    expect(mondayOf('2026-11-09')).toBe('2026-11-09') // Monday
    expect(mondayOf('2026-11-15')).toBe('2026-11-09') // Sunday
    expect(mondayOf('2027-01-01')).toBe('2026-12-28') // across a year boundary
  })

  it.each([
    ['first ever visit', null, ['visit', 'daily_visitor', 'weekly_visitor']],
    ['again the same day', '2026-11-11', ['visit']],
    ['new day, same week', '2026-11-09', ['visit', 'daily_visitor']],
    ['new week', '2026-11-08', ['visit', 'daily_visitor', 'weekly_visitor']],
  ])('%s', (_name, last, expected) => {
    expect(visitEvents('2026-11-11', last)).toEqual(expected)
  })
})

describe('recordVisit', () => {
  const WED_10AM_TORONTO = new Date('2026-11-11T15:00:00Z')

  beforeEach(() => {
    resetUsageForTests()
    localStorage.clear()
  })

  it('sends only event names and remembers only the date, on this device', () => {
    const fetchMock = fakeFetch(() => ({ status: 204, body: null }))
    recordVisit(WED_10AM_TORONTO)

    expect(everythingSent(fetchMock)).toBe(
      'POST /api/usage {"events":["visit","daily_visitor","weekly_visitor"]} {"Content-Type":"application/json"}')
    expect(localStorage.getItem('yuroom.lastVisit')).toBe('2026-11-11')
    expect(Object.keys(localStorage)).toEqual(['yuroom.lastVisit']) // nothing else stored
  })

  it('counts once per page load', () => {
    const fetchMock = fakeFetch(() => ({ status: 204, body: null }))
    recordVisit(WED_10AM_TORONTO)
    recordVisit(WED_10AM_TORONTO)
    recordNearMe()
    recordNearMe()
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(String(fetchMock.mock.calls[1]![1]!.body)).toBe('{"events":["near_me"]}')
  })

  it('uses the campus date, not the phone’s', () => {
    fakeFetch(() => ({ status: 204, body: null }))
    recordVisit(new Date('2026-11-12T03:30:00Z')) // 22:30 Wednesday in Toronto (Thursday in Auckland/UTC)
    expect(localStorage.getItem('yuroom.lastVisit')).toBe('2026-11-11')
  })

  it('respects Global Privacy Control', () => {
    const fetchMock = fakeFetch(() => ({ status: 204, body: null }))
    vi.stubGlobal('navigator', Object.assign(Object.create(navigator), { globalPrivacyControl: true }))
    recordVisit(WED_10AM_TORONTO)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('never breaks the app when the server is down', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('offline'))))
    expect(() => recordVisit(WED_10AM_TORONTO)).not.toThrow()
    await Promise.resolve()
  })
})
