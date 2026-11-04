export const FILTROS_VACIOS = { especie: '', desde: '', hasta: '', investigador: '', departamento: '' };

const FECHA = /^\d{4}-\d{2}-\d{2}$/;
const fechaValida = (s) => FECHA.test(s) && !Number.isNaN(Date.parse(`${s}T00:00:00Z`));

/** Valida con las mismas reglas del servidor. Devuelve { campo: mensaje } (vacío si todo está bien). */
export function validarFiltros(f, { conInvestigador = true } = {}) {
  const errores = {};
  const especie = f.especie.trim();
  if (especie && especie.length < 2) errores.especie = 'Escribe al menos 2 letras.';
  else if (especie.length > 100) errores.especie = 'Usa máximo 100 caracteres.';
  if (conInvestigador && f.investigador.trim().length > 100) errores.investigador = 'Usa máximo 100 caracteres.';
  if (f.desde && !fechaValida(f.desde)) errores.desde = 'Escribe una fecha válida (día/mes/año).';
  if (f.hasta && !fechaValida(f.hasta)) errores.hasta = 'Escribe una fecha válida (día/mes/año).';
  if (!errores.desde && !errores.hasta && f.desde && f.hasta && f.desde > f.hasta) {
    errores.hasta = 'La fecha «Desde» no puede ser posterior a la fecha «Hasta».';
  }
  return errores;
}

export function limpiarFiltros(f) {
  return Object.fromEntries(Object.entries(f).map(([k, v]) => [k, typeof v === 'string' ? v.trim() : v]));
}
