import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { obtenerFicha } from '../api/fichas.js';
import AvisoUbicacionProtegida from '../components/AvisoUbicacionProtegida.jsx';
import ChipEstado from '../components/ChipEstado.jsx';
import ErrorDeConsulta from '../components/ErrorDeConsulta.jsx';
import Esqueleto from '../components/Esqueleto.jsx';
import { formatearFecha } from '../utilidades/formato.js';
import { useTitulo } from '../utilidades/useTitulo.js';
import NoEncontrada from './NoEncontrada.jsx';

function Fila({ etiqueta, valor }) {
  if (!valor) return null; // campo ausente: se omite la fila completa
  return (
    <div className="fila-dato">
      <dt>{etiqueta}</dt>
      <dd>{valor}</dd>
    </div>
  );
}

export default function Ficha() {
  const { id } = useParams();
  const { state } = useLocation();
  const volver = typeof state?.volver === 'string' && state.volver.startsWith('?') ? state.volver : '';
  const [estado, setEstado] = useState({ tipo: 'cargando' });
  const [intento, setIntento] = useState(0);
  const refH1 = useRef(null);

  useTitulo(
    estado.tipo === 'ok' ? `${estado.ficha.nombreCientifico} · BioHub` : estado.tipo === 'noEncontrada' ? 'Especie no encontrada · BioHub' : 'BioHub',
  );

  useEffect(() => {
    const control = new AbortController();
    setEstado({ tipo: 'cargando' });
    obtenerFicha(id, { signal: control.signal })
      .then((ficha) => setEstado({ tipo: 'ok', ficha }))
      .catch((e) => {
        if (e?.name === 'AbortError') return;
        // 404 y 400 (id que no es UUID) se muestran igual: no se revela la diferencia.
        setEstado(e?.status === 404 || e?.status === 400 ? { tipo: 'noEncontrada' } : { tipo: 'error', error: e });
      });
    return () => control.abort();
  }, [id, intento]);

  useEffect(() => {
    if (estado.tipo === 'ok') refH1.current?.focus();
  }, [estado]);

  if (estado.tipo === 'noEncontrada') return <NoEncontrada tipo="ficha" />;

  const enlaceVolver = <Link to={`/${volver}`} className="enlace-volver">Volver a los resultados</Link>;

  if (estado.tipo === 'cargando') {
    return (
      <div aria-busy="true">
        {enlaceVolver}
        <p role="status" className="solo-lectores">Cargando la ficha…</p>
        <Esqueleto tipo="ficha" />
      </div>
    );
  }

  if (estado.tipo === 'error') {
    return (
      <div>
        {enlaceVolver}
        <ErrorDeConsulta error={estado.error} onReintentar={() => setIntento((n) => n + 1)}>
          <Link to="/">Ir al buscador</Link>
        </ErrorDeConsulta>
      </div>
    );
  }

  const f = estado.ficha;
  return (
    <article className="ficha">
      {enlaceVolver}
      <header className="ficha-cabecera">
        <h1 tabIndex={-1} ref={refH1}><i lang="la">{f.nombreCientifico}</i></h1>
        {f.nombreComun ? <p className="ficha-comun">{f.nombreComun}</p> : null}
        <ChipEstado estado={f.estadoConservacion} />
      </header>

      <div className="ficha-columnas">
        <section className="ficha-ubicacion" aria-labelledby="h-donde">
          <h2 id="h-donde">Dónde se encontró</h2>
          <dl className="datos">
            <Fila etiqueta="Departamento" valor={f.departamento} />
            <Fila etiqueta="Región" valor={f.region} />
          </dl>
          <AvisoUbicacionProtegida estadoConservacion={f.estadoConservacion} />
        </section>
        <div className="ficha-principal">
          <section aria-labelledby="h-datos">
            <h2 id="h-datos">Datos de la especie</h2>
            <dl className="datos">
              <Fila etiqueta="Familia" valor={f.familia} />
              <Fila etiqueta="Tipo de espécimen" valor={f.tipoEspecimen} />
              <Fila etiqueta="Fecha de recolección" valor={f.fechaRecoleccion ? formatearFecha(f.fechaRecoleccion) : null} />
            </dl>
          </section>
          {f.descripcion ? (
            <section aria-labelledby="h-desc">
              <h2 id="h-desc">Descripción</h2>
              <p className="texto-corrido">{f.descripcion}</p>
            </section>
          ) : null}
        </div>

      </div>
    </article>
  );
}
