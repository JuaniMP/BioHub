import { getJson } from './cliente.js';

/**
 * Adaptador de la API pública. LISTA BLANCA (02 §9.1): se construye un objeto nuevo solo con
 * estas claves; cualquier otro campo que llegue (por error) se descarta aquí y nunca llega a la UI.
 */
const CAMPOS_PUBLICOS = [
  'id', 'nombreCientifico', 'nombreComun', 'familia', 'tipoEspecimen',
  'fechaRecoleccion', 'estadoConservacion', 'departamento', 'region', 'descripcion',
];

const textoOnulo = (v) => (typeof v === 'string' && v.trim() !== '' ? v : null);

export function aFichaPublica(crudo) {
  const origen = crudo && typeof crudo === 'object' ? crudo : {};
  const ficha = {};
  for (const campo of CAMPOS_PUBLICOS) ficha[campo] = textoOnulo(origen[campo]);
  return ficha;
}

export const TAMANO_PAGINA = 20;

export async function buscarFichas({ q, familia, departamento, page = 0 }, { signal } = {}) {
  const p = new URLSearchParams();
  if (q) p.set('q', q);
  if (familia) p.set('familia', familia);
  if (departamento) p.set('departamento', departamento);
  p.set('page', String(page));
  p.set('size', String(TAMANO_PAGINA));
  const d = await getJson(`/api/publica/fichas?${p}`, { signal });
  const contenido = Array.isArray(d?.content) ? d.content : [];
  return {
    fichas: contenido.map(aFichaPublica),
    page: Number.isInteger(d?.page) ? d.page : page,
    total: Number.isFinite(d?.total) ? d.total : contenido.length,
    totalPaginas: Number.isFinite(d?.totalPaginas) ? d.totalPaginas : 1,
    modoDegradado: d?.modoDegradado === true,
    filtrosIgnorados: Array.isArray(d?.filtrosIgnorados) ? d.filtrosIgnorados.filter((x) => typeof x === 'string') : [],
  };
}

export async function obtenerFicha(id, { signal } = {}) {
  const d = await getJson(`/api/publica/fichas/${encodeURIComponent(id)}`, { signal });
  return aFichaPublica(d);
}
