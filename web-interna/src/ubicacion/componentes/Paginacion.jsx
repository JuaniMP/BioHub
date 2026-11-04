/** `pagina` es base 0 (como la API). Misma interfaz que la del portal público. */
export default function Paginacion({ pagina, totalPaginas, onCambiar }) {
  if (!totalPaginas || totalPaginas <= 1) return null;
  const hayAnterior = pagina > 0;
  const haySiguiente = pagina < totalPaginas - 1;
  return (
    <nav aria-label="Paginación" className="ub-paginacion">
      <button type="button" className="ub-boton ub-boton--secundario" aria-disabled={!hayAnterior} onClick={() => hayAnterior && onCambiar(pagina - 1)}>
        Anterior
      </button>
      <span aria-current="page" className="ub-paginacion__actual">Página {pagina + 1} de {totalPaginas}</span>
      <button type="button" className="ub-boton ub-boton--secundario" aria-disabled={!haySiguiente} onClick={() => haySiguiente && onCambiar(pagina + 1)}>
        Siguiente
      </button>
    </nav>
  );
}
