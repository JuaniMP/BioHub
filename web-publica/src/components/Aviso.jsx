import { IconoAdvertencia, IconoInfo } from './Iconos.jsx';

/** Aviso en línea. info/advertencia usan role=status; error usa role=alert. */
export default function Aviso({ tipo = 'info', titulo, children, ...resto }) {
  const rol = tipo === 'error' ? 'alert' : 'status';
  const Icono = tipo === 'info' ? IconoInfo : IconoAdvertencia;
  return (
    <div role={rol} className={`aviso aviso-${tipo}`} {...resto}>
      <Icono />
      <div>
        {titulo ? <p className="aviso-titulo">{titulo}</p> : null}
        <p>{children}</p>
      </div>
    </div>
  );
}
