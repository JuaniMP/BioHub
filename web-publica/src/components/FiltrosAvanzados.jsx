import { DEPARTAMENTOS } from '../datos/departamentos.js';
import CampoSelect from './CampoSelect.jsx';
import CampoTexto from './CampoTexto.jsx';

const TEXTO_DEGRADADO =
  'Hay muchas visitas en este momento. Por ahora solo puedes buscar por nombre; los filtros de familia y departamento están desactivados.';

/** Familia (texto libre) y departamento (lista estática). Se desactivan en modo degradado. */
export default function FiltrosAvanzados({ familia, departamento, onFamilia, onDepartamento, onLimpiar, degradado }) {
  return (
    <details className="filtros" open>
      <summary>Filtrar por familia o departamento</summary>
      <div className="filtros-campos">
        <CampoTexto
          etiqueta="Familia"
          value={familia}
          maxLength={100}
          disabled={degradado}
          aria-describedby={degradado ? 'ayuda-degradado' : undefined}
          onChange={(e) => onFamilia(e.target.value)}
        />
        <CampoSelect
          etiqueta="Departamento"
          vacio="Todos los departamentos"
          opciones={DEPARTAMENTOS}
          value={departamento}
          disabled={degradado}
          aria-describedby={degradado ? 'ayuda-degradado' : undefined}
          onChange={(e) => onDepartamento(e.target.value)}
        />
        {degradado ? <p id="ayuda-degradado" className="campo-ayuda">{TEXTO_DEGRADADO}</p> : null}
        <button type="button" className="boton boton-enlace" onClick={onLimpiar}>Limpiar filtros</button>
      </div>
    </details>
  );
}
