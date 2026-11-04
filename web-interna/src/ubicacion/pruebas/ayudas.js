import { vi } from 'vitest';

export const ALTO = ['cientifico_alto_rango'];
export const INVESTIGADOR = ['investigador_campo'];

export const HALLAZGO = {
  id: '3f2a9c1e-5b7d-4e8a-9f10-2c3d4e5f6a7b',
  nombreCientifico: 'Atelopus ebenoides',
  fechaRecoleccion: '2026-03-14',
  investigador: 'investigador1',
  departamento: 'Boyacá',
  departamentoCodigo: '15',
  region: 'Andina',
  estadoConservacion: 'EN_PELIGRO',
};

export const filaAlto = (extra = {}) => ({ ...HALLAZGO, estadoCoordenadas: 'VISIBLES', ubicacionProtegida: false, latitud: 5.1234567, longitud: -73.1234567, ...extra });
export const filaProtegida = (extra = {}) => ({ ...HALLAZGO, estadoCoordenadas: 'PROTEGIDAS', ubicacionProtegida: true, ...extra });

export const pagina = (content, extra = {}) => ({ content, page: 0, size: 20, total: content.length, totalPaginas: 1, omitidos: 0, ...extra });

export class ErrorHttp extends Error {
  constructor(status, codigo = null) {
    super(`HTTP ${status}`);
    this.status = status;
    this.codigo = codigo;
  }
}

/** Cliente simulado: `responder(ruta)` devuelve el JSON o lanza. */
export function clienteFalso(responder) {
  return { get: vi.fn((ruta, op) => Promise.resolve().then(() => responder(ruta, op))) };
}

export const feature = (id, lon, lat, extra = {}) => ({
  type: 'Feature',
  id,
  geometry: { type: 'Point', coordinates: [lon, lat] },
  properties: { ...HALLAZGO, hallazgoId: id, ...extra },
});

export const mapa = (features, extra = {}) => ({
  type: 'FeatureCollection', features, total: features.length, devueltos: features.length, truncado: false, omitidos: 0, limite: 1000, ...extra,
});
