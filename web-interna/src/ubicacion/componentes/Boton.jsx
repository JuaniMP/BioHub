export default function Boton({ variante = 'primario', type = 'button', className = '', ...resto }) {
  return <button type={type} className={`ub-boton ub-boton--${variante} ${className}`.trim()} {...resto} />;
}
