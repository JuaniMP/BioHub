/** Marcador de carga. La animación se desactiva con prefers-reduced-motion (CSS). */
export default function Esqueleto({ tipo = 'tarjeta', cantidad = 1 }) {
  return Array.from({ length: cantidad }, (_, i) => (
    <div key={i} className={`esqueleto esqueleto-${tipo}`} aria-hidden="true" data-testid="esqueleto" />
  ));
}
