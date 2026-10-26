import { useEffect, useState } from 'react';

/** Cuenta regresiva en segundos (para el botón Reintentar tras un 429). Se reinicia cuando `clave` cambia. */
export function useCuentaRegresiva(inicio, clave) {
  const [restante, setRestante] = useState(inicio);
  const [claveVista, setClaveVista] = useState(clave);
  if (claveVista !== clave) {
    setClaveVista(clave);
    setRestante(inicio);
  }
  useEffect(() => {
    if (restante <= 0) return undefined;
    const t = setTimeout(() => setRestante((r) => r - 1), 1000);
    return () => clearTimeout(t);
  }, [restante]);
  return restante;
}
