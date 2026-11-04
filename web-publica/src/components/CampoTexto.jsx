import { useId } from 'react';

/** Campo de texto con etiqueta visible, ayuda y error enlazados por aria-describedby. */
export default function CampoTexto({ etiqueta, ayuda, error, inputRef, className = '', ...resto }) {
  const id = useId();
  const idAyuda = ayuda ? `${id}-ayuda` : null;
  const idError = error ? `${id}-error` : null;
  const describe = [idAyuda, idError, resto['aria-describedby']].filter(Boolean).join(' ') || undefined;
  return (
    <div className={`campo ${className}`.trim()}>
      <label htmlFor={id}>{etiqueta}</label>
      <input id={id} ref={inputRef} type="text" aria-invalid={error ? 'true' : undefined} {...resto} aria-describedby={describe} />
      {ayuda ? <p id={idAyuda} className="campo-ayuda">{ayuda}</p> : null}
      {error ? <p id={idError} className="campo-error">{error}</p> : null}
    </div>
  );
}
