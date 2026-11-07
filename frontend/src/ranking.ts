import type { Building, FreeRoom } from './api'
import { distanceMetres, type Point } from './geo'

export interface RankedRoom extends FreeRoom {
  /** Straight-line distance from the user to the room's building, or null if unknown. */
  distanceMetres: number | null
}

/**
 * Without a location: keep the server's order (unreported first, longest-free first).
 * With one: unreported rooms still come first, then nearest building, then the server's order
 * (Array.prototype.sort is stable, so equal distances keep longest-free first).
 */
export function rankRooms(rooms: FreeRoom[], buildings: Building[], here: Point | null): RankedRoom[] {
  const coords = new Map(buildings
    .filter((b) => b.latitude !== null && b.longitude !== null)
    .map((b) => [b.code, { latitude: b.latitude!, longitude: b.longitude! }]))

  const withDistance = rooms.map((r) => {
    const at = coords.get(r.building)
    return { ...r, distanceMetres: here && at ? distanceMetres(here, at) : null }
  })
  if (!here) return withDistance

  const reported = (r: RankedRoom) => (r.occupiedReports ? 1 : 0)
  return [...withDistance].sort((a, b) =>
    reported(a) - reported(b)
    || (a.distanceMetres ?? Infinity) - (b.distanceMetres ?? Infinity))
}

/** Distance to the closest known building, to notice when someone isn't on campus at all. */
export function nearestBuildingMetres(buildings: Building[], here: Point): number | null {
  const distances = buildings
    .filter((b) => b.latitude !== null && b.longitude !== null)
    .map((b) => distanceMetres(here, { latitude: b.latitude!, longitude: b.longitude! }))
  return distances.length ? Math.min(...distances) : null
}
