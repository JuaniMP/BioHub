import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { useEffect, useRef } from 'react';
import { MapContainer, Marker, TileLayer, useMap } from 'react-leaflet';
import { etiquetaEstado, normalizarEstado } from '../utilidades/formato.js';

export const CENTRO_COLOMBIA = [4.5, -74.1];
export const ZOOM_INICIAL = 5;
/** Máximo acercamiento: limita lo que las teselas de OSM pueden revelar (02 §7.3). */
export const ZOOM_MAXIMO = 12;
export const MAX_ERRORES_TESELAS = 3;
const URL_TESELAS = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const ATRIBUCION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

// Iconos DOM (enfocables con teclado). Forma distinta por estado: no se depende solo del color.
const ICONOS = Object.fromEntries(
  ['EN_PELIGRO', 'NO_AMENAZADA', 'SIN_EVALUAR'].map((e) => [
    e,
    L.divIcon({ className: `ub-marcador ub-marcador--${e.toLowerCase().replace('_', '-')}`, html: '<span></span>', iconSize: [20, 20], iconAnchor: [10, 10] }),
  ]),
);

function AjustarVista({ puntos }) {
  const mapa = useMap();
  useEffect(() => {
    if (puntos.length === 0) return;
    mapa.fitBounds(puntos.map((p) => p.posicion), { maxZoom: ZOOM_MAXIMO, padding: [32, 32] });
  }, [puntos, mapa]);
  return null;
}

/** Mapa Leaflet con teselas OSM. Recibe puntos ya convertidos a [lat, lon]. */
export default function MapaHallazgos({ puntos, seleccionadoId, onSeleccionar, onTeselasFallidas }) {
  const errores = useRef(0);
  const manejadores = {
    tileerror: () => {
      errores.current += 1;
      if (errores.current === MAX_ERRORES_TESELAS + 1) onTeselasFallidas?.();
    },
  };
  return (
    <div role="region" aria-label="Mapa de hallazgos" className="ub-mapa">
      <p className="ub-solo-lectores">Mapa con {puntos.length} hallazgos</p>
      <MapContainer center={CENTRO_COLOMBIA} zoom={ZOOM_INICIAL} maxZoom={ZOOM_MAXIMO} minZoom={3} scrollWheelZoom keyboard style={{ height: '100%', width: '100%' }}>
        <TileLayer url={URL_TESELAS} attribution={ATRIBUCION} maxZoom={ZOOM_MAXIMO} eventHandlers={manejadores} />
        <AjustarVista puntos={puntos} />
        {puntos.map((p) => (
          <Marker
            key={p.id}
            position={p.posicion}
            icon={ICONOS[normalizarEstado(p.estadoConservacion)]}
            keyboard
            title={`${p.nombreCientifico} (${etiquetaEstado(p.estadoConservacion)})`}
            zIndexOffset={p.id === seleccionadoId ? 1000 : 0}
            eventHandlers={{ click: (e) => onSeleccionar(p.id, e.target.getElement?.()) }}
          />
        ))}
      </MapContainer>
    </div>
  );
}
