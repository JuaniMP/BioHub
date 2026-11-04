import Aviso from './Aviso.jsx';
import Boton from './Boton.jsx';

export default function MensajeError({ texto, onReintentar, deshabilitarReintento = false, textoBoton = 'Reintentar', children }) {
  return (
    <div className="bloque-error">
      <Aviso tipo="error">{texto}</Aviso>
      {onReintentar ? (
        <Boton onClick={onReintentar} disabled={deshabilitarReintento}>{textoBoton}</Boton>
      ) : null}
      {children}
    </div>
  );
}
