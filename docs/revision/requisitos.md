# Revisión de cumplimiento: RF1–RF19 y HU-01..HU-13

Fecha: 2026-11-08. Rama `feature/ubicacion-consulta-publica`, HEAD `6a5495a` (integra `develop` con el PR #7). Revisión de solo lectura: no se ejecutó ninguna prueba; el estado sale del código del repositorio y de los resultados ya reportados en `docs/equipo/07-reporte-qa.md` (Iteración 17: científico 322/0, consulta-pública 239/0, gateway 168/0, E2E por gateway 50/50; Iteración 16: web-interna 46/46, web-publica 32/32).

Fuente de verdad: `docs/entregas/SRS-BioHub.txt`. Responsables: J = Juanita, S = Santiago, H = Hernando, N = Nicolás.

Regla de estado: **Cumple** = código + prueba automatizada que cubre el criterio. **Parcial** = código sin prueba suficiente, solo parte de los criterios, o backend sin la pantalla que exige la HU. **No cumple** = no hay código. **Sin evidencia** = no se puede determinar desde el repo.

Límite de la revisión: solo se evaluó el árbol de esta rama (que contiene `develop`). Existen las ramas locales/remotas `feature/registro-cientifico` y `feature/reportes-equipos`, pero no tengo herramienta de git para ver si tienen trabajo sin integrar. Lo que no esté en `develop` no cuenta como entregado.

---

## 1. Resumen ejecutivo

- **RF (19):** 7 Cumple, 1 Parcial, 11 No cumple. **HU (13):** 1 Cumple, 6 Parcial, 6 No cumple.
- **Por responsable (RF):** J 4 (3 Cumple, 1 Parcial); S 4 (4 Cumple); H 7 (7 No cumple); N 4 (4 No cumple).
- **Por responsable (HU):** J 3 (3 Parcial); S 4 (HU-08 Cumple; HU-10, HU-11, HU-12 Parcial); H 4 (HU-01, 02, 03, 05: 4 No cumple); N 2 (HU-04, HU-09: 2 No cumple).
- **Cumplimiento funcional aproximado:** RF 37 % estricto (7/19) o 39 % contando Parcial a medias. HU: 18 de 38 criterios de aceptación tienen código y prueba (47 %); por puntos Fibonacci, 8 de 76 puntos están completos y 31 parciales (aprox. 31 % ponderado). La mitad de los puntos (37/76) no tiene una sola línea de código.
- **Causa raíz:** `registro` (Hernando), `servicio-equipos` y `servicio-reportes` (Nicolás) siguen siendo esqueletos: solo `package-info.java`, ping y el consumidor/publicador de eventos de prueba. No existe ninguna tabla de hallazgos, fichas, historial, equipos ni reportes (solo hay migraciones de `ubicacion_*`). Por eso hoy **ningún hallazgo real entra al sistema**: el portal, el mapa y la búsqueda interna están completos pero se alimentan solo con eventos simulados y datos sembrados.
- **Lo de Juanita y Santiago está en buen estado**; el cuello de botella para la entrega son Hernando (registro e historial) y Nicolás (equipos y reportes), más el montaje de pantallas de `web-interna` (Santiago).

---

## 2. Requisitos funcionales RF1–RF19

Rutas abreviadas: `CI/` = `servicio-cientifico/src/main/java/co/edu/unbosque/biohub/cientifico/ubicacion/`; `CP/` = `servicio-consulta-publica/src/main/java/co/edu/unbosque/biohub/consultapublica/`; `GW/` = `api-gateway/src/main/java/co/edu/unbosque/biohub/gateway/`; pruebas en las mismas rutas bajo `src/test/`.

| ID | Descripción corta | Resp. | Estado | Evidencia (código y prueba) | Qué falta |
|---|---|---|---|---|---|
| RF1 | Registrar hallazgos y ejemplares con evidencia adjunta | H | **No cumple** | `servicio-cientifico/.../cientifico/registro/**` solo tiene `package-info.java`; `V1__base.sql` sin tablas (solo hay `CREATE TABLE ubicacion_*`). El único publicador es `EventosPruebaController` (`POST /api/cientifico/eventos/prueba`, `prueba.ping`). El gateway ya reenvía multipart (`GatewayIntegracionTest.unAdjuntoMultipartLlegaCompletoAlServicio`), pero no hay receptor. | Todo el módulo: endpoint de hallazgo, adjuntos (fotos, audio, datos), publicar `hallazgo.registrado` con `PublicadorEventos`, llamar a `UbicacionService` (o evento) para cifrar coordenadas. |
| RF2 | Fichas taxonómicas con esquema flexible | H | **No cumple** | Igual que RF1; sin tabla de fichas/plantillas. | Modelo flexible (JSON/EAV), endpoints, validación dinámica. |
| RF3 | Modificar o agregar atributos medibles a las fichas | H | **No cumple** | Sin código. | Alta/renombrar/deshabilitar atributos sin tocar registros históricos. |
| RF4 | Reclasificar o modificar un ejemplar ya registrado | H | **No cumple** | Sin código de registro. Del lado de J ya existe quien reacciona: `UbicacionServiceImpl.actualizar` y `CP/eventos/service/ProcesadorHallazgos` (`hallazgo.reclasificado`), probados con eventos simulados en `ConsumidorHallazgosIntegracionTest`. | Endpoint de reclasificación con justificación obligatoria y publicar `hallazgo.reclasificado`. |
| RF5 | Registrar equipos físicos de expedición | N | **No cumple** | `servicio-equipos/.../equipos/**`: solo `PingController`, `SecurityConfig` y `package-info.java`; `V1__base.sql` sin tablas. Prueba: solo `PingControllerTest`. | CRUD de equipos, tipos (dron, cámara trampa, hidrófono, sensor), migración. |
| RF6 | Asociar obligatoriamente cada equipo al investigador responsable | N | **No cumple** | Sin código. La validación del custodio puede apoyarse en `GET /api/gateway/usuarios/{id}` (`GW/controller/UsuariosController.java:52`), ya hecho por S. | Validar custodio registrado y activo; campo obligatorio. |
| RF7 | Historial completo e inmutable de cambios de un hallazgo | H | **No cumple** | Sin tabla ni código de historial. | Tabla append-only con permisos INSERT/SELECT solamente, trigger o permisos que bloqueen UPDATE/DELETE (RNF10/ASR7). |
| RF8 | Consultar versiones históricas para comparar periodos | H | **No cumple** | Sin código. | Endpoint de versiones y comparación. |
| RF9 | Registrar y mostrar quién autorizó cada cambio y el motivo | H | **No cumple** | Sin código. La matriz de roles de `11-seguridad-y-acceso.md` pide guardar el `sub` del JWT (SEC-17). | Guardar `sub`, motivo y fecha en el historial; exponerlos al auditor. |
| RF10 | Módulo de consulta pública: buscar y ver fichas | J | **Cumple** | `CP/controller/FichasPublicasController.java:43-57` (`GET /api/publica/fichas`, `/{id}` con `q`, `familia`, `departamento`), `CP/service/ConsultaFichasService`, `ValidadorBusqueda`, `CP/infrastructure/BusquedaFichasJdbc`; UI `web-publica/src/paginas/Inicio.jsx`, `Ficha.jsx`. Pruebas: `BusquedaFichasIntegracionTest`, `FichasPublicasControllerTest`, `QaPublicaInyeccionYLimitesTest`, `SeguridadEndpointsTest`, Vitest `Inicio.test.jsx`/`Ficha.test.jsx`, Playwright `web-publica/e2e/portal.spec.js`, Bruno `consulta-publica/01..15`. | Nada de J. El catálogo solo se llena con `hallazgo.*` reales cuando H publique (hoy solo eventos simulados y `infra/ci/SembrarHallazgos.java`). |
| RF11 | Restringir ubicación de especies en peligro: solo región o departamento a visitantes | J | **Cumple** | Más estricto que el SRS (D14): ninguna especie lleva coordenadas. `CP/eventos/service/ValidadorDatosHallazgo` (rechaza coordenadas), `CP/domain/FichaPublica` (sin campos de coordenadas), `FichaPublicaResponse.ubicacionProtegida`, `web-publica/src/components/AvisoUbicacionProtegida.jsx`. Pruebas: `SinCoordenadasIntegracionTest`, `ValidadorDatosHallazgoTest`, `DetectorCoordenadasTest`, `ConsumidorEventosRechazosIntegracionTest`, `QaEventosInvalidosDlqTest`, Bruno `consulta-publica/08,10,13`, E2E y Vitest. | Riesgo residual de Bajo: SEC-J-17 (guarda de coordenadas eludible en el publicador) y SEC-J-02 (coordenadas escritas en texto libre). |
| RF12 | Mapa con coordenadas exactas solo para personal científico de alto rango | J (+S montaje) | **Parcial** | Backend completo: `CI/controller/MapaUbicacionController.java:26-27` (`@PreAuthorize("@politicaUbicacion.puedeVerCoordenadas(authentication)")`), `MapaService`, `ServicioDescifrado`. Pruebas: `MapaApiTest`, `PoliticaAccesoUbicacionTest`, `QaSeguridadJwtTest`, `SeguridadEndpointsTest` (56/56), E2E por gateway (admin, investigador, auditor, analista y sinrol: 403; cientifico1: 200), Bruno `ubicacion/09..14`. UI: `web-interna/src/ubicacion/PaginaMapa.jsx` + `MapaHallazgos.jsx` con `PaginaMapa.test.jsx`. **Pero** `web-interna/src/App.jsx` (53 líneas) solo muestra el ping: no hay router, menú ni referencia a `src/ubicacion/`. Ningún usuario puede llegar al mapa desde la aplicación. | Montar la ruta del mapa y el enlace por rol en `web-interna` (S, tarea 16j; D17). Datos reales dependen de H. |
| RF13 | Validar permisos y roles de forma centralizada al pedir ubicación | J (roles/gateway: S) | **Cumple** | `CI/service/PoliticaAccesoUbicacion.java` (única clase que decide), usada por `MapaService`, `BusquedaHallazgosService`, `ServicioDescifrado` (doble barrera: `@PreAuthorize` + servicio). `denyAll` por defecto (`servicio-cientifico/.../config/SecurityConfig.java:38`, consulta-pública `:39`, gateway `:69`; D55). Pruebas: `PoliticaAccesoUbicacionTest`, `ServicioDescifradoTest`, `MapaApiTest`, `BusquedaHallazgosApiTest`, `ArquitecturaSeguridadTest`, `SeguridadEndpointsTest`, `MatrizAccesoGatewayTest`. | Nada de J. SEC-J-19 (endurecer `ArquitecturaSeguridadTest`) y SEC-J-20 (403 problem+json fuera de `ubicacion`) en curso. |
| RF14 | Punto de entrada API para universidades y ONGs | S (datos: J) | **Cumple** | `GW/controller/ApiExternaController.java:24` (`GET /api/v1/externa/fichas/**`), `GW/infrastructure/filtro/ApiKeyRateLimitFilter.java`, `ProxyService.reenviarApiExterna`. Pruebas: `GatewayIntegracionTest` (ciclo emitir/usar/revocar, límite), `ApiKeyRateLimitFilterTest`, `ProxyServiceTest`, Bruno `gateway/11..20`, E2E de QA 17f (key válida 200, sin key 401, revocada 401). | Nada obligatorio. La API externa solo sirve fichas públicas, que dependen de que H publique hallazgos. |
| RF15 | Reportes mensuales de índices de presión | N | **No cumple** | `servicio-reportes/.../reportes/**`: solo `eventos/` (consumidor genérico con DLQ, `ConsumidorEventos`, `EventosRecibidosController`), ping y `package-info.java`. Sin tablas. | Cálculo del índice, tareas asíncronas, almacenamiento en BD analítica. |
| RF16 | Cruce de registros masivos para detectar deforestación | N | **No cumple** | Sin código. | Consumo de `hallazgo.*`, cruce y alertas de zonas. |
| RF17 | Registrar usuarios internos y asignarles uno de 5 roles | S | **Cumple** (API) | `GW/controller/UsuariosController.java:58` (`POST /api/gateway/usuarios`), `ServicioUsuarios`, `KeycloakAdminAdapter`. Pruebas: `KeycloakAdminAdapterIntegracionTest.creaUnUsuarioConExactamenteUnRolYContrasenaTemporal`, `ServicioUsuariosTest`, `UsuariosControllerTest`, Bruno `gateway/21..26`. | La pantalla de usuarios no existe (ver HU-10). |
| RF18 | Autenticar usuarios internos antes de los módulos | S | **Cumple** | JWT en gateway y en los 5 servicios (`SecurityConfig` de cada uno), login PKCE en `web-interna/src/auth/keycloak.js` y `App.jsx`. Pruebas: `GatewaySecurityTest`, `MatrizAccesoGatewayTest`, `SeguridadEndpointsTest` (científico y consulta-pública), `QaSeguridadJwtTest`, `web-interna/src/App.test.jsx`, E2E 17f (sin token 401). | `servicio-equipos` y `servicio-reportes` aún usan `.anyRequest().authenticated()` (`SecurityConfig.java:32` en ambos) en lugar de `denyAll` (SEC-02, N). No afecta a RF18. |
| RF19 | Admin modifica o revoca el rol y se refleja de inmediato en RF13 | S | **Cumple** (API) | `UsuariosController.java:66,73` (`PUT/DELETE /{id}/rol`), `RevocacionTokenFilter`, `RevocacionesRedis`. Pruebas: `GatewayIntegracionTest.unCambioDeRolInvalidaElTokenDesdeLaSiguientePeticion`, `KeycloakAdminAdapterIntegracionTest.asignarRolDejaExactamenteUno/quitarRolesDejaAlUsuarioSinRoles`, Bruno `gateway/25,26,28`. Cierra la ambigüedad A4 de trazabilidad (el gateway invalida el token anterior en la siguiente petición). | Sin pantalla (HU-10). Si servicios internos se llamaran directo sin gateway, la revocación no aplica; hoy escuchan solo en `127.0.0.1`. |

Conteo RF: Cumple 7 (RF10, 11, 13, 14, 17, 18, 19), Parcial 1 (RF12), No cumple 11 (RF1–RF9, RF15, RF16).

---

## 3. Historias de usuario HU-01..HU-13

Criterios "x/n" = criterios de aceptación del SRS con código y prueba / total.

| ID | Título | Resp. | Criterios | Estado | Qué falta |
|---|---|---|---|---|---|
| HU-01 | Registro de hallazgos en campo (5 pts) | H | 0/3 | **No cumple** | Todo: formulario y validación de obligatorios ("Campos obligatorios incompletos"), adjuntos, registro del investigador. |
| HU-02 | Fichas taxonómicas flexibles (8 pts) | H | 0/3 | **No cumple** | Plantillas con atributos dinámicos, formulario que los renderiza y valida, no alterar registros históricos. |
| HU-03 | Reclasificación (3 pts) | H | 0/2 | **No cumple** | Edición por rol científico y justificación obligatoria. Los consumidores de `hallazgo.reclasificado` de J ya existen. |
| HU-04 | Asignación obligatoria de equipos (3 pts) | N | 0/3 | **No cumple** | Validar custodio activo, estado "En Expedición", vista de equipos por investigador. |
| HU-05 | Auditoría histórica (5 pts) | H | 0/3 | **No cumple** | Tabla de auditoría con valor previo/nuevo, justificación, usuario y fecha; bloqueo físico de UPDATE/DELETE; comparador de versiones. |
| HU-06 | Portal de consulta pública (5 pts) | J | 2/3 | **Parcial** | c.1 (nombre, familia, departamento): cubierto por `BusquedaFichasIntegracionTest` y Bruno `consulta-publica/01,03,04`. c.2 (ocultar coordenadas): `SinCoordenadasIntegracionTest`. c.3 (< 2 s con carga pesada de fondo): hay k6 de pico x60 sin carga científica (`pruebas-carga/resultados/asr1-*.json`, p95 de 3 ms local, 0 % fallos) pero falta el par `CIENTIFICA=0/1` (ARQ-32) y medir contra Aiven real. |
| HU-07 | Mapa interactivo para alto rango (8 pts) | J | 3/3 en backend | **Parcial** | c.1 rol alto rango (`MapaApiTest`, `PoliticaAccesoUbicacionTest`), c.2 validación central en cada petición (`SeguridadEndpointsTest`, E2E 17f), c.3 descifrado en memoria (`ServicioDescifradoTest`, `QaCifradoYReposoTest`). Falta que la pantalla `PaginaMapa.jsx` esté montada en `web-interna` (S) y datos reales de H. |
| HU-08 | API con rate limiting (8 pts) | S | 3/3 | **Cumple** | c.1 X-API-Key (`ApiKeyRateLimitFilterTest`, Bruno `gateway/11-13`), c.2 429 (`GatewayIntegracionTest.superarElLimiteDelConsumidorDa429...`, Bruno `17`), c.3 URL estable `/api/v1/externa/**` (`ProxyServiceTest`, `RutasGateway.PREFIJO_API_EXTERNA`). Pendiente menor: la corrida de GitHub Actions del workflow del gateway no se ha visto. |
| HU-09 | Reportes mensuales (13 pts) | N | 0/3 | **No cumple** | Generación asíncrona, aislamiento de carga (RNF3/ASR3), descarga PDF y CSV. Es la historia más grande (13 de 76 pts). |
| HU-10 | Registro y asignación de roles (5 pts) | S | 3/3 en API | **Parcial** | c.1 exactamente un rol (`creaUnUsuarioConExactamenteUnRol...`, Bruno `23`), c.2 sin rol solo portal público (`MatrizAccesoGatewayTest`, Bruno `28,29`), c.3 cambio desde la siguiente petición (`GatewayIntegracionTest:362`). Falta la pantalla de administración de usuarios (tarea 16j de S). Leyéndola solo por criterios, sería Cumple. |
| HU-11 | Autenticación segura (3 pts) | S (c.3: H) | 2/3 | **Parcial** | c.1 rechazo sin sesión (`GatewaySecurityTest`, `SeguridadEndpointsTest`), c.2 bloqueo a 3 intentos (`failureFactor: 3` en `infra/keycloak/realm-biohub.json:12`; `KeycloakAdminAdapterIntegracionTest.tresIntentosFallidosBloqueanLaCuenta`). c.3 (guardar el ID del usuario en cada carga/modificación/reclasificación): imposible hoy, no hay acciones de carga. Depende de H (guardar `sub`, SEC-17). |
| HU-12 | Emisión y revocación de API Keys (5 pts) | S | 3/3 en API | **Parcial** | c.1 key única por consumidor (`ServicioApiKeysTest`, `GeneradorApiKeyTest`), c.2 revocación inmediata (`GatewayIntegracionTest.cicloCompleto...`, `unaLecturaTardiaNoRevivirUnaKeyRevocada`, Bruno `19,20`), c.3 401 sin key (Bruno `11,12`). Falta la pantalla de API Keys (16j). Por criterios sería Cumple. |
| HU-13 | Búsqueda interna de hallazgos (5 pts) | J | 2/3 | **Parcial** | c.1 filtros combinables (`BusquedaHallazgosApiTest.losFiltrosSeCombinan`, `filtraPorRangoDeFechas...`), c.2 coordenadas solo por rol (`altoRangoVeLatitudYLongitud`, `losDemasRolesNoVenCoordenadas...`). c.3 (< 3 s con volumen alto): no hay prueba (CG-08 sin implementar, no existe el grupo `rendimiento`). `PaginaBusqueda.jsx` existe y está probada pero no montada en `web-interna` (S). |

Conteo HU: Cumple 1 (HU-08), Parcial 6 (HU-06, 07, 10, 11, 12, 13), No cumple 6 (HU-01, 02, 03, 04, 05, 09). Si se aceptara la API como interfaz suficiente para el administrador, HU-10 y HU-12 pasarían a Cumple (3 Cumple, 4 Parcial).

---

## 4. Cambios respecto a `trazabilidad.md` (línea base 2026-11-03)

Esa matriz estaba hecha en la iteración 15 (antes del PR #7 de Santiago y de las iteraciones 16 y 17). Cambios:

| Qué | Antes (2026-11-03) | Ahora | Por qué |
|---|---|---|---|
| RF14 | Fuera de alcance, "parcial" (filtro stub) | **Cumple** | PR #7: `ApiKeyRateLimitFilter` real, API externa `/api/v1/externa/fichas`, pruebas y Bruno `gateway/`. |
| RF17, RF19 | "Parcial" (solo realm de Keycloak) | **Cumple** (API) | `UsuariosController`, `KeycloakAdminAdapter`, `RevocacionTokenFilter`, `KeycloakAdminAdapterIntegracionTest`. Resuelve A4 (latencia de cambio de rol). |
| RF18 | "Parece hecho" | **Cumple** | Verificado con `GatewaySecurityTest`, `SeguridadEndpointsTest` y E2E 17f (50/50). |
| RF12 | Cumple | **Parcial** (bajó) | Se aplica la regla "backend sin la pantalla": la línea base ya anotaba que `App.jsx` no montaba `src/ubicacion/` y sigue igual (`App.jsx` solo hace el ping). No es una regresión de código sino un criterio más estricto. |
| HU-08 | Sin código | **Cumple** | Misma razón que RF14; además cierra A6 (ruta externa separada del portal). |
| HU-10, HU-12 | Parcial / sin código | **Parcial** (3/3 en API, sin pantalla) | Backend con pruebas; pantallas pendientes (16j). |
| HU-11 | Parcial (A5: realm toleraba 5 intentos) | **Parcial** (2/3) | `failureFactor` bajó a 3 y hay prueba; c.3 sigue sin evidencia por depender de H. |
| HU-06 c.3 | "k6 no existe" | Parcial, con evidencia local | Existen `pruebas-carga/k6/*.js` y `resultados/asr1-*.json`; falta el par con carga científica (ARQ-32) y D42 pospuesto. |
| RNF11, RNF12, ASR8, ASR9 | Parcial / sin código | Cumple en gateway (según matriz de Santiago y pruebas) | PR #7. |
| SEC-02 (`denyAll`) | Pendiente en los 4 servicios | Hecho en gateway, `servicio-cientifico` y `servicio-consulta-publica` (D55, iteración 17); **pendiente en `servicio-equipos` y `servicio-reportes`** | `SecurityConfig.java:32` de ambos sigue con `anyRequest().authenticated()`. |
| Pruebas | 157 científico / 128 consulta-pública | 322 / 239 (más 168 en gateway) | Iteraciones 16 y 17, más `SeguridadEndpointsTest` y `ArquitecturaSeguridadTest`. |
| IT-01..IT-04 | No cumple | Hechas (según `00-plan.md`: VPN retirada, Aiven con SSL, CI en GitHub verde) | Iteraciones 15–17. No son RF/HU. |
| RF1–RF9, RF15, RF16 y HU-01..05, 09 | Sin código | **Sin cambio: siguen sin código** | `registro`, `servicio-equipos` y `servicio-reportes` no tienen avances en esta rama. |

Sin cambio: RF10, RF11, RF13 (Cumple), HU-06, HU-07, HU-13 (Parcial).

---

## 5. Lo que está bien hecho

- **Seguridad de ubicación:** cifrado AES-256-GCM con AAD y llaves versionadas, política de acceso en una sola clase con doble barrera (`@PreAuthorize` + servicio), sin coordenadas en logs, errores, caché ni eventos salientes. Cubierto por 322 pruebas en científico, incluidas reglas ArchUnit (`ArquitecturaUbicacionTest`, `ArquitecturaSeguridadTest`).
- **Consulta pública:** CQRS (BD de lectura propia + eventos), caché Redis tolerante a caída, modo degradado automático, 239 pruebas, Playwright en Chromium/Firefox/WebKit y k6 con 0 % de fallos en el pico x60 local.
- **Gateway completo:** API Keys con solo hash, límite por consumidor y por IP con respaldo en memoria, revocación inmediata, circuito abierto con 503, usuarios y roles contra Keycloak, 168 pruebas y colección Bruno `gateway/` de 29 peticiones.
- **Control de acceso por defecto en denegar** en gateway, científico y consulta-pública, con E2E de 50 casos que coincide con la matriz de `11-seguridad-y-acceso.md`.
- **Infraestructura:** CI en GitHub verde, perfiles `local`/`aiven` con TLS, Flyway trazable, pruebas herméticas frente al `.env`.
- **Calidad de la documentación:** decisiones numeradas (D1–D55), contratos de eventos (`04-contrato-api.md`) y reportes QA/seguridad por iteración.

---

## 6. Top de faltantes priorizados para la entrega

| # | ID | Qué falta | Severidad | Resp. | Esfuerzo |
|---|---|---|---|---|---|
| 1 | RF1, RF2, RF3, HU-01, HU-02 | Módulo `registro`: hallazgos, adjuntos, fichas flexibles. Sin esto no entra ningún dato real y el portal, el mapa y la búsqueda quedan vacíos. Bloquea RF4, RF7–RF9, HU-11 c.3 y los reportes. | Crítico | H | L |
| 2 | RF7, RF8, RF9, HU-05 (RNF10, ASR7) | Historial inmutable append-only, versiones y comparador, quién y por qué. Es un ASR del SRS. | Crítico | H | L |
| 3 | RF15, RF16, HU-09 (RNF3, ASR3) | `servicio-reportes`: reportes asíncronos con índices de presión, cruce de registros y descarga PDF/CSV. La HU más grande (13 pts) y un ASR. | Crítico | N | L |
| 4 | RF4, HU-03 | Reclasificación con justificación obligatoria y evento `hallazgo.reclasificado` hacia consulta pública. | Alto | H | M |
| 5 | RF5, RF6, HU-04 | `servicio-equipos`: CRUD, custodio obligatorio y activo (usar `GET /api/gateway/usuarios/{id}`), estado "En Expedición", vista por investigador. | Alto | N | M |
| 6 | RF12, HU-07, HU-13 | Montar `web-interna`: router, menú por rol y rutas de `src/ubicacion/` (hoy `App.jsx` solo hace ping). Convierte en entregables visibles al mapa y la búsqueda. | Alto | S | M |
| 7 | HU-11 c.3 | Guardar el `sub` del JWT en cada carga/modificación (SEC-17). Depende de que exista `registro`. | Alto | H | S |
| 8 | HU-10, HU-12 | Pantallas de administración de usuarios y API Keys en `web-interna` (tarea 16j). | Medio | S | M |
| 9 | HU-06 c.3, HU-13 c.3 (RNF5, ASR4) | Mediciones pendientes: par k6 `CIENTIFICA=0/1` (ARQ-32), degradación bajo carga y 1 vs 2 réplicas (ARQ-30), prueba de HU-13 con 10.000 hallazgos (CG-08), todo repetido sobre `develop` y contra Aiven. Pospuestas por decisión de la usuaria hasta que estén los demás servicios. | Medio | J | M |
| 10 | SEC-02 en equipos y reportes; integración real de eventos | `denyAll` y `@PreAuthorize` en `servicio-equipos` y `servicio-reportes` (`SecurityConfig.java:32`). E2E real de `hallazgo.registrado` desde `registro` con `user_id` AMQP, SEC-J-17 (guarda de coordenadas) como condición previa, y alinear con SEC-J-19/20 (en curso). | Medio | N y J (E2E: H + J) | S |

Otros menores: RNF6/RNF7 sin prueba con usuarios (J), RNF13 sin Safari/Edge reales en `web-interna` (S, J), OBS-J17-1 (403 con cuerpo vacío en `/api/publica/eventos/recibidos`, resuelve SEC-J-20), quitar `0.0.0.0/0` en Aiven (grupo).

---

## 7. Parte de Juanita

Alcance: `servicio-cientifico` módulo `ubicacion`, `servicio-consulta-publica`, `web-publica` y las pantallas de mapa y búsqueda de `web-interna/src/ubicacion/`.

| ID | Estado | Resumen |
|---|---|---|
| RF10 | Cumple | Búsqueda y ficha públicas con pruebas JUnit, Vitest, Playwright y Bruno. |
| RF11 | Cumple | Sin coordenadas de ninguna especie en el portal (D14); aviso `AvisoUbicacionProtegida`. |
| RF12 | Parcial | Backend y componente de mapa listos y probados; falta que S monte la ruta en `web-interna`. |
| RF13 | Cumple | `PoliticaAccesoUbicacion` central, `denyAll` y `@PreAuthorize` con pruebas 401/403/2xx. |
| HU-06 | Parcial (2/3) | Falta la medición de c.3 con carga científica simultánea y contra Aiven real (ARQ-32, ARQ-30). |
| HU-07 | Parcial (3/3 backend) | Falta el montaje de la pantalla (S) y datos reales (H). |
| HU-13 | Parcial (2/3) | Falta la prueba de c.3 (< 3 s con volumen alto, CG-08) y el montaje de la pantalla (S). |

Qué puede hacer Juanita sin depender de nadie:
1. Prueba de volumen de HU-13 c.3: sembrar 10.000 hallazgos en `ubicacion_hallazgo` y medir la búsqueda combinada (CG-08). Hoy no existe el grupo de pruebas `rendimiento`. Esfuerzo S.
2. Par `CIENTIFICA=0/1` de k6 (ARQ-32) y la corrida con degradación activa (ARQ-30). Pospuestas por ella misma hasta que los demás servicios estén completos; conviene repetirlas sobre `develop` antes de la entrega. Esfuerzo M.
3. Pendientes de seguridad propios: SEC-J-19 y SEC-J-20 (en curso por otro agente), SEC-J-17 (condición previa para publicar `hallazgo.*` reales), SEC-J-18, OBS-J17-1; migrar `.env` a `cientifico_mq`/`consulta_mq` (SEC-04).
4. Confirmar con el grupo las rutas fuera de la matriz y D55; y si 120 peticiones por minuto por IP alcanzan para un campus. El aviso de 429 en `web-publica` ya está hecho (`ErrorDeConsulta.jsx`, `useCuentaRegresiva.js`, pruebas en `Inicio.test.jsx:168` y `Ficha.test.jsx:136`).
5. Pruebas de usabilidad (RNF6 "buscar ficha", RNF7 con 5 o más personas) y de navegadores reales (RNF13): sin automatizar o manuales, no medidas.

Qué necesita de otros: Hernando debe publicar `hallazgo.registrado` y `hallazgo.reclasificado` reales, con `PublicadorEventos` (que ya pone `user_id`) y llamando a `UbicacionService` o evento, para que el portal y el mapa tengan datos; Santiago debe montar `src/ubicacion/` en la navegación.
