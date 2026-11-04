/** Botón con tipo explícito. variante: primario | secundario | enlace. */
export default function Boton({ variante = 'primario', type = 'button', cargando = false, children, className = '', ...resto }) {
  return (
    <button type={type} className={`boton boton-${variante} ${className}`.trim()} aria-busy={cargando || undefined} {...resto}>
      {children}
      {cargando ? '…' : null}
    </button>
  );
}
