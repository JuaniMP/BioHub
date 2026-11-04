import { Link } from 'react-router';

export default function EncabezadoPublico() {
  return (
    <header className="encabezado-publico">
      <div className="contenedor">
        <Link to="/" className="marca">BioHub</Link>
        <p className="lema">Explora la biodiversidad de Colombia</p>
      </div>
    </header>
  );
}
