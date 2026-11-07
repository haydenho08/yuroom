import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { App } from '../App'
import { fakeFetch, requestedUrls, scheduleResponse } from '../test/fakeApi'

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}

describe('RoomPage', () => {
  it('shows the day as alternating free and class blocks in time order', async () => {
    const fetchMock = fakeFetch(() => ({ body: scheduleResponse() }))
    renderAt('/rooms/1?date=2026-11-09')

    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('LAS A')
    expect(requestedUrls(fetchMock)).toEqual(['/api/rooms/1/schedule?date=2026-11-09'])

    const blocks = screen.getAllByRole('listitem').map((li) => li.textContent)
    expect(blocks).toEqual([
      'Free7–8:30 am1h 30m',
      'Class8:30–11:30 am3h',
      'Free11:30 am–1 pm1h 30m',
      'Class1–2 pm1h',
      'Free2–11 pm9h',
    ])
    expect(screen.getByText('Mon, Nov 9')).toBeInTheDocument()
  })

  it('links to the previous and next day', async () => {
    fakeFetch(() => ({ body: scheduleResponse({ date: '2026-11-30' }) }))
    renderAt('/rooms/1?date=2026-11-30')
    expect(await screen.findByRole('link', { name: 'Next day' })).toHaveAttribute('href', '/rooms/1?date=2026-12-01')
    expect(screen.getByRole('link', { name: 'Previous day' })).toHaveAttribute('href', '/rooms/1?date=2026-11-29')
  })

  it('explains exam days instead of drawing a misleading timetable', async () => {
    fakeFetch(() => ({
      body: scheduleResponse({
        status: 'EXAMS', booked: [], free: [], notice: "Fall examinations: the class timetable doesn't apply",
      }),
    }))
    renderAt('/rooms/1?date=2026-12-15')
    expect(await screen.findByRole('status')).toHaveTextContent('Fall examinations')
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
  })

  it('shows the server message for an unknown room', async () => {
    fakeFetch(() => ({ status: 404, body: { detail: 'No room with id 999' } }))
    renderAt('/rooms/999')
    expect(await screen.findByRole('alert')).toHaveTextContent('No room with id 999')
    expect(screen.getByRole('link', { name: 'Back to free rooms' })).toHaveAttribute('href', '/')
  })
})
