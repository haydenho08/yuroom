import { useCallback, useEffect, useRef, useState } from 'react'
import type { Point } from './geo'

export type LocationState =
  | { kind: 'off' }
  | { kind: 'locating' }
  | { kind: 'ready'; point: Point; accuracyMetres: number }
  | { kind: 'denied' }
  | { kind: 'unavailable'; message: string }

/**
 * The device's position, only after the user asks for it, and only in memory.
 *
 * Privacy by design: the position is never sent to the server, put in the URL, or written to
 * localStorage. Turning "near me" off (or leaving the page) stops the watch and forgets it.
 * It's watched rather than read once because students are walking between buildings.
 */
export function useLocation() {
  const [state, setState] = useState<LocationState>({ kind: 'off' })
  const watchId = useRef<number | null>(null)

  const stop = useCallback(() => {
    if (watchId.current !== null) navigator.geolocation.clearWatch(watchId.current)
    watchId.current = null
    setState({ kind: 'off' })
  }, [])

  const start = useCallback(() => {
    if (!('geolocation' in navigator)) {
      setState({ kind: 'unavailable', message: "This browser can't share its location." })
      return
    }
    setState({ kind: 'locating' })
    watchId.current = navigator.geolocation.watchPosition(
      (pos) => setState({
        kind: 'ready',
        point: { latitude: pos.coords.latitude, longitude: pos.coords.longitude },
        accuracyMetres: pos.coords.accuracy,
      }),
      (err) => setState(err.code === err.PERMISSION_DENIED
        ? { kind: 'denied' }
        : { kind: 'unavailable', message: "Couldn't get your location. Try again outside or near a window." }),
      // Building-level precision is plenty; don't drain the battery for GPS-grade fixes.
      { enableHighAccuracy: false, maximumAge: 30_000, timeout: 15_000 },
    )
  }, [])

  useEffect(() => () => {
    if (watchId.current !== null) navigator.geolocation.clearWatch(watchId.current)
  }, [])

  return { state, start, stop }
}
