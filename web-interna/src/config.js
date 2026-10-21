export const config = {
  apiUrl: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  keycloakUrl: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180',
  keycloakRealm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'biohub',
  keycloakClientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'web-interna',
};
