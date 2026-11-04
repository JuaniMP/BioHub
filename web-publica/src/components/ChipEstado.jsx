import { etiquetaEstado, normalizarEstado } from '../utilidades/formato.js';
import { IconoAdvertencia, IconoPregunta, IconoVerificado } from './Iconos.jsx';

const ICONOS = { EN_PELIGRO: IconoAdvertencia, NO_AMENAZADA: IconoVerificado, SIN_EVALUAR: IconoPregunta };

/** Estado de conservación: texto + icono + color (el color nunca va solo). */
export default function ChipEstado({ estado }) {
  const clave = normalizarEstado(estado);
  const Icono = ICONOS[clave];
  return (
    <span className={`chip chip-${clave.toLowerCase().replace('_', '-')}`}>
      <Icono />
      {etiquetaEstado(estado)}
    </span>
  );
}
