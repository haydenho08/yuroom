import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { App } from '../App'
import type { WeeklyUsageResponse } from '../api'
import { BUILDINGS, fakeFetch, freeResponse } from '../test/fakeApi'

/** The free-rooms page's API, plus /api/info saying whether data is synthetic. */
function siteApi(demoData: boolean) {
  return fakeFetch((url) => {
    if (url.pathname === '/api/info') return { body: { demoData } }
    if (url.pathname === '/api/buildings') return { body: BUILDINGS }
    if (url.pathname === '/api/rooms/free') return { body: freeResponse() }
    return undefined
  })
}

function renderAt(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
}

describe('demo data banner', () => {
  it('warns on every page while the timetable is synthetic', async () => {
    siteApi(true)
    renderAt('/')
    expect(await screen.findByRole('note')).toHaveTextContent(/Demo data.*made up, not York's real timetable/)
  })

  it('is absent with real data', async () => {
    siteApi(false)
    renderAt('/')
    await screen.findByRole('heading', { name: '3 rooms free' })
    expect(screen.queryByRole('note')).not.toBeInTheDocument()
  })
})

describe('StatsPage', () => {
  const usage: WeeklyUsageResponse = {
    timeZone: 'America/Toronto',
    note: 'Counts are per browser, not per person.',
    weeks: [
      { weekStart: '2026-11-02', visits: 900, weeklyVisitors: 310, avgDailyVisitors: 71.4, nearMeUses: 40, reports: 6, complete: true },
      { weekStart: '2026-11-09', visits: 250, weeklyVisitors: 120, avgDailyVisitors: 60, nearMeUses: 9, reports: 1, complete: false },
    ],
  }

  it('shows weekly numbers newest first, with the caveats', async () => {
    const fetchMock = fakeFetch((url) => url.pathname === '/api/usage/weekly' ? { body: usage } : undefined)
    renderAt('/stats')

    const rows = within(await screen.findByRole('table')).getAllByRole('row').slice(1)
    expect(rows.map((r) => r.textContent)).toEqual([
      'Mon, Nov 9 (so far)1206025091',
      'Mon, Nov 231071.4900406',
    ])
    expect(screen.getByText('Counts are per browser, not per person.')).toBeInTheDocument()
    expect(fetchMock.mock.calls.map(([u]) => String(u))).toContain('/api/usage/weekly?weeks=12')
  })

  it('is linked from the footer', async () => {
    siteApi(false)
    renderAt('/')
    expect(screen.getByRole('link', { name: 'Usage stats' })).toHaveAttribute('href', '/stats')
  })
})
