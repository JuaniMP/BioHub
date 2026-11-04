/** Paginación. `pagina` es base 0 (como la API); al usuario se le muestra base 1. */
export default function Paginacion({ pagina, totalPaginas, onCambiar }) {
  if (!totalPaginas || totalPaginas <= 1) return null;
  const hayAnterior = pagina > 0;
  const haySiguiente = pagina < totalPaginas - 1;
  const ir = (destino, habilitado) => (e) => {
    e.preventDefault();
    if (habilitado) onCambiar(destino);
  };
  return (
    <nav aria-label="Paginación" className="paginacion">
      <button type="button" className="boton boton-secundario" aria-disabled={!hayAnterior} onClick={ir(pagina - 1, hayAnterior)}>
        Anterior
      </button>
      <span aria-current="page" className="paginacion-actual">
        Página {pagina + 1} de {totalPaginas}
      </span>
      <button type="button" className="boton boton-secundario" aria-disabled={!haySiguiente} onClick={ir(pagina + 1, haySiguiente)}>
        Siguiente
      </button>
    </nav>
  );
}
