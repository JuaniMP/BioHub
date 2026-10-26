import { useCuentaRegresiva } from '../utilidades/useCuentaRegresiva.js';
import MensajeError from './MensajeError.jsx';

export const TEXTO_ERROR = 'No pudimos cargar la información, intenta de nuevo';
export const TEXTO_400 = 'Algo en la búsqueda no es válido. Revisa lo que escribiste.';
export const TEXTO_429 = 'Hiciste muchas búsquedas seguidas. Espera un momento e intenta de nuevo.';

/** Error de una consulta: 400, 429 (con cuenta regresiva de 10 s) o genérico (red, 5xx). */
export default function ErrorDeConsulta({ error, onReintentar, children }) {
  const es429 = error?.status === 429;
  const espera = useCuentaRegresiva(es429 ? 10 : 0, error);
  const texto = es429 ? TEXTO_429 : error?.status === 400 ? TEXTO_400 : TEXTO_ERROR;
  return (
    <MensajeError texto={texto} onReintentar={error?.status === 400 ? undefined : onReintentar} deshabilitarReintento={espera > 0}>
      {es429 && espera > 0 ? <p className="campo-ayuda">Podrás reintentar en {espera} s.</p> : null}
      {children}
    </MensajeError>
  );
}
