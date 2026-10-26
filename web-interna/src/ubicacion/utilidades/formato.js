const ETIQUETAS = { EN_PELIGRO: 'En peligro', NO_AMENAZADA: 'No amenazada', SIN_EVALUAR: 'Sin evaluar' };

export const normalizarEstado = (v) => (Object.hasOwn(ETIQUETAS, v) ? v : 'SIN_EVALUAR');
export const etiquetaEstado = (v) => ETIQUETAS[normalizarEstado(v)];

/** "2026-03-15" -> "15/03/2026" */
export function fechaCorta(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso ?? '');
  return m ? `${m[3]}/${m[2]}/${m[1]}` : (iso ?? '');
}

export const textoHallazgos = (n) => (n === 1 ? '1 hallazgo' : `${n} hallazgos`);

/** Coordenada con 5 decimales (las coordenadas solo se muestran en texto, nunca en URL ni título). */
export const formatearCoordenada = (n) => Number(n).toFixed(5);
