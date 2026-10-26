import { useEffect, useRef, useState } from 'react';
import { buscarHallazgos, estadoDeError } from './api/ubicacionApi.js';
import AccesoDenegado from './AccesoDenegado.jsx';
import Aviso from './componentes/Aviso.jsx';
import EstadoVacio from './componentes/EstadoVacio.jsx';
import { FiltrosHallazgos } from './componentes/FiltrosUbicacion.jsx';
import MensajeError from './componentes/MensajeError.jsx';
import Paginacion from './componentes/Paginacion.jsx';
import { TablaEsqueleto, TablaHallazgos, TEXTO_ROL_RESTRINGIDO } from './componentes/TablaHallazgos.jsx';
import { FILTROS_VACIOS } from './utilidades/filtros.js';
import { textoHallazgos } from './utilidades/formato.js';
import { esAltoRango } from './utilidades/roles.js';
import { useTardanza } from './utilidades/useTemporizador.js';

const TAMANOS = [20, 50, 100];

/**
 * HU-13 / I1. Props:
 *  - cliente: { get(ruta, { signal }) } (ver api/ubicacionApi.js)
 *  - roles: roles de negocio del usuario (rolesUsuario() de Santiago)
 *  - alSesionVencida(): se llama ante un 401 (p. ej. keycloak.login)
 *  - alVerEnMapa(id): opcional; si falta, no se pinta el botón "Ver en el mapa"
 */
export default function PaginaBusqueda({ cliente, roles = [], alSesionVencida, alVerEnMapa }) {
  const altoRango = esAltoRango(roles);
  const [filtros, setFiltros] = useState(FILTROS_VACIOS);
  const [pagina, setPagina] = useState(0);
  const [tamano, setTamano] = useState(20);
  const [intento, setIntento] = useState(0);
  const [estado, setEstado] = useState({ tipo: 'cargando' });
  const refResumen = useRef(null);
  const moverFoco = useRef(false);
  const refSesion = useRef(alSesionVencida);
  refSesion.current = alSesionVencida;
  const tardando = useTardanza(estado.tipo === 'cargando');

  useEffect(() => {
    document.title = 'Búsqueda de hallazgos · BioHub';
  }, []);

  useEffect(() => {
    const control = new AbortController();
    setEstado({ tipo: 'cargando' });
    buscarHallazgos(cliente, { filtros, page: pagina, size: tamano, altoRango }, { signal: control.signal })
      .then((r) => setEstado({ tipo: 'ok', ...r }))
      .catch((e) => {
        const err = estadoDeError(e);
        if (err.name === 'AbortError') return;
        setEstado({ tipo: 'error', status: err.status }); // se descartan los datos en memoria
        if (err.status === 401) refSesion.current?.();
      });
    return () => control.abort();
  }, [cliente, filtros, pagina, tamano, intento, altoRango]);

  useEffect(() => {
    if (estado.tipo === 'ok' && moverFoco.current) {
      moverFoco.current = false;
      refResumen.current?.focus();
    }
  }, [estado]);

  const aplicar = (f) => {
    moverFoco.current = true;
    setFiltros(f);
    setPagina(0);
    setIntento((n) => n + 1);
  };
  const limpiar = () => {
    setFiltros(FILTROS_VACIOS);
    setPagina(0);
  };

  if (estado.tipo === 'error' && estado.status === 403) return <div className="ub-raiz"><AccesoDenegado /></div>;

  const hayFiltros = Object.values(filtros).some(Boolean);

  return (
    <div className="ub-raiz">
      <h1>Búsqueda de hallazgos</h1>
      {!altoRango ? <Aviso tipo="info">{TEXTO_ROL_RESTRINGIDO}</Aviso> : null}
      <FiltrosHallazgos valores={filtros} onAplicar={aplicar} onLimpiar={limpiar} />

      <section aria-label="Resultados" aria-busy={estado.tipo === 'cargando'}>
        {estado.tipo === 'cargando' ? (
          <>
            <p role="status">Buscando hallazgos…</p>
            {tardando ? <p role="status">Esto está tardando más de lo normal…</p> : null}
            <TablaEsqueleto />
          </>
        ) : null}

        {estado.tipo === 'error' && estado.status === 401 ? (
          <Aviso tipo="info">Tu sesión terminó. Te llevamos al inicio de sesión…</Aviso>
        ) : null}
        {estado.tipo === 'error' && estado.status === 400 ? (
          <MensajeError texto="Algo en la búsqueda no es válido. Revisa los filtros." />
        ) : null}
        {estado.tipo === 'error' && ![400, 401, 403].includes(estado.status) ? (
          <MensajeError texto="No pudimos cargar los hallazgos. Intenta de nuevo." onReintentar={() => setIntento((n) => n + 1)} />
        ) : null}

        {estado.tipo === 'ok' && estado.omitidos > 0 ? (
          <Aviso tipo="advertencia">
            No se pudieron mostrar {estado.omitidos} hallazgos por un problema técnico. Avisa al equipo de soporte.
          </Aviso>
        ) : null}

        {estado.tipo === 'ok' && estado.total === 0 ? (
          <EstadoVacio
            titulo="No hay hallazgos que coincidan con los filtros"
            ayuda="Prueba con menos filtros o con otro rango de fechas."
            onLimpiar={hayFiltros ? limpiar : undefined}
          />
        ) : null}

        {estado.tipo === 'ok' && estado.total > 0 ? (
          <>
            <div className="ub-resumen">
              <p tabIndex={-1} ref={refResumen} aria-live="polite" className="ub-resumen__total">{textoHallazgos(estado.total)}</p>
              <label className="ub-tamano">
                Mostrar{' '}
                <select
                  value={tamano}
                  onChange={(e) => {
                    setTamano(Number(e.target.value));
                    setPagina(0);
                  }}
                >
                  {TAMANOS.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </label>
            </div>
            <TablaHallazgos hallazgos={estado.hallazgos} altoRango={altoRango} onVerEnMapa={alVerEnMapa} />
            <Paginacion
              pagina={estado.page}
              totalPaginas={estado.totalPaginas}
              onCambiar={(n) => {
                moverFoco.current = true;
                setPagina(n);
              }}
            />
          </>
        ) : null}
      </section>
    </div>
  );
}
