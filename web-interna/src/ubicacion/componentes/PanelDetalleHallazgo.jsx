import { useEffect } from 'react';
import { fechaCorta, formatearCoordenada } from '../utilidades/formato.js';
import Boton from './Boton.jsx';
import ChipEstado from './ChipEstado.jsx';

/** Detalle del hallazgo seleccionado (región, no diálogo modal). Esc cierra. Lat/lon solo como texto. */
export default function PanelDetalleHallazgo({ punto, onCerrar }) {
  useEffect(() => {
    if (!punto) return undefined;
    const alTeclear = (e) => {
      if (e.key === 'Escape') onCerrar();
    };
    document.addEventListener('keydown', alTeclear);
    return () => document.removeEventListener('keydown', alTeclear);
  }, [punto, onCerrar]);

  return (
    <aside className="ub-detalle" aria-label="Detalle del hallazgo">
      {punto ? (
        <>
          <h2><i lang="la">{punto.nombreCientifico}</i></h2>
          <dl>
            <dt>Especie</dt><dd><i lang="la">{punto.nombreCientifico}</i></dd>
            <dt>Estado de conservación</dt><dd><ChipEstado estado={punto.estadoConservacion} /></dd>
            <dt>Fecha de recolección</dt><dd>{fechaCorta(punto.fechaRecoleccion)}</dd>
            <dt>Investigador</dt><dd>{punto.investigador}</dd>
            <dt>Departamento</dt><dd>{punto.departamento}</dd>
            <dt>Región</dt><dd>{punto.region}</dd>
            <dt>Latitud</dt><dd>{formatearCoordenada(punto.posicion[0])}</dd>
            <dt>Longitud</dt><dd>{formatearCoordenada(punto.posicion[1])}</dd>
          </dl>
          <Boton variante="secundario" onClick={onCerrar}>Cerrar</Boton>
        </>
      ) : (
        <p>Selecciona un marcador para ver el detalle.</p>
      )}
    </aside>
  );
}
