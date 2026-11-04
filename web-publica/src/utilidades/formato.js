const ETIQUETAS_ESTADO = {
  EN_PELIGRO: 'En peligro',
  NO_AMENAZADA: 'No amenazada',
  SIN_EVALUAR: 'Sin evaluar',
};

/** Cualquier valor desconocido se trata como "Sin evaluar" (02 §5.1). */
export function normalizarEstado(valor) {
  return Object.hasOwn(ETIQUETAS_ESTADO, valor) ? valor : 'SIN_EVALUAR';
}

export const etiquetaEstado = (valor) => ETIQUETAS_ESTADO[normalizarEstado(valor)];

/** "2026-03-15" -> "15 de marzo de 2026" (sin desfase de zona horaria). */
export function formatearFecha(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso ?? '');
  if (!m) return iso ?? '';
  const fecha = new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3])));
  return fecha.toLocaleDateString('es-CO', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });
}

export function textoTotal(n) {
  return n === 1 ? '1 especie encontrada' : `${n} especies encontradas`;
}
