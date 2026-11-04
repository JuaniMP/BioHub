import { useEffect } from 'react';

/** Cambia el título de la pestaña (02 §8). */
export function useTitulo(titulo) {
  useEffect(() => {
    document.title = titulo;
  }, [titulo]);
}
