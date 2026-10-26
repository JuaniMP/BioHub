import { useId, useState } from 'react';
import { fechaCorta, formatearCoordenada } from '../utilidades/formato.js';
import Boton from './Boton.jsx';
import ChipEstado from './ChipEstado.jsx';
import IndicadorRestringido from './IndicadorRestringido.jsx';

export const TEXTO_ROL_RESTRINGIDO =
  'Tu rol no permite ver las coordenadas exactas. Solo el personal científico de alto rango puede verlas. Si las necesitas para tu trabajo, pídelas a una persona con ese rol.';

function CeldaCoordenadas({ h, altoRango }) {
  // Regla de celda: si el rol no es alto rango, SIEMPRE "Restringido" y no se lee ningún valor de la fila.
  if (!altoRango) return <IndicadorRestringido />;
  if (h.estadoCoordenadas === 'VISIBLES') return <>{formatearCoordenada(h.latitud)}, {formatearCoordenada(h.longitud)}</>;
  if (h.estadoCoordenadas === 'NO_DISPONIBLES') return <span className="ub-suave">No disponible</span>;
  return <span className="ub-suave">Sin coordenadas</span>;
}

/** Tabla de hallazgos (apilada en pantallas pequeñas por CSS). Columna "Acción" solo para alto rango. */
export function TablaHallazgos({ hallazgos, altoRango, onVerEnMapa, cargando = false }) {
  const [verPorQue, setVerPorQue] = useState(false);
  const idPorQue = useId();
  return (
    <div className="ub-tabla-contenedor">
      <table className="ub-tabla" aria-busy={cargando}>
        <caption className="ub-solo-lectores">Hallazgos registrados</caption>
        <thead>
          <tr>
            <th scope="col">Especie</th>
            <th scope="col">Fecha</th>
            <th scope="col">Investigador</th>
            <th scope="col">Departamento / Región</th>
            <th scope="col">Estado</th>
            <th scope="col">
              Coordenadas
              {!altoRango ? (
                <button type="button" className="ub-porque" aria-expanded={verPorQue} aria-controls={idPorQue} onClick={() => setVerPorQue((v) => !v)}>
                  ¿Por qué?
                </button>
              ) : null}
            </th>
            {altoRango ? <th scope="col">Acción</th> : null}
          </tr>
        </thead>
        <tbody>
          {hallazgos.map((h) => (
            <tr key={h.id}>
              <td data-etiqueta="Especie"><i lang="la">{h.nombreCientifico}</i></td>
              <td data-etiqueta="Fecha">{fechaCorta(h.fechaRecoleccion)}</td>
              <td data-etiqueta="Investigador">{h.investigador}</td>
              <td data-etiqueta="Departamento / Región">{[h.departamento, h.region].filter(Boolean).join(' / ')}</td>
              <td data-etiqueta="Estado"><ChipEstado estado={h.estadoConservacion} /></td>
              <td data-etiqueta="Coordenadas"><CeldaCoordenadas h={h} altoRango={altoRango} /></td>
              {altoRango ? (
                <td data-etiqueta="Acción">
                  {h.estadoCoordenadas === 'VISIBLES' && onVerEnMapa ? (
                    <Boton variante="secundario" onClick={() => onVerEnMapa(h.id)} aria-label={`Ver en el mapa: ${h.nombreCientifico}`}>
                      Ver en el mapa
                    </Boton>
                  ) : null}
                </td>
              ) : null}
            </tr>
          ))}
        </tbody>
      </table>
      {!altoRango && verPorQue ? <p id={idPorQue} className="ub-porque-texto">{TEXTO_ROL_RESTRINGIDO}</p> : null}
    </div>
  );
}

export function TablaEsqueleto() {
  return (
    <div aria-hidden="true">
      {Array.from({ length: 5 }, (_, i) => <div key={i} className="ub-esqueleto" data-testid="ub-esqueleto" />)}
    </div>
  );
}
