import { config } from '../config.js';
import { keycloak } from '../auth/keycloak.js';

/**
 * GET autenticado contra el API Gateway.
 * Refresca el token si le quedan menos de 30 s (los tokens duran 5 min).
 * Si la sesión de Keycloak expiró y no se puede refrescar, vuelve a pedir login.
 */
export async function getAutenticado(ruta) {
  try {
    await keycloak.updateToken(30);
  } catch {
    await keycloak.login();
    throw new Error('La sesión expiró. Redirigiendo al inicio de sesión…');
  }
  const respuesta = await fetch(`${config.apiUrl}${ruta}`, {
    headers: { Authorization: `Bearer ${keycloak.token}`, Accept: 'application/json' },
  });
  if (!respuesta.ok) {
    throw new Error(`El servidor respondió ${respuesta.status}`);
  }
  return respuesta.json();
}
