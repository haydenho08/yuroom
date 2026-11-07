import { Link, Route, Routes } from 'react-router'
import { FreeRoomsPage } from './pages/FreeRoomsPage'
import { RoomPage } from './pages/RoomPage'

export function App() {
  return (
    <div className="app">
      <header className="topbar">
        <Link to="/" className="wordmark" aria-label="YURoom home">
          YU<span>Room</span>
        </Link>
        <span className="tagline">free classrooms, right now</span>
      </header>

      <main className="content">
        <Routes>
          <Route path="/" element={<FreeRoomsPage />} />
          <Route path="/rooms/:id" element={<RoomPage />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>

      <footer className="footer">
        <p>
          <strong>Student-built, not affiliated with York University.</strong> Based on the class
          timetable only. Rooms can still be booked for events, locked, or full of people studying.
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
