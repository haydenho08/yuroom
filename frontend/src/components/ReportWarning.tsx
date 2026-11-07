import type { OccupiedReports } from '../api'
import { formatAgo } from '../format'

/** "Reported occupied 12 min ago (2 people)": a warning, not a verdict. */
export function ReportWarning({ reports, compact = false }: { reports: OccupiedReports; compact?: boolean }) {
  const who = reports.count > 1 ? ` by ${reports.count} people` : ''
  return (
    <span className={compact ? 'report report--compact' : 'report'}>
      <span aria-hidden="true">⚠</span> Reported occupied{who} {formatAgo(reports.lastReportedAt)}
    </span>
  )
}
