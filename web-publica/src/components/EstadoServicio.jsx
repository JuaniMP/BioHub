/** Indicador del estado de la API pública. */
export default function EstadoServicio({ estado, datos, error }) {
  if (estado === 'cargando') return <p role="status">Comprobando el servicio…</p>;
  if (estado === 'error') return <p role="alert" className="error">Servicio no disponible ({error}).</p>;
  return (
    <p role="status" className="ok">
      Conectado a <strong>{datos.servicio}</strong> · {new Date(datos.fecha).toLocaleString('es-CO')}
    </p>
  );
}
