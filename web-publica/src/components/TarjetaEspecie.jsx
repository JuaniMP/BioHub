import { Link } from 'react-router';
import ChipEstado from './ChipEstado.jsx';

/** Tarjeta de resultado: un solo enlace (un único tab stop). Sin fecha ni nada parecido a una ubicación precisa. */
export default function TarjetaEspecie({ ficha, volver = '' }) {
  const lugar = [ficha.departamento, ficha.region ? `Región ${ficha.region}` : null].filter(Boolean).join(' · ');
  return (
    <li className="tarjeta">
      <Link to={`/especies/${encodeURIComponent(ficha.id)}`} state={{ volver }} className="tarjeta-enlace">
        <span className="tarjeta-titulo"><i lang="la">{ficha.nombreCientifico}</i></span>
        {ficha.nombreComun ? <span className="tarjeta-comun">{ficha.nombreComun}</span> : null}
        <ChipEstado estado={ficha.estadoConservacion} />
        {ficha.familia ? <span>Familia {ficha.familia}</span> : null}
        {lugar ? <span>{lugar}</span> : null}
      </Link>
    </li>
  );
}
