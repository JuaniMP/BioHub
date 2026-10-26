import { useId } from 'react';

/** Lista desplegable nativa con etiqueta visible. */
export default function CampoSelect({ etiqueta, opciones, vacio, className = '', ...resto }) {
  const id = useId();
  return (
    <div className={`campo ${className}`.trim()}>
      <label htmlFor={id}>{etiqueta}</label>
      <select id={id} {...resto}>
        <option value="">{vacio}</option>
        {opciones.map((o) => (
          <option key={o.codigo} value={o.codigo}>{o.nombre}</option>
        ))}
      </select>
    </div>
  );
}
