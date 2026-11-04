import Boton from './Boton.jsx';
import { IconoLupa } from './Iconos.jsx';

export default function EstadoVacio({ titulo, ayuda, onLimpiar }) {
  return (
    <div className="ub-vacio" role="status">
      <IconoLupa />
      <p className="ub-vacio__titulo">{titulo}</p>
      <p>{ayuda}</p>
      {onLimpiar ? <Boton variante="secundario" onClick={onLimpiar}>Limpiar filtros</Boton> : null}
    </div>
  );
}
