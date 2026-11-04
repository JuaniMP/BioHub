import { useEffect, useRef } from 'react';
import { Link } from 'react-router';
import { useTitulo } from '../utilidades/useTitulo.js';

const TEXTOS = {
  ficha: {
    titulo: 'No encontramos esta especie',
    ayuda: 'Es posible que el enlace esté mal escrito o que la ficha ya no esté disponible.',
    pestana: 'Especie no encontrada · BioHub',
  },
  pagina: {
    titulo: 'Esta página no existe',
    ayuda: 'Revisa la dirección o vuelve al buscador.',
    pestana: 'Página no encontrada · BioHub',
  },
};

/** 404 de ficha (también para id inválido, 400) o de página desconocida. */
export default function NoEncontrada({ tipo = 'pagina' }) {
  const t = TEXTOS[tipo];
  const refH1 = useRef(null);
  useTitulo(t.pestana);
  useEffect(() => refH1.current?.focus(), []);
  return (
    <div className="pagina-404">
      <h1 tabIndex={-1} ref={refH1}>{t.titulo}</h1>
      <p>{t.ayuda}</p>
      <Link to="/" className="boton boton-primario">Ir al buscador</Link>
    </div>
  );
}
