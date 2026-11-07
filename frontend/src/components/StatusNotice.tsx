import type { Status } from '../api'

const TONE: Record<Status, 'info' | 'warn'> = {
  OK: 'info',
  NO_CLASSES: 'info',
  OUTSIDE_HOURS: 'warn',
  CLOSED: 'warn',
  EXAMS: 'warn',
  OUTSIDE_TERM: 'warn',
}

/** The server's explanation of why the timetable does or doesn't apply. */
export function StatusNotice({ status, notice }: { status: Status; notice: string | null }) {
  if (!notice) return null
  return (
    <p className={`notice notice--${TONE[status]}`} role="status">
      {notice}
    </p>
  )
}
