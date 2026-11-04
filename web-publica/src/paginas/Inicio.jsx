import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { buscarFichas } from '../api/fichas.js';
import Aviso from '../components/Aviso.jsx';
import AvisoUbicacionProtegida from '../components/AvisoUbicacionProtegida.jsx';
import Buscador from '../components/Buscador.jsx';
import ErrorDeConsulta from '../components/ErrorDeConsulta.jsx';
import EstadoVacio from '../components/EstadoVacio.jsx';
import Esqueleto from '../components/Esqueleto.jsx';
import Paginacion from '../components/Paginacion.jsx';
import TarjetaEspecie from '../components/TarjetaEspecie.jsx';
import { textoTotal } from '../utilidades/formato.js';
import { useTitulo } from '../utilidades/useTitulo.js';

const TEXTO_DEGRADADO =
  'Hay muchas visitas en este momento. Por ahora solo puedes buscar por nombre; los filtros de familia y departamento están desactivados.';

/** Página de la URL (base 0, igual que la API). Valores raros caen a 0. */
function leerPagina(valor) {
  const n = Number.parseInt(valor ?? '', 10);
  return Number.isInteger(n) && n > 0 && n < 10000 ? n : 0;
}

export default function Inicio() {
  useTitulo('Buscar especies · BioHub');
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const familia = params.get('familia') ?? '';
  const departamento = params.get('departamento') ?? '';
  const page = leerPagina(params.get('page'));

  const [estado, setEstado] = useState({ tipo: 'cargando' });
  const [degradado, setDegradado] = useState(false);
  const [intento, setIntento] = useState(0);
  const refTitulo = useRef(null);
  const moverFoco = useRef(false);

  useEffect(() => {
    const control = new AbortController();
    setEstado({ tipo: 'cargando' });
    buscarFichas({ q, familia, departamento, page }, { signal: control.signal })
      .then((r) => {
        setDegradado(r.modoDegradado);
        setEstado({ tipo: 'ok', ...r });
      })
      .catch((e) => {
        if (e?.name === 'AbortError') return;
        setEstado({ tipo: 'error', error: e });
      });
    return () => control.abort();
  }, [q, familia, departamento, page, intento]);

  useEffect(() => {
    if (estado.tipo === 'ok' && moverFoco.current) {
      moverFoco.current = false;
      refTitulo.current?.focus();
    }
  }, [estado]);

  const aUrl = (v, pagina = 0) => {
    const p = new URLSearchParams();
    if (v.q) p.set('q', v.q);
    if (v.familia) p.set('familia', v.familia);
    if (v.departamento) p.set('departamento', v.departamento);
    if (pagina > 0) p.set('page', String(pagina));
    return p;
  };

  const buscar = (v) => {
    const nueva = aUrl(degradado ? { ...v, familia: '', departamento: '' } : v);
    if (nueva.toString() === params.toString()) setIntento((n) => n + 1); // misma búsqueda: se repite
    else setParams(nueva);
  };
  const cambiarPagina = (n) => {
    moverFoco.current = true;
    setParams(aUrl({ q, familia, departamento }, n));
  };
  const limpiar = () => setParams(new URLSearchParams());
  const hayFiltros = Boolean(familia || departamento);
  const volver = params.toString() ? `?${params}` : '';

  return (
    <>
      <Buscador valores={{ q, familia, departamento }} degradado={degradado} onBuscar={buscar} />

      {degradado ? (
        <Aviso tipo="advertencia">
          {TEXTO_DEGRADADO}
          {estado.tipo === 'ok' && estado.filtrosIgnorados.length > 0 ? ' Se ignoraron los filtros de familia y departamento.' : ''}
        </Aviso>
      ) : null}

      <section aria-busy={estado.tipo === 'cargando'} aria-label="Resultados">
        {estado.tipo === 'cargando' ? (
          <>
            <p role="status" className="solo-lectores">Buscando especies…</p>
            <div className="lista-tarjetas"><Esqueleto tipo="tarjeta" cantidad={6} /></div>
          </>
        ) : null}

        {estado.tipo === 'error' ? (
          <ErrorDeConsulta error={estado.error} onReintentar={() => setIntento((n) => n + 1)}>
            {estado.error?.status === 400 ? <button type="button" className="boton boton-secundario" onClick={limpiar}>Limpiar búsqueda</button> : null}
          </ErrorDeConsulta>
        ) : null}

        {estado.tipo === 'ok' && estado.total === 0 ? (
          <EstadoVacio
            titulo="No se encontraron especies"
            ayuda={`Revisa la ortografía o prueba con menos palabras.${hayFiltros ? ' Prueba quitando algún filtro.' : ''}`}
            textoAccion="Limpiar búsqueda"
            onAccion={limpiar}
          />
        ) : null}

        {estado.tipo === 'ok' && estado.total > 0 ? (
          <>
            <h2 className="resultados-titulo" tabIndex={-1} ref={refTitulo} aria-live="polite">{textoTotal(estado.total)}</h2>
            <ul className="lista-tarjetas" aria-label="Resultados de la búsqueda">
              {estado.fichas.map((f) => <TarjetaEspecie key={f.id} ficha={f} volver={volver} />)}
            </ul>
            <Paginacion pagina={estado.page} totalPaginas={estado.totalPaginas} onCambiar={cambiarPagina} />
          </>
        ) : null}
      </section>

      <AvisoUbicacionProtegida variante="breve" />
    </>
  );
}
