import { useRef, useState } from 'react';
import Boton from './Boton.jsx';
import CampoTexto from './CampoTexto.jsx';
import FiltrosAvanzados from './FiltrosAvanzados.jsx';

/** Valida `q` con las mismas reglas del servidor (vacío permitido; 2 a 100 caracteres). */
export function validarQ(valor) {
  const q = valor.trim();
  if (q === '') return null;
  if (q.length < 2) return 'Escribe al menos 2 letras.';
  if (q.length > 100) return 'Usa máximo 100 caracteres.';
  return null;
}

/**
 * Formulario de búsqueda. Mantiene un borrador local y llama a `onBuscar` solo al enviar
 * (Enter o botón), de modo que los filtros viajen a la URL solo cuando la persona busca.
 */
export default function Buscador({ valores, degradado, onBuscar }) {
  const [q, setQ] = useState(valores.q);
  const [familia, setFamilia] = useState(valores.familia);
  const [departamento, setDepartamento] = useState(valores.departamento);
  const [errorQ, setErrorQ] = useState(null);
  const refQ = useRef(null);
  const [previo, setPrevio] = useState(valores);

  // Si la URL cambia desde fuera (Atrás, "Limpiar búsqueda"), el borrador la sigue.
  if (previo.q !== valores.q || previo.familia !== valores.familia || previo.departamento !== valores.departamento) {
    setPrevio(valores);
    setQ(valores.q);
    setFamilia(valores.familia);
    setDepartamento(valores.departamento);
    setErrorQ(null);
  }

  const enviar = (e) => {
    e.preventDefault();
    const error = validarQ(q);
    setErrorQ(error);
    if (error) {
      refQ.current?.focus();
      return;
    }
    onBuscar({ q: q.trim(), familia: familia.trim(), departamento });
  };

  const limpiar = () => {
    setFamilia('');
    setDepartamento('');
  };

  return (
    <form role="search" className="buscador" onSubmit={enviar} noValidate>
      <h2 className="buscador-titulo">Busca una especie</h2>
      <div className="buscador-fila">
        <CampoTexto
          etiqueta="Nombre científico o común"
          inputRef={refQ}
          value={q}
          placeholder="Por ejemplo: Atelopus"
          ayuda="Escribe el nombre científico o común."
          error={errorQ}
          autoComplete="off"
          onChange={(e) => setQ(e.target.value)}
        />
        <Boton type="submit">Buscar</Boton>
      </div>
      <FiltrosAvanzados
        familia={familia}
        departamento={departamento}
        degradado={degradado}
        onFamilia={setFamilia}
        onDepartamento={setDepartamento}
        onLimpiar={limpiar}
      />
    </form>
  );
}
