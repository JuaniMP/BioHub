/**
 * ÚNICO punto de conversión de coordenadas del mapa.
 * GeoJSON entrega [longitud, latitud]; Leaflet espera [latitud, longitud].
 */
export function geoJsonALeaflet(coordinates) {
  if (!Array.isArray(coordinates) || coordinates.length < 2) return null;
  const [lon, lat] = coordinates;
  if (!Number.isFinite(lon) || !Number.isFinite(lat)) return null;
  if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
  return [lat, lon];
}
