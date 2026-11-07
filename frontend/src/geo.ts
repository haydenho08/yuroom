/** Pure geometry for "near me". Runs only in the browser; coordinates never leave the device. */

export interface Point {
  latitude: number
  longitude: number
}

const EARTH_RADIUS_M = 6_371_000

/** Great-circle ("as the crow flies") distance in metres, haversine formula. */
export function distanceMetres(a: Point, b: Point): number {
  const rad = (deg: number) => (deg * Math.PI) / 180
  const dLat = rad(b.latitude - a.latitude)
  const dLon = rad(b.longitude - a.longitude)
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.latitude)) * Math.cos(rad(b.latitude)) * Math.sin(dLon / 2) ** 2
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)))
}

/**
 * Rough walking time: paths aren't straight lines, so add 30% to the crow-flies distance,
 * at an average 80 m per minute. Always at least 1 minute.
 */
export function walkingMinutes(metres: number): number {
  return Math.max(1, Math.ceil((metres * 1.3) / 80))
}

/** 40 -> "40 m", 351 -> "350 m", 1234 -> "1.2 km". */
export function formatDistance(metres: number): string {
  if (metres < 1000) return `${Math.max(10, Math.round(metres / 10) * 10)} m`
  return `${(metres / 1000).toFixed(1)} km`
}
