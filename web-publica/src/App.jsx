import { Route, Routes } from 'react-router';
import EncabezadoPublico from './components/EncabezadoPublico.jsx';
import EnlaceSaltar from './components/EnlaceSaltar.jsx';
import Ficha from './paginas/Ficha.jsx';
import Inicio from './paginas/Inicio.jsx';
import NoEncontrada from './paginas/NoEncontrada.jsx';

/** Rutas del portal. El router (BrowserRouter / MemoryRouter) lo pone quien monta la App. */
export default function App() {
  return (
    <>
      <EnlaceSaltar />
      <EncabezadoPublico />
      <main id="contenido" className="contenedor">
        <Routes>
          <Route path="/" element={<Inicio />} />
          <Route path="/especies/:id" element={<Ficha />} />
          <Route path="*" element={<NoEncontrada tipo="pagina" />} />
        </Routes>
      </main>
    </>
  );
}
