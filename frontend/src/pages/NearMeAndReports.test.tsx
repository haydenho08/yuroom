import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { App } from '../App'
import {
  BUILDINGS, everythingSent, fakeFetch, fakeGeolocation, freeResponse, scheduleResponse,
} from '../test/fakeApi'
import { campusNow } from '../format'

function renderAt(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
}

function freeRoomsApi(free = freeResponse()) {
  return fakeFetch((url) => {
    if (url.pathname === '/api/buildings') return { body: BUILDINGS }
    if (url.pathname === '/api/rooms/free') return { body: free }
    return undefined
  })
}

/** A spot right outside Curtis Lecture Halls, with digits we can search for in requests. */
const AT_CLH = { latitude: 43.7731694, longitude: -79.5051794 }

describe('Near me', () => {
  it('asks for location only when tapped, then sorts by distance with walking times', async () => {
    freeRoomsApi()
    const geo = fakeGeolocation(AT_CLH)
    const user = userEvent.setup()
    renderAt('/')

    await screen.findByRole('heading', { name: '3 rooms free' })
    expect(geo.watchPosition).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Near me' }))

    const rows = within(screen.getByRole('list')).getAllByRole('link')
    expect(rows.map((a) => a.querySelector('.room__name')!.textContent)).toEqual(['CLH A', 'LAS C', 'LAS B'])
    expect(rows[0]).toHaveTextContent('10 m · 1 min walk')
    expect(rows[1]).toHaveTextContent(/[89]0 m · 2 min walk/)
    expect(screen.getByText(/Your location stays on this device/)).toBeInTheDocument()
  })

  it('never sends the location anywhere: not in URLs, bodies or headers', async () => {
    const fetchMock = freeRoomsApi()
    fakeGeolocation(AT_CLH)
    const user = userEvent.setup()
    renderAt('/')
    await user.click(await screen.findByRole('button', { name: 'Near me' }))
    await user.click(screen.getByRole('button', { name: 'LAS' })) // trigger more requests while located

    const sent = everythingSent(fetchMock)
    expect(sent).toContain('/api/rooms/free?building=LAS')
    expect(sent).not.toMatch(/43\.77|79\.50|lat|lon/i)
    expect(window.location.search).not.toMatch(/lat|lon|near/i)
  })

  it('turning it off stops watching and restores the server order', async () => {
    freeRoomsApi()
    const geo = fakeGeolocation(AT_CLH)
    const user = userEvent.setup()
    renderAt('/')
    const toggle = await screen.findByRole('button', { name: 'Near me' })
    await user.click(toggle)
    await user.click(toggle)

    expect(geo.clearWatch).toHaveBeenCalledWith(42)
    const rows = within(screen.getByRole('list')).getAllByRole('link')
    expect(rows[0]).toHaveTextContent('LAS C')
    expect(rows[0]).not.toHaveTextContent('walk')
  })

  it('explains a blocked permission instead of failing silently', async () => {
    freeRoomsApi()
    fakeGeolocation('denied')
    const user = userEvent.setup()
    renderAt('/')
    await user.click(await screen.findByRole('button', { name: 'Near me' }))
    expect(screen.getByText(/Location is blocked for this site/)).toBeInTheDocument()
  })

  it('notices when you are not on campus', async () => {
    freeRoomsApi()
    fakeGeolocation({ latitude: 43.6532, longitude: -79.3832 }) // downtown Toronto
    const user = userEvent.setup()
    renderAt('/')
    await user.click(await screen.findByRole('button', { name: 'Near me' }))
    expect(screen.getByText(/You seem to be off campus \(nearest building 1\d\.\d km away\)/)).toBeInTheDocument()
  })
})

describe('Occupancy reports', () => {
  it('shows a warning on reported rooms in the list', async () => {
    const minutesAgo = new Date(Date.now() - 12 * 60_000).toISOString()
    const free = freeResponse()
    free.rooms[2] = { ...free.rooms[2]!, occupiedReports: { count: 2, lastReportedAt: minutesAgo } }
    freeRoomsApi(free)
    renderAt('/')
    await screen.findByRole('heading', { name: '3 rooms free' }) // past the loading skeleton
    const rows = within(screen.getByRole('list')).getAllByRole('link')
    expect(rows[2]).toHaveTextContent('Reported occupied by 2 people 12 min ago')
    expect(rows[0]).not.toHaveTextContent('Reported')
  })

  it('reports a room in two taps, sending nothing but the room id', async () => {
    const today = campusNow().date
    let reported = false
    const fetchMock = fakeFetch((url, init) => {
      if (init?.method === 'POST' && url.pathname === '/api/rooms/1/reports') {
        reported = true
        return { status: 201, body: { roomId: 1, expiresAt: '', activeReports: 1, lastReportedAt: new Date().toISOString() } }
      }
      return {
        body: scheduleResponse({
          date: today,
          occupiedReports: reported ? { count: 1, lastReportedAt: new Date().toISOString() } : null,
        }),
      }
    })
    const user = userEvent.setup()
    renderAt(`/rooms/1?date=${today}`)

    await user.click(await screen.findByRole('button', { name: 'Room actually occupied?' }))
    expect(reported).toBe(false) // first tap only asks
    await user.click(screen.getByRole('button', { name: "Yes, it's occupied" }))

    expect(await screen.findByText(/Thanks! Others will see a warning/)).toBeInTheDocument()
    expect(await screen.findByText(/Reported occupied just now/)).toBeInTheDocument()
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(post[1]?.body).toBeUndefined()
  })

  it('shows the server message when rate-limited', async () => {
    const today = campusNow().date
    fakeFetch((_url, init) => init?.method === 'POST'
      ? { status: 429, body: { detail: 'You already reported this room recently.' } }
      : { body: scheduleResponse({ date: today }) })
    const user = userEvent.setup()
    renderAt(`/rooms/1?date=${today}`)
    await user.click(await screen.findByRole('button', { name: 'Room actually occupied?' }))
    await user.click(screen.getByRole('button', { name: "Yes, it's occupied" }))
    expect(await screen.findByRole('alert')).toHaveTextContent('You already reported this room recently.')
  })

  it('does not offer reporting for other days or exam days', async () => {
    fakeFetch(() => ({ body: scheduleResponse({ date: '2026-11-09' }) }))
    renderAt('/rooms/1?date=2026-11-09')
    await screen.findByRole('heading', { level: 1 })
    expect(screen.queryByRole('button', { name: 'Room actually occupied?' })).not.toBeInTheDocument()
  })
})
