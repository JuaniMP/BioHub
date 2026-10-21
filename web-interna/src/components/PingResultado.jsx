/** Muestra el resultado de un /ping. */
export default function PingResultado({ estado, datos, error }) {
  if (estado === 'cargando') return <p role="status">Consultando el Servicio Científico…</p>;
  if (estado === 'error') return <p role="alert" className="error">No se pudo contactar el servicio: {error}</p>;
  if (!datos) return null;
  return (
    <dl className="ping">
      <dt>Servicio</dt>
      <dd>{datos.servicio}</dd>
      <dt>Usuario</dt>
      <dd>{datos.usuario}</dd>
      <dt>Roles</dt>
      <dd>{datos.roles.join(', ') || '—'}</dd>
      <dt>Fecha</dt>
      <dd>{new Date(datos.fecha).toLocaleString('es-CO')}</dd>
    </dl>
  );
}
