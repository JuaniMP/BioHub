import { useEffect, useState } from 'react';

/** true cuando `activo` lleva más de `ms` milisegundos encendido (para "Esto está tardando más de lo normal…"). */
export function useTardanza(activo, ms = 8000) {
  const [tarde, setTarde] = useState(false);
  useEffect(() => {
    if (!activo) {
      setTarde(false);
      return undefined;
    }
    const t = setTimeout(() => setTarde(true), ms);
    return () => clearTimeout(t);
  }, [activo, ms]);
  return tarde;
}
