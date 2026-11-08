import { Link, Route, Routes } from 'react-router'
import type { SiteInfo } from './api'
import { FreeRoomsPage } from './pages/FreeRoomsPage'
import { RoomPage } from './pages/RoomPage'
import { StatsPage } from './pages/StatsPage'
import { useApi } from './useApi'

export function App() {
  const info = useApi<SiteInfo>('/api/info')

  return (
    <div className="app">
      <header className="topbar">
        <Link to="/" className="wordmark" aria-label="YURoom home">
          YU<span>Room</span>
        </Link>
        <span className="tagline">free classrooms, right now</span>
      </header>

      {info.data?.demoData && (
        <p className="demo-banner" role="note">
          <strong>Demo data.</strong> These classes are made up, not York&apos;s real timetable. Don&apos;t
          walk to a room based on this yet.
        </p>
      )}

      <main className="content">
        <Routes>
          <Route path="/" element={<FreeRoomsPage />} />
          <Route path="/rooms/:id" element={<RoomPage />} />
          <Route path="/stats" element={<StatsPage />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>

      <footer className="footer">
        <p>
          <strong>Student-built, not affiliated with York University.</strong> Based on the class
          timetable only. Rooms can still be booked for events, locked, or full of people studying.
        </p>
        <p className="footer__links">
          No accounts, no cookies, no tracking. <Link to="/stats">Usage stats</Link>
        </p>
      </footer>
    </div>
  )
}

function NotFound() {
  return (
    <section className="state">
      <h1>Page not found</h1>
      <Link to="/" className="button">See free rooms</Link>
    </section>
  )
}
