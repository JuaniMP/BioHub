import Keycloak from 'keycloak-js';
import { config } from '../config.js';

/** Instancia única de Keycloak para toda la app. */
export const keycloak = new Keycloak({
  url: config.keycloakUrl,
  realm: config.keycloakRealm,
  clientId: config.keycloakClientId,
});

// Si falla el refresco automático (sesión SSO vencida), se pide login de nuevo.
keycloak.onAuthRefreshError = () => keycloak.login();

let inicializacion;

/**
 * Inicia sesión (redirige al login de Keycloak si no hay sesión).
 * Flujo Authorization Code + PKCE (S256). El token vive solo en memoria, nunca en localStorage.
 * Se memoriza la promesa porque keycloak.init() solo puede llamarse una vez
 * (React StrictMode monta los efectos dos veces en desarrollo).
 */
export function iniciarSesion() {
  if (!inicializacion) {
    inicializacion = keycloak.init({
      onLoad: 'login-required',
      pkceMethod: 'S256',
      checkLoginIframe: false,
    });
  }
  return inicializacion;
}

export function cerrarSesion() {
  return keycloak.logout({ redirectUri: window.location.origin });
}

const ROLES_BIOHUB = [
  'investigador_campo',
  'auditor_cientifico',
  'cientifico_alto_rango',
  'analista_ambiental',
  'administrador',
];

/** Roles de negocio del usuario (ignora los roles técnicos de Keycloak). */
export function rolesUsuario() {
  const roles = keycloak.tokenParsed?.realm_access?.roles ?? [];
  return roles.filter((r) => ROLES_BIOHUB.includes(r));
}
