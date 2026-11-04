import { IconoAdvertencia, IconoInfo } from './Iconos.jsx';

/** info/advertencia -> role=status; error -> role=alert. */
export default function Aviso({ tipo = 'info', children, ...resto }) {
  const Icono = tipo === 'info' ? IconoInfo : IconoAdvertencia;
  return (
    <div role={tipo === 'error' ? 'alert' : 'status'} className={`ub-aviso ub-aviso--${tipo}`} {...resto}>
      <Icono />
      <p>{children}</p>
    </div>
  );
}
