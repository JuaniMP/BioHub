const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

/** Error de la API con el estado HTTP y el `codigo` estable del contrato (problem+json). */
export class ErrorApi extends Error {
  constructor(status, codigo = null, reintentarEn = null) {
    super(`La API respondió ${status}`);
    this.name = 'ErrorApi';
    this.status = status; // 0 = sin respuesta (red caída)
    this.codigo = codigo;
    this.reintentarEn = reintentarEn; // segundos (cabecera Retry-After), si vino
  }
}

/** GET a la API pública a través del gateway. Sin token, sin API Key, sin cookies. */
export async function getJson(ruta, { signal } = {}) {
  let respuesta;
  try {
    respuesta = await fetch(`${API_URL}${ruta}`, {
      headers: { Accept: 'application/json' },
      credentials: 'omit',
      signal,
    });
  } catch (e) {
    if (e?.name === 'AbortError') throw e;
    throw new ErrorApi(0);
  }
  if (!respuesta.ok) {
    let codigo = null;
    try {
      codigo = (await respuesta.json())?.codigo ?? null;
    } catch {
      // cuerpo vacío o no JSON: se decide solo por el estado HTTP
    }
    const retry = Number(respuesta.headers?.get?.('Retry-After'));
    throw new ErrorApi(respuesta.status, codigo, Number.isFinite(retry) && retry > 0 ? retry : null);
  }
  return respuesta.json();
}
