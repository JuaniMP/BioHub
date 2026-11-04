import { useRef, useState } from 'react';
import { DEPARTAMENTOS } from '../datos/departamentos.js';
import { FILTROS_VACIOS, limpiarFiltros, validarFiltros } from '../utilidades/filtros.js';
import Boton from './Boton.jsx';
import { CampoFecha, CampoSelect, CampoTexto } from './Campos.jsx';

/**
 * Formulario de filtros. variante "busqueda" (con Investigador, botón "Buscar") o "mapa"
 * (sin Investigador, botón "Aplicar filtros"). Solo llama a `onAplicar` si la validación pasa.
 */
export default function FiltrosUbicacion({ variante = 'busqueda', valores, onAplicar, onLimpiar, deshabilitado = false }) {
  const [borrador, setBorrador] = useState(valores);
  const [errores, setErrores] = useState({});
  const formulario = useRef(null);
  const conInvestigador = variante === 'busqueda';

  const cambiar = (campo) => (e) => setBorrador((b) => ({ ...b, [campo]: e.target.value }));

  const enviar = (e) => {
    e.preventDefault();
    const errs = validarFiltros(borrador, { conInvestigador });
    setErrores(errs);
    const primero = Object.keys(errs)[0];
    if (primero) {
      formulario.current?.querySelector(`[data-campo="${primero}"]`)?.focus();
      return;
    }
    onAplicar(limpiarFiltros(conInvestigador ? borrador : { ...borrador, investigador: '' }));
  };

  const limpiar = () => {
    setBorrador(FILTROS_VACIOS);
    setErrores({});
    onLimpiar();
  };

  return (
    <form ref={formulario} className="ub-filtros" onSubmit={enviar} noValidate aria-label={conInvestigador ? 'Filtros de búsqueda' : 'Filtros del mapa'}>
      <fieldset disabled={deshabilitado}>
        <div className="ub-filtros__rejilla">
          <CampoTexto etiqueta="Especie" data-campo="especie" value={borrador.especie} maxLength={120} error={errores.especie} onChange={cambiar('especie')} />
          <CampoFecha etiqueta="Desde" data-campo="desde" value={borrador.desde} error={errores.desde} onChange={cambiar('desde')} />
          <CampoFecha etiqueta="Hasta" data-campo="hasta" value={borrador.hasta} error={errores.hasta} onChange={cambiar('hasta')} />
          {conInvestigador ? (
            <CampoTexto etiqueta="Investigador" data-campo="investigador" value={borrador.investigador} maxLength={120} error={errores.investigador} onChange={cambiar('investigador')} />
          ) : null}
          <CampoSelect etiqueta="Departamento" vacio="Todos los departamentos" opciones={DEPARTAMENTOS} value={borrador.departamento} onChange={cambiar('departamento')} />
        </div>
        <div className="ub-filtros__acciones">
          <Boton type="submit">{conInvestigador ? 'Buscar' : 'Aplicar filtros'}</Boton>
          <Boton variante="secundario" onClick={limpiar}>Limpiar filtros</Boton>
        </div>
      </fieldset>
    </form>
  );
}

export const FiltrosHallazgos = (props) => <FiltrosUbicacion variante="busqueda" {...props} />;
export const FiltrosMapa = (props) => <FiltrosUbicacion variante="mapa" {...props} />;
