# web-publica

Portal público de BioHub (React 19 + Vite). Sin login: consume `GET /api/publica/**` del gateway.

## Comandos
```bash
npm ci
npm run dev          # http://localhost:5174 (API en VITE_API_URL, por defecto http://localhost:8080)
npm run build        # genera dist/ (aplica la CSP de index.html con el origen de VITE_API_URL)
npm test             # vitest (componentes)
npx playwright install --with-deps   # una sola vez (navegadores + librerías; requiere sudo)
npm run test:e2e     # Playwright (RNF13): construye, sirve el build y corre en chromium, firefox y webkit
```
Las E2E simulan la API con `page.route` (`e2e/fixtures.js`, forma de `04-contrato-api.md` §4); no necesitan backend.

Variables: `E2E_SIN_WEBKIT=1` omite Safari si faltan sus librerías del sistema; `E2E_EDGE=1` agrega el canal `msedge`
(`npx playwright install msedge`). Edge usa el motor de Chromium, así que el proyecto `chromium` cubre ese motor.

## Seguridad: CSP y cabeceras (SEC-J-07)
`index.html` lleva una `<meta http-equiv="Content-Security-Policy">`; el build sustituye `%CONNECT_SRC%` por el origen de
`VITE_API_URL`. En `vite dev` la meta se quita (el refresco en caliente usa scripts en línea). No hay `unsafe-inline` en
`script-src` ni en `style-src`. El portal no usa mapas, así que no se permiten teselas en `img-src`.

Una `<meta>` no admite `frame-ancestors`, `X-Content-Type-Options` ni `Referrer-Policy` (esta última sí por `<meta name="referrer">`).
El servidor o CDN que sirva `dist/` debe enviar estas cabeceras en todas las respuestas (sustituye `<gateway>` por el origen de la API):

```
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self' https://<gateway>; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: geolocation=(), camera=(), microphone=()
```
Ejemplo `vercel.json` (SEC-J-07):
```json
{ "headers": [{ "source": "/(.*)", "headers": [
  { "key": "Content-Security-Policy", "value": "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self' https://<gateway>; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'" },
  { "key": "X-Content-Type-Options", "value": "nosniff" },
  { "key": "Referrer-Policy", "value": "strict-origin-when-cross-origin" },
  { "key": "Permissions-Policy", "value": "geolocation=(), camera=(), microphone=()" }
] }],
  "rewrites": [{ "source": "/(.*)", "destination": "/index.html" }] }
```
Las E2E verifican que la app carga sin violaciones de CSP y que un script en línea inyectado es bloqueado.

## Protocolo de usabilidad
`docs/protocolo-usabilidad.md` (RNF6 y RNF7, prueba con personas).
