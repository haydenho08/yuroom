import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { App } from '../App'
import { BUILDINGS, fakeFetch, freeResponse, requestedUrls } from '../test/fakeApi'

function renderAt(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}

function apiWith(free = freeResponse()) {
  return fakeFetch((url) => {
    if (url.pathname === '/api/buildings') return { body: BUILDINGS }
    if (url.pathname === '/api/rooms/free') return { body: free }
    return undefined
  })
}

describe('FreeRoomsPage', () => {
  it('lists free rooms in the order the server ranked them, with friendly times', async () => {
    apiWith()
    renderAt()

    const items = within(await screen.findByRole('list', { name: '' })).getAllByRole('link')
    expect(items.map((a) => a.textContent)).toEqual([
      expect.stringContaining('LAS C'),
      expect.stringContaining('CLH A'),
      expect.stringContaining('LAS B'),
    ])
    expect(items[1]).toHaveTextContent('Free until 2:30 pm · 1h 45m')
    expect(items[0]).toHaveTextContent('Free until 11 pm (closing) · 10h 15m')
    expect(items[1]).toHaveAttribute('href', '/rooms/7?date=2026-11-09')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('3 rooms free')
    expect(screen.getByText(/Mon, Nov 9 · 12:45 pm · timetable checked 3h ago/)).toBeInTheDocument()
  })

  it('flags short windows so nobody walks over for 15 minutes of study', async () => {
    apiWith()
    renderAt()
    expect(await screen.findByText('15m')).toHaveClass('short')
    expect(screen.getByText('1h 45m')).not.toHaveClass('short')
  })

  it('always shows the not-affiliated disclaimer', async () => {
    apiWith()
    renderAt()
    expect(screen.getByRole('contentinfo')).toHaveTextContent('Student-built, not affiliated with York University.')
  })

  it('filters by building and minimum length through the URL and the API query', async () => {
    const fetchMock = apiWith()
    const user = userEvent.setup()
    renderAt()

    await user.click(await screen.findByRole('button', { name: 'LAS' }))
    await user.click(screen.getByRole('button', { name: '1h+' }))

    expect(screen.getByRole('button', { name: 'LAS' })).toHaveAttribute('aria-pressed', 'true')
    expect(requestedUrls(fetchMock)).toContain('/api/rooms/free?building=LAS&minMinutes=60')
  })

  it('reads filters from a shared link', async () => {
    const fetchMock = apiWith()
    renderAt('/?building=CLH&min=30&at=2026-11-09T10:00')
    await screen.findByRole('heading', { level: 1 })
    expect(requestedUrls(fetchMock)).toContain('/api/rooms/free?building=CLH&at=2026-11-09T10%3A00&minMinutes=30')
    expect(screen.getByLabelText(/Check another time/)).toHaveValue('2026-11-09T10:00')
  })

  it('shows the server notice and no empty-state nagging during exams', async () => {
    apiWith(freeResponse({
      status: 'EXAMS',
      rooms: [],
      notice: "Fall examinations: the class timetable doesn't apply during exams",
    }))
    renderAt()
    expect(await screen.findByRole('status')).toHaveTextContent("doesn't apply during exams")
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Exam period')
    expect(screen.queryByText(/0 rooms free/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Try a shorter time/)).not.toBeInTheDocument()
  })

  it('suggests loosening filters when nothing matches', async () => {
    apiWith(freeResponse({ rooms: [] }))
    renderAt('/?building=LAS&min=120')
    expect(await screen.findByText(/No rooms free for 2h\+ in LAS right now/)).toBeInTheDocument()
    expect(screen.getByText(/Try a shorter time or another building/)).toBeInTheDocument()
  })

  it('shows the API error and lets the user retry', async () => {
    let fail = true
    fakeFetch((url) => {
      if (url.pathname === '/api/buildings') return { body: BUILDINGS }
      return fail ? { status: 503, body: { detail: 'Service unavailable' } } : { body: freeResponse() }
    })
    const user = userEvent.setup()
    renderAt()

    expect(await screen.findByRole('alert')).toHaveTextContent("Couldn't load rooms: Service unavailable")
    fail = false
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('3 rooms free')
  })
})
