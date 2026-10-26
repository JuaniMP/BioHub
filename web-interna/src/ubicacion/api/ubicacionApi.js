import { geoJsonALeaflet } from '../utilidades/geo.js';

/** Error de la API: `status` (0 = sin respuesta), `codigo` estable del contrato (problem+json). */
export class ErrorApi extends Error {
  constructor(status, codigo = null) {
    super(`La API respondió ${status}`);
    this.name = 'ErrorApi';
    this.status = status;
    this.codigo = codigo;
  }
}

/**
 * Cliente HTTP inyectable. Contrato mínimo que espera el módulo:
 *   cliente.get(ruta, { signal }) -> Promise<json>   (rechaza con un error que tenga `.status`)
 * `crearClienteUbicacion` da una implementación lista con `fetch`; Santiago solo aporta
 * `apiUrl` y `obtenerToken` (por ejemplo: async () => { await keycloak.updateToken(30); return keycloak.token; }).
 */
export function crearClienteUbicacion({ apiUrl, obtenerToken }) {
  return {
    async get(ruta, { signal } = {}) {
      let token;
      try {
        token = await obtenerToken();
      } catch {
        throw new ErrorApi(401);
      }
      let r;
      try {
        r = await fetch(`${apiUrl}${ruta}`, {
          headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
          cache: 'no-store',
          credentials: 'omit',
          signal,
        });
      } catch (e) {
        if (e?.name === 'AbortError') throw e;
        throw new ErrorApi(0);
      }
      if (!r.ok) {
        let codigo = null;
        try {
          codigo = (await r.json())?.codigo ?? null;
        } catch {
          // el 401 no lleva cuerpo
        }
        throw new ErrorApi(r.status, codigo);
      }
      return r.json();
    },
  };
}

/** Normaliza cualquier fallo del cliente a { status, codigo } sin depender del tipo de error. */
export function estadoDeError(e) {
  return { name: e?.name, status: Number.isInteger(e?.status) ? e.status : 0, codigo: e?.codigo ?? null };
}

function consulta(filtros, extra = {}) {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries({ ...filtros, ...extra })) {
    if (v !== '' && v != null) p.set(k, String(v));
  }
  return p.toString();
}

const numero = (v) => (typeof v === 'number' && Number.isFinite(v) ? v : null);

/**
 * Adaptador de una fila de la búsqueda. Defensa en profundidad (02 §3.4, D21): si el rol no es
 * alto rango, la fila resultante NO contiene latitud ni longitud, venga lo que venga en la respuesta.
 */
export function aHallazgo(crudo, altoRango) {
  const h = {
    id: crudo?.id ?? null,
    nombreCientifico: crudo?.nombreCientifico ?? '',
    fechaRecoleccion: crudo?.fechaRecoleccion ?? '',
    investigador: crudo?.investigador ?? '',
    departamento: crudo?.departamento ?? '',
    region: crudo?.region ?? '',
    estadoConservacion: crudo?.estadoConservacion ?? 'SIN_EVALUAR',
    estadoCoordenadas: 'PROTEGIDAS',
    latitud: null,
    longitud: null,
  };
  if (!altoRango) return h; // siempre "Restringido"
  const lat = numero(crudo?.latitud);
  const lon = numero(crudo?.longitud);
  if (lat !== null && lon !== null) {
    h.estadoCoordenadas = 'VISIBLES';
    h.latitud = lat;
    h.longitud = lon;
  } else {
    h.estadoCoordenadas = crudo?.estadoCoordenadas === 'NO_DISPONIBLES' ? 'NO_DISPONIBLES' : 'SIN_COORDENADAS';
  }
  return h;
}

export async function buscarHallazgos(cliente, { filtros, page, size, altoRango }, { signal } = {}) {
  const d = await cliente.get(`/api/cientifico/ubicacion/hallazgos?${consulta(filtros, { page, size })}`, { signal });
  const contenido = Array.isArray(d?.content) ? d.content : [];
  return {
    hallazgos: contenido.map((c) => aHallazgo(c, altoRango)),
    page: Number.isInteger(d?.page) ? d.page : page,
    total: Number.isFinite(d?.total) ? d.total : contenido.length,
    totalPaginas: Number.isFinite(d?.totalPaginas) ? d.totalPaginas : 1,
    omitidos: altoRango && Number.isFinite(d?.omitidos) ? d.omitidos : 0,
  };
}

/** Solo se llama para alto rango (la página lo comprueba antes). */
export async function obtenerMapa(cliente, { filtros, hallazgoId }, { signal } = {}) {
  const parametros = hallazgoId
    ? consulta({}, { hallazgoId })
    : consulta({ especie: filtros.especie, departamento: filtros.departamento, desde: filtros.desde, hasta: filtros.hasta });
  const d = await cliente.get(`/api/cientifico/ubicacion/mapa${parametros ? `?${parametros}` : ''}`, { signal });
  const features = Array.isArray(d?.features) ? d.features : [];
  const puntos = [];
  for (const f of features) {
    const posicion = geoJsonALeaflet(f?.geometry?.coordinates); // [lon, lat] -> [lat, lon]
    if (!posicion) continue;
    const p = f.properties ?? {};
    puntos.push({
      id: p.hallazgoId ?? f.id,
      posicion,
      nombreCientifico: p.nombreCientifico ?? '',
      fechaRecoleccion: p.fechaRecoleccion ?? '',
      investigador: p.investigador ?? '',
      departamento: p.departamento ?? '',
      region: p.region ?? '',
      estadoConservacion: p.estadoConservacion ?? 'SIN_EVALUAR',
    });
  }
  return {
    puntos,
    total: Number.isFinite(d?.total) ? d.total : puntos.length,
    truncado: d?.truncado === true,
    omitidos: Number.isFinite(d?.omitidos) ? d.omitidos : 0,
    limite: Number.isFinite(d?.limite) ? d.limite : 1000,
  };
}
