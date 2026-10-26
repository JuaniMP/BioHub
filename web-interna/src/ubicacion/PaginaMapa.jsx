import { useCallback, useEffect, useRef, useState } from 'react';
import { estadoDeError, obtenerMapa } from './api/ubicacionApi.js';
import AccesoDenegado from './AccesoDenegado.jsx';
import Aviso from './componentes/Aviso.jsx';
import EstadoVacio from './componentes/EstadoVacio.jsx';
import { FiltrosMapa } from './componentes/FiltrosUbicacion.jsx';
import MapaHallazgos from './componentes/MapaHallazgos.jsx';
import MensajeError from './componentes/MensajeError.jsx';
import PanelDetalleHallazgo from './componentes/PanelDetalleHallazgo.jsx';
import ChipEstado from './componentes/ChipEstado.jsx';
import { FILTROS_VACIOS } from './utilidades/filtros.js';
import { fechaCorta, formatearCoordenada, textoHallazgos } from './utilidades/formato.js';
import { esAltoRango } from './utilidades/roles.js';

/**
 * HU-07 / I2. Props:
 *  - cliente, roles, alSesionVencida: como en PaginaBusqueda
 *  - hallazgoId: opcional; abre el mapa mostrando solo ese hallazgo ("Ver en el mapa")
 *  - alIrABusqueda(): acción del botón de la pantalla de acceso denegado
 * Si el rol no es cientifico_alto_rango no se hace NINGUNA llamada a la API.
 */
export default function PaginaMapa({ cliente, roles = [], hallazgoId = null, alSesionVencida, alIrABusqueda }) {
  const altoRango = esAltoRango(roles);
  const [filtros, setFiltros] = useState(FILTROS_VACIOS);
  const [soloId, setSoloId] = useState(hallazgoId);
  const [intento, setIntento] = useState(0);
  const [estado, setEstado] = useState({ tipo: 'cargando' });
  const [vista, setVista] = useState('mapa');
  const [teselasFallidas, setTeselasFallidas] = useState(false);
  const [seleccionadoId, setSeleccionadoId] = useState(null);
  const apertura = useRef(null);
  const refSesion = useRef(alSesionVencida);
  refSesion.current = alSesionVencida;

  useEffect(() => setSoloId(hallazgoId), [hallazgoId]);

  useEffect(() => {
    if (!altoRango) return undefined; // sin permiso: ninguna llamada
    document.title = 'Mapa de hallazgos · BioHub';
    const control = new AbortController();
    setEstado({ tipo: 'cargando' });
    setSeleccionadoId(null);
    obtenerMapa(cliente, { filtros, hallazgoId: soloId }, { signal: control.signal })
      .then((r) => {
        setEstado({ tipo: 'ok', ...r });
        if (soloId && r.puntos.length === 1) setSeleccionadoId(r.puntos[0].id);
      })
      .catch((e) => {
        const err = estadoDeError(e);
        if (err.name === 'AbortError') return;
        setEstado({ tipo: 'error', status: err.status }); // se quitan marcadores y datos
        if (err.status === 401) refSesion.current?.();
      });
    return () => control.abort();
  }, [cliente, altoRango, filtros, soloId, intento]);

  const seleccionar = useCallback((id, elemento) => {
    apertura.current = elemento ?? document.activeElement;
    setSeleccionadoId(id);
  }, []);
  const cerrarDetalle = useCallback(() => {
    setSeleccionadoId(null);
    const el = apertura.current;
    if (el && document.contains(el)) el.focus();
  }, []);

  if (!altoRango || (estado.tipo === 'error' && estado.status === 403)) {
    return <div className="ub-raiz"><AccesoDenegado alIrABusqueda={alIrABusqueda} /></div>;
  }

  const puntos = estado.tipo === 'ok' ? estado.puntos : [];
  const seleccionado = puntos.find((p) => p.id === seleccionadoId) ?? null;
  const cargando = estado.tipo === 'cargando';
  const hayFiltros = Object.values(filtros).some(Boolean);
  const aplicar = (f) => {
    setSoloId(null);
    setFiltros(f);
    setIntento((n) => n + 1);
  };
  const limpiar = () => {
    setSoloId(null);
    setFiltros(FILTROS_VACIOS);
  };

  return (
    <div className="ub-raiz">
      <div className="ub-mapa-cabecera">
        <h1>Mapa de hallazgos</h1>
        <div role="group" aria-label="Vista" className="ub-vista">
          <button type="button" className="ub-boton ub-boton--secundario" aria-pressed={vista === 'mapa'} onClick={() => setVista('mapa')}>Mapa</button>
          <button type="button" className="ub-boton ub-boton--secundario" aria-pressed={vista === 'lista'} onClick={() => setVista('lista')}>Lista</button>
        </div>
      </div>

      <FiltrosMapa valores={filtros} onAplicar={aplicar} onLimpiar={limpiar} deshabilitado={cargando} />

      {soloId ? (
        <Aviso tipo="info">
          Estás viendo solo el hallazgo elegido. <button type="button" className="ub-boton ub-boton--enlace" onClick={() => setSoloId(null)}>Ver todos los hallazgos</button>
        </Aviso>
      ) : null}

      {cargando ? <p role="status" aria-busy="true">Cargando hallazgos…</p> : null}

      {estado.tipo === 'ok' ? (
        <p role="status" className="ub-contador">
          {estado.truncado ? `${estado.limite} hallazgos en el mapa (hay más)` : `${textoHallazgos(puntos.length)} en el mapa`}
        </p>
      ) : null}
      {estado.tipo === 'ok' && estado.truncado ? (
        <Aviso tipo="advertencia">
          Se muestran {estado.limite} de los hallazgos que cumplen los filtros. Aplica más filtros para ver el resto.
        </Aviso>
      ) : null}
      {estado.tipo === 'ok' && estado.omitidos > 0 ? (
        <Aviso tipo="info">No se pudieron mostrar {estado.omitidos} hallazgos por un problema técnico. Avisa al equipo de soporte.</Aviso>
      ) : null}
      {teselasFallidas ? (
        <Aviso tipo="advertencia">No se pudo cargar el mapa base. Los hallazgos siguen disponibles en la vista Lista.</Aviso>
      ) : null}
      {estado.tipo === 'error' && estado.status === 401 ? <Aviso tipo="info">Tu sesión terminó. Te llevamos al inicio de sesión…</Aviso> : null}
      {estado.tipo === 'error' && estado.status !== 401 ? (
        <MensajeError texto="No pudimos cargar el mapa. Intenta de nuevo." onReintentar={() => setIntento((n) => n + 1)} />
      ) : null}
      {estado.tipo === 'ok' && puntos.length === 0 ? (
        <EstadoVacio
          titulo="No hay hallazgos para los filtros seleccionados"
          ayuda="Prueba con menos filtros o con otro rango de fechas."
          onLimpiar={hayFiltros || soloId ? limpiar : undefined}
        />
      ) : null}

      {estado.tipo !== 'error' ? (
        <div className="ub-mapa-capa">
          {vista === 'mapa' ? (
            <a href="#ub-lista-hallazgos" className="ub-saltar-mapa" onClick={(e) => { e.preventDefault(); setVista('lista'); }}>
              Saltar el mapa y ver la lista
            </a>
          ) : null}
          <div className="ub-mapa-grid">
            {vista === 'mapa' ? (
              <MapaHallazgos
                puntos={puntos}
                seleccionadoId={seleccionadoId}
                onSeleccionar={seleccionar}
                onTeselasFallidas={() => {
                  setTeselasFallidas(true);
                  setVista('lista');
                }}
              />
            ) : (
              <div id="ub-lista-hallazgos" className="ub-tabla-contenedor">
                <table className="ub-tabla">
                  <caption className="ub-solo-lectores">Hallazgos en el mapa</caption>
                  <thead>
                    <tr>
                      <th scope="col">Especie</th><th scope="col">Fecha</th><th scope="col">Departamento</th>
                      <th scope="col">Estado</th><th scope="col">Latitud</th><th scope="col">Longitud</th>
                    </tr>
                  </thead>
                  <tbody>
                    {puntos.map((p) => (
                      <tr key={p.id}>
                        <td data-etiqueta="Especie">
                          <button type="button" className="ub-boton ub-boton--enlace" onClick={(e) => seleccionar(p.id, e.currentTarget)}>
                            <i lang="la">{p.nombreCientifico}</i>
                          </button>
                        </td>
                        <td data-etiqueta="Fecha">{fechaCorta(p.fechaRecoleccion)}</td>
                        <td data-etiqueta="Departamento">{p.departamento}</td>
                        <td data-etiqueta="Estado"><ChipEstado estado={p.estadoConservacion} /></td>
                        <td data-etiqueta="Latitud">{formatearCoordenada(p.posicion[0])}</td>
                        <td data-etiqueta="Longitud">{formatearCoordenada(p.posicion[1])}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <PanelDetalleHallazgo punto={seleccionado} onCerrar={cerrarDetalle} />
          </div>
        </div>
      ) : null}
    </div>
  );
}
