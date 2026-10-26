# Módulo de ubicación (D17)

Dos pantallas autocontenidas: `PaginaBusqueda` (HU-13) y `PaginaMapa` (HU-07, Leaflet). No usan el router ni el login: reciben lo que necesitan por props. Estilos propios con prefijo `ub-` (no tocan `index.css`).

## Cómo montarlas (Santiago)

```jsx
import { PaginaBusqueda, PaginaMapa, crearClienteUbicacion } from './ubicacion/index.js';
import { keycloak, rolesUsuario } from './auth/keycloak.js';
import { config } from './config.js';

// Crear UNA vez (fuera del componente o con useMemo): debe tener identidad estable.
const cliente = crearClienteUbicacion({
  apiUrl: config.apiUrl,
  obtenerToken: async () => { await keycloak.updateToken(30); return keycloak.token; },
});

// Rutas propuestas: /ubicacion/busqueda y /ubicacion/mapa
<PaginaBusqueda
  cliente={cliente}
  roles={rolesUsuario()}
  alSesionVencida={() => keycloak.login()}
  alVerEnMapa={(id) => irA(`/ubicacion/mapa?hallazgo=${id}`)}   // opcional
/>

<PaginaMapa
  cliente={cliente}
  roles={rolesUsuario()}
  hallazgoId={idDeLaUrl}                       // opcional (query ?hallazgo=<id>)
  alSesionVencida={() => keycloak.login()}
  alIrABusqueda={() => irA('/ubicacion/busqueda')}
/>
```

- `cliente` es cualquier objeto `{ get(ruta, { signal }) => Promise<json> }` que rechace con un error con `status` (y `codigo` si lo conoce). `crearClienteUbicacion` ya lo implementa con `fetch` (Bearer, `cache: 'no-store'`, sin cookies); en tests se puede inyectar uno falso.
- `roles`: roles de negocio (`rolesUsuario()`). Solo `cientifico_alto_rango` ve coordenadas y entra al mapa. Cualquier otro rol (o lista vacía) ve "Restringido" y, en el mapa, la pantalla de acceso denegado **sin llamar a la API**.
- El enlace "Mapa" puede mostrarse a todos los roles: la pantalla explica el rechazo.
- Las pantallas cambian `document.title` y mueven el foco al título al entrar.
- Las dependencias `leaflet` y `react-leaflet` ya están en `package.json`. La CSP debe permitir `img-src https://tile.openstreetmap.org` (teselas con `maxZoom: 12` y atribución visible).
- Las coordenadas solo viven en el estado de React: nunca en URL, almacenamiento, título ni consola.

## Pruebas

`npm test` (carpeta `pruebas/`, sin backend: cliente falso y `react-leaflet` simulado).
