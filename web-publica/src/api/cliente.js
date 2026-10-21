const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

/** GET a la API pública a través del gateway (sin token; luego llevará API Key). */
export async function getPublico(ruta) {
  const respuesta = await fetch(`${API_URL}${ruta}`, { headers: { Accept: 'application/json' } });
  if (!respuesta.ok) {
    throw new Error(`El servidor respondió ${respuesta.status}`);
  }
  return respuesta.json();
}
