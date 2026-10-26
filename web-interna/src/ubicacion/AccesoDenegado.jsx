import { useEffect, useRef } from 'react';
import Boton from './componentes/Boton.jsx';
import { IconoCandado } from './componentes/Iconos.jsx';

/** I3. Pantalla estática; no usa role="alert" (no es un error inesperado). */
export default function AccesoDenegado({ alIrABusqueda }) {
  const h1 = useRef(null);
  useEffect(() => {
    document.title = 'Acceso denegado · BioHub';
    h1.current?.focus();
  }, []);
  return (
    <section className="ub-denegado">
      <IconoCandado />
      <h1 tabIndex={-1} ref={h1}>No tienes permiso para ver esta información</h1>
      <p>
        El mapa muestra coordenadas exactas y solo está disponible para el personal científico de alto rango. Puedes
        buscar hallazgos sin coordenadas exactas en la búsqueda de hallazgos.
      </p>
      {alIrABusqueda ? <Boton onClick={alIrABusqueda}>Ir a la búsqueda de hallazgos</Boton> : null}
    </section>
  );
}
