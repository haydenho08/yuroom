import type { WeeklyUsageResponse } from '../api'
import { formatDate } from '../format'
import { useApi } from '../useApi'

/**
 * Public usage numbers. If a number is on a resume, anyone can check where it came from, and this
 * page says exactly what is (and isn't) counted.
 */
export function StatsPage() {
  const usage = useApi<WeeklyUsageResponse>('/api/usage/weekly?weeks=12')
  const weeks = [...(usage.data?.weeks ?? [])].reverse() // newest first

  return (
    <section className="stats">
      <h1>Usage stats</h1>
      <p className="muted">
        Weekly totals, Monday to Sunday, campus time. YURoom keeps one number per day for each of these,
        and nothing else: no IP addresses, cookies, accounts or per-visit records.
      </p>

      {usage.error && !usage.data ? (
        <p role="alert">Couldn&apos;t load stats: {usage.error.message}</p>
      ) : !usage.data ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          <div className="table-scroll">
            <table className="stats__table">
              <thead>
                <tr>
                  <th scope="col">Week of</th>
                  <th scope="col">Weekly visitors</th>
                  <th scope="col">Daily avg</th>
                  <th scope="col">Visits</th>
                  <th scope="col">Near me</th>
                  <th scope="col">Reports</th>
                </tr>
              </thead>
              <tbody>
                {weeks.map((w) => (
                  <tr key={w.weekStart}>
                    <th scope="row">
                      {formatDate(w.weekStart)}
                      {!w.complete && <span className="muted"> (so far)</span>}
                    </th>
                    <td>{w.weeklyVisitors}</td>
                    <td>{w.avgDailyVisitors}</td>
                    <td>{w.visits}</td>
                    <td>{w.nearMeUses}</td>
                    <td>{w.reports}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="hint">{usage.data.note}</p>
          <p className="hint">Browsers with Global Privacy Control or Do Not Track turned on aren&apos;t counted at all.</p>
        </>
      )}
    </section>
  )
}
