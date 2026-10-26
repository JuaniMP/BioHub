import { useId } from 'react';

/** Campo con etiqueta visible; error enlazado por aria-describedby. `tipo` = text | date. */
export function CampoTexto({ etiqueta, ayuda, error, tipo = 'text', ...resto }) {
  const id = useId();
  const describe = [ayuda && `${id}-a`, error && `${id}-e`].filter(Boolean).join(' ') || undefined;
  return (
    <div className="ub-campo">
      <label htmlFor={id}>{etiqueta}</label>
      <input id={id} type={tipo} aria-invalid={error ? 'true' : undefined} aria-describedby={describe} {...resto} />
      {ayuda ? <p id={`${id}-a`} className="ub-campo__ayuda">{ayuda}</p> : null}
      {error ? <p id={`${id}-e`} className="ub-campo__error">{error}</p> : null}
    </div>
  );
}

export const CampoFecha = (props) => <CampoTexto tipo="date" ayuda="Formato día/mes/año" {...props} />;

export function CampoSelect({ etiqueta, opciones, vacio, ...resto }) {
  const id = useId();
  return (
    <div className="ub-campo">
      <label htmlFor={id}>{etiqueta}</label>
      <select id={id} {...resto}>
        <option value="">{vacio}</option>
        {opciones.map((o) => <option key={o.codigo} value={o.codigo}>{o.nombre}</option>)}
      </select>
    </div>
  );
}
