import Boton from './Boton.jsx';
import { IconoLupa } from './Iconos.jsx';

export default function EstadoVacio({ titulo, ayuda, textoAccion, onAccion }) {
  return (
    <div className="estado-vacio" role="status">
      <IconoLupa />
      <h2>{titulo}</h2>
      <p>{ayuda}</p>
      {onAccion ? <Boton variante="secundario" onClick={onAccion}>{textoAccion}</Boton> : null}
    </div>
  );
}
