/** Único rol que puede ver coordenadas exactas (D21). */
export const ROL_ALTO_RANGO = 'cientifico_alto_rango';

export function esAltoRango(roles) {
  return Array.isArray(roles) && roles.includes(ROL_ALTO_RANGO);
}
