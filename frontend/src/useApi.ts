import { useCallback, useEffect, useState } from 'react'
import { getJson } from './api'

export interface ApiState<T> {
  data: T | undefined
  error: Error | undefined
  loading: boolean
  reload: () => void
}

/**
 * Fetches JSON for {@code path} and keeps it fresh.
 * - Aborts the old request when the path changes, so a slow old answer can't overwrite a new one.
 * - Keeps showing the previous data while reloading (no flash of empty list).
 * - Optional polling, plus a refresh when the tab becomes visible again: phones put tabs to
 *   sleep, and "free until 2:30" from an hour ago is worse than no answer.
 */
export function useApi<T>(path: string | null, options: { refreshMs?: number } = {}): ApiState<T> {
  const { refreshMs } = options
  const [data, setData] = useState<T>()
  const [error, setError] = useState<Error>()
  const [tick, setTick] = useState(0)
  // "Loading" is derived, not stored: we're loading while the request we want (path + tick)
  // isn't the one that last settled. No setState in the effect body, so no extra render.
  const wanted = path === null ? null : `${tick}:${path}`
  const [settled, setSettled] = useState<string | null>(null)
  const reload = useCallback(() => setTick((t) => t + 1), [])

  useEffect(() => {
    if (wanted === null || path === null) return
    const controller = new AbortController()
    getJson<T>(path, controller.signal)
      .then((result) => {
        setData(result)
        setError(undefined)
        setSettled(wanted)
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setError(e instanceof Error ? e : new Error(String(e)))
        setSettled(wanted)
      })
    return () => controller.abort()
  }, [path, wanted])
  const loading = wanted !== null && settled !== wanted

  useEffect(() => {
    if (!refreshMs) return
    const timer = window.setInterval(reload, refreshMs)
    const onVisible = () => {
      if (document.visibilityState === 'visible') reload()
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [refreshMs, reload])

  return { data, error, loading, reload }
}
