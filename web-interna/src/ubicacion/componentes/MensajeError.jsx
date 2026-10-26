import Aviso from './Aviso.jsx';
import Boton from './Boton.jsx';

export default function MensajeError({ texto, onReintentar }) {
  return (
    <div className="ub-bloque-error">
      <Aviso tipo="error">{texto}</Aviso>
      {onReintentar ? <Boton onClick={onReintentar}>Reintentar</Boton> : null}
    </div>
  );
}
