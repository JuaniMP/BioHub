import { useEffect, useState } from 'react';
import { getPublico } from './api/cliente.js';
import EstadoServicio from './components/EstadoServicio.jsx';

export default function App() {
  const [ping, setPing] = useState({ estado: 'cargando', datos: null, error: null });

  useEffect(() => {
    let activo = true;
    getPublico('/api/publica/ping')
      .then((datos) => activo && setPing({ estado: 'ok', datos, error: null }))
      .catch((e) => activo && setPing({ estado: 'error', datos: null, error: e.message }));
    return () => {
      activo = false;
    };
  }, []);

  return (
    <main className="contenedor">
      <h1>BioHub</h1>
      <p>Portal público de consulta de biodiversidad.</p>
      <EstadoServicio {...ping} />
    </main>
  );
}
