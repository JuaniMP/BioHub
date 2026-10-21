import { useCallback, useEffect, useState } from 'react';
import { cerrarSesion, iniciarSesion, keycloak, rolesUsuario } from './auth/keycloak.js';
import { getAutenticado } from './api/cliente.js';
import PingResultado from './components/PingResultado.jsx';

export default function App() {
  const [autenticado, setAutenticado] = useState(false);
  const [errorLogin, setErrorLogin] = useState(null);
  const [ping, setPing] = useState({ estado: 'inactivo', datos: null, error: null });

  const probarPing = useCallback(async () => {
    setPing({ estado: 'cargando', datos: null, error: null });
    try {
      const datos = await getAutenticado('/api/cientifico/ping');
      setPing({ estado: 'ok', datos, error: null });
    } catch (e) {
      setPing({ estado: 'error', datos: null, error: e.message });
    }
  }, []);

  useEffect(() => {
    iniciarSesion()
      .then((ok) => {
        setAutenticado(ok);
        if (ok) probarPing();
      })
      .catch(() => setErrorLogin('No se pudo conectar con el servidor de autenticación.'));
  }, [probarPing]);

  if (errorLogin) return <main className="contenedor"><p role="alert" className="error">{errorLogin}</p></main>;
  if (!autenticado) return <main className="contenedor"><p role="status">Iniciando sesión…</p></main>;

  return (
    <main className="contenedor">
      <header className="encabezado">
        <h1>BioHub · Interna</h1>
        <div>
          <span>
            {keycloak.tokenParsed?.preferred_username} ({rolesUsuario().join(', ')})
          </span>
          <button type="button" onClick={cerrarSesion}>Cerrar sesión</button>
        </div>
      </header>
      <section>
        <h2>Prueba de punta a punta</h2>
        <p>Web interna → API Gateway → Servicio Científico, con el JWT de Keycloak.</p>
        <button type="button" onClick={probarPing}>Probar ping</button>
        <PingResultado {...ping} />
      </section>
    </main>
  );
}
