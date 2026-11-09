# Revisión de RNF1–RNF13 y ASR1–ASR9 contra el SRS y el SAD

**Fecha:** 2026-11-08 · **Rama:** `feature/ubicacion-consulta-publica` (HEAD `6a5495a`, ya integra `develop` con el PR #7) · **Autor:** arquitecto-software · **Tipo:** revisión de solo lectura (no se modificó código ni documentos existentes; no se arrancaron servicios; no se usó el perfil `aiven`).

**Fuentes de verdad:** `docs/entregas/SRS-BioHub.txt` (§2.2 RNF, §2.2.1 ASR, líneas 158-335) y `docs/entregas/SAD-BioHub.txt` (§4.5, §4.7, §5.1-§5.3, §6). **Apoyo:** `03-arquitectura.md` (D1-D55), `10-verificacion-sad.md` (§3, §15h, §16), `trazabilidad.md` (línea base 2026-11-03 y actualización de Santiago 2026-11-05), `07-reporte-qa.md` (15h, 16, 17), `08-reporte-seguridad.md` (iteración 17), `09-despliegue.md`, `00-plan.md`, `pruebas-carga/`, `.github/workflows/ci.yml`, y el código.

**Responsables:** J = Juanita, S = Santiago, H = Hernando, N = Nicolás.

**Regla de estado (del encargo):**
- **Cumple:** táctica implementada + evidencia medida o prueba automatizada que demuestra la medida del escenario.
- **Parcial:** táctica implementada sin la medición que exige el SRS, o medida en condiciones distintas al escenario.
- **No cumple:** táctica ausente.
- **Sin evidencia:** no se puede determinar desde el repositorio.

Esta regla es más estricta que la de `10-verificacion-sad.md` §3 ("Parcial = existe una parte"). Por eso ASR3 pasa de Parcial a No cumple sin que haya cambiado el código (ver §5).

**Trabajo en curso al revisar:** otro agente está corrigiendo SEC-J-19/SEC-J-20 (403/401 en problem+json). En el árbol de trabajo hay cambios sin commit en los dos `SecurityConfig`, en `ConsultaPublicaExceptionHandler` y dos `ProblemaSeguridadHandler.java` nuevos. **No se evaluaron.** Aquí figuran como "en curso".

---

## 1. Resumen ejecutivo

| Estado | RNF (13) | ASR (9) |
|---|---|---|
| **Cumple** | 3: RNF9, RNF11, RNF12 | 3: ASR6, ASR8, ASR9 |
| **Parcial** | 6: RNF1, RNF2, RNF5, RNF6, RNF7, RNF13 | 3: ASR1, ASR2, ASR4 |
| **No cumple** | 4: RNF3, RNF4, RNF8, RNF10 | 3: ASR3, ASR5, ASR7 |
| **Sin evidencia** | 0 | 0 |

**Por responsable:**
- **Juanita:** RNF9/ASR6 Cumple. RNF1/ASR1, RNF5/ASR4, RNF6 (la parte de buscar ficha), RNF7 y RNF13 están en Parcial. Faltan mediciones (k6 de D42 y pruebas de usabilidad); el diseño ya está.
- **Santiago:** RNF11/ASR8 y RNF12/ASR9 Cumple (PR #7). Siguen pendientes el bulkhead (ARQ-24) y las réplicas del gateway detrás de NGINX.
- **Hernando:** RNF4, RNF8/ASR5 y RNF10/ASR7 No cumple. RNF2/ASR2 está en Parcial solo por el aislamiento que hicieron otros. El módulo `registro` solo tiene `package-info.java`, y `origin/feature/registro-cientifico` no tiene commits propios: apunta a `e00285f`, igual que `develop` el 2026-11-04.
- **Nicolás:** RNF3/ASR3 No cumple. `servicio-reportes` solo consume eventos de prueba, y `origin/feature/reportes-equipos` tampoco tiene commits propios (`e00285f`).

**Riesgo principal para la entrega.** Tres de los cuatro atributos críticos del SAD (§3.2: integridad, modificabilidad, reportes) no tienen código: ASR7, ASR5 y ASR3, de Hernando y Nicolás. Sin ellos tampoco se pueden medir RNF2, RNF3 ni RNF4. Hay un segundo riesgo, propio de la parte de Juanita: **ninguna corrida k6 del repositorio cumple los criterios de D42.** Los JSON versionados (`pruebas-carga/resultados/*.json`) se generaron antes de que el script guardara los metadatos. No registran `replicas`, `cpus_por_replica`, `commit`, `red_k6` ni `exito_d42_pct`, y no existe ninguna corrida con degradación activa ni el par `asr4-sin`/`asr4-con`. ASR1 y ASR4 se pueden defender en lo cualitativo, pero hoy no tienen una medición que cumpla D42.

---

## 2. Tabla RNF1–RNF13

| ID | Atributo y medida (SRS) | Resp. | Táctica / código | Evidencia | Estado | Qué falta |
|---|---|---|---|---|---|---|
| **RNF1** | Disponibilidad. Pico ≥ 60x en consulta pública: ≥ 95 % sin error de servidor, secundarias desactivables, estabilización < 5 min | J (rate limit: S) | Servicio aislado; caché Redis tolerante (`ConsultaFichasService.java:29,38` `@Cacheable(sync=true)`, `CacheTolerante`); degradación automática con Strategy (`BusquedaDegradada.java:7-25`, `ElectorEstrategiaBusqueda`, `MonitorCarga`, umbrales en `UmbralesDegradacion.java:25-33`: en curso 100/50, p95 1000/500 ms); forzada por `CONSULTA_MODO_DEGRADADO` (`application.yml` de consulta-publica, línea del bloque `publica.modo-degradado`); réplicas + NGINX solo en `pruebas-carga/compose-carga.yml:10-14` y `nginx-carga.conf:6` | Pruebas: `DegradacionDinamicaWebTest.degradaConLaCargaYVuelve`, `DegradacionForzadaWebTest`, `EstrategiaBusquedaTest`, `MonitorCargaTest`, `RedisCaidoIntegracionTest.conRedisCaidoLasConsultasSigueRespondiendoDesdeMysqlYElHealthSigueUp`. k6: `asr1-1replica-n60.json` (519.599 peticiones, 0 % no-200, p95 3,0 ms, estabilización 0 s), `asr1-2replicas-n60.json`, `limite-2replicas-n100.json` | **Parcial** | Las corridas **no cumplen D42**: su `parametros` no tiene `replicas`, `cpus_por_replica`, `commit` ni `red_k6`, y falta `exito_d42_pct`. La degradación nunca se activó bajo carga (ARQ-30). Con N = 60, 1 y 2 réplicas no se distinguen. N es un valor supuesto. k6 entra por NGINX directo, sin pasar por el gateway (D33) |
| **RNF2** | Carga de datos científicos ≤ 5 s con pico público x60 | H (recursos: S) | Servicio y base de escritura separados: `servicio-cientifico` con base `cientifico` en el servicio Aiven de escritura (D39); consulta pública en el de lectura. Sin límites de CPU/memoria para científico (ARQ-24) | `MigracionesIntegracionTest.laFichaPublicaNoTieneColumnasDeCoordenadasNiDeUsuario` (consulta-publica no toca `cientifico`); 00-plan 17a: conectividad TLS a los dos servicios Aiven | **Parcial** | El módulo de carga no existe (`registro/**` vacío). `k6/registro-hallazgo.js:1` "PENDIENTE - NO MEDIBLE". Falta la corrida combinada pico + registro |
| **RNF3** | El reporte mensual no deja indisponible el registro diario ni lo degrada más de 10 % | N | Hay infraestructura: cola `reportes.eventos` + DLQ (`servicio-reportes/.../config/RabbitConfig.java`) y base `reportes` aparte. No hay generación de reportes, flujo 202 ni modelo analítico | `ConsumidorEventosIntegracionTest` (solo consume eventos de prueba) | **No cumple** | HU-09 completa (solicitud asíncrona, procesamiento en cola, base analítica) y la medición del registro con y sin reporte (Δ ≤ 10 %) |
| **RNF4** | Registrar hallazgo con archivos ≤ 5 s en condiciones normales | H | No existe. Lo que sí hay: el gateway ya no consume el multipart (D51, `GatewayIntegracionTest.unAdjuntoMultipartLlegaCompletoAlServicio`) | `k6/registro-hallazgo.js` (esqueleto PENDIENTE) | **No cumple** | HU-01 y la corrida k6 de registro con adjunto |
| **RNF5** | Consulta pública ≤ 2 s con carga científica simultánea | J | CQRS: `ConsumidorEventos` → `ProcesadorHallazgos` → `ProyectorFichas`/`EscrituraFichasJdbc`; caché TTL 5 min / 60 s (`application.yml:95-96`); Hikari `connection-timeout: 2000` (`:17`); Redis 300 ms (`:38-39`) | `ConsumidorHallazgosIntegracionTest`, `CacheRedisIntegracionTest`, `BusquedaFichasIntegracionTest`. Script `k6/carga-cientifica-simultanea.js` corregido (BUG-J2: `user_id` y publicador en el compose) | **Parcial** | No hay `asr4-sin.json` ni `asr4-con.json` (ARQ-32). Los p95 de ASR1 (≤ 25,5 ms) se midieron **sin** carga científica |
| **RNF6** | Usuario nuevo aprende a buscar ficha y registrar hallazgo en < 5 min, ≤ 3 pasos, error ≤ 5 % | J (buscar) · H (registrar) | Buscar: `Inicio.jsx` → `Ficha.jsx`, 3 pasos | E2E Playwright `portal.spec.js:22` "buscar y abrir la ficha en 3 pasos" y `:123` (solo con teclado). Corre en CI en Chromium, Firefox y WebKit (`ci.yml:38`) | **Parcial** | Prueba de usabilidad con personas (UX-04: tiempo y tasa de error). La mitad "registrar hallazgo" no existe (H) |
| **RNF7** | ≥ 90 % de visitantes identifica por qué no ve las coordenadas | J | `AvisoUbicacionProtegida.jsx`; `ubicacionProtegida: true` siempre (D27) | E2E `portal.spec.js:39,59,74` (aviso visible; aunque la API envíe coordenadas por error, no se muestran) | **Parcial** | Prueba de usabilidad UX-01..UX-03 (≥ 5 participantes, % de aciertos). No hay registro de que se haya ejecutado (`07` 15h §6) |
| **RNF8** | Agregar o editar un campo personalizado en < 2 min sin ayuda, éxito ≥ 90 % | H | No existe la columna JSON ni la fábrica de atributos (SAD §4.7) | — | **No cumple** | HU-02 (columna JSON + interfaz) y la prueba de usabilidad |
| **RNF9** | Coordenadas cifradas con AES-256, nunca en texto plano | J | `CifradorAesGcm.java:26-28` (AES/GCM, tag de 128 bits, nonce de 12 B por cifrado, AAD = UUID); `V20261026_1010__ubicacion_hallazgo.sql:3,15-16` (sin columnas lat/lon); cadena Decorator auditoría → cifrado → JPA (`ConfiguracionUbicacion`); llaves fuera de la base (`ProveedorDeLlavesEntorno`/`ProveedorDeLlavesKeyStore`, D20/D40) | `QaCifradoYReposoTest.cf04_cf08_mismasCoordenadasEnTresEstadosGuardanSoloCifradoYDistinto`, `cf03_…NuncaRepitenNonceNiTexto`, `cf07_alterarCualquierByte…`; `MigracionesUbicacionTest`; `QaBarridoCoordenadasInternoTest`; `ObservadorUbicacionCifradoFallaTest`. Auditoría 17: el cifrado de producción no cambió (`08` §3) | **Cumple** | Residuales Bajos: SEC-J-17 (la guarda del publicador se elude con números) y SEC-J-18 (la auditoría falla abierta). No afectan la persistencia cifrada |
| **RNF10** | Historial inmutable: rechazar update/delete sin excepción de rol | H (permisos: dba) | No existe: sin `VersionHallazgo.Builder`, sin tabla de historial. `cientifico_app` tiene `SELECT, INSERT, UPDATE, DELETE` sobre toda la base (`deploy/aiven/01-escritura-bases-y-usuarios.sql.example:81`) | — | **No cumple** | HU-05, tabla append-only, `GRANT SELECT, INSERT` sin UPDATE/DELETE sobre el historial y una prueba con MySQL que intente UPDATE/DELETE como cada rol |
| **RNF11** | API pública: 60 peticiones/min por consumidor, configurable por el administrador | S | `ApiKeyRateLimitFilter.java:109` (límite propio del consumidor o el de por defecto); `PropiedadesLimites.java:19-20` (60 y 120); `api-gateway/.../application.yml:90,92`; ventana fija en Redis con respaldo en memoria (D46); `ConsumidoresController` + `ServicioConsumidores.cambiarLimite` | `GatewayIntegracionTest.superarElLimiteDelConsumidorDa429YCambiarloSeAplicaDeInmediato` (`:245`), `conPeticionesSimultaneasPasanExactamenteLasDelLimite` (`:274`), `ApiKeyRateLimitFilterTest.superarElLimiteDa429ConRetryAfterYNoLlegaAlServicio`, `RedisCaidoIntegracionTest.elPortalYLaApiExternaSiguenFuncionandoYLimitando`; E2E de QA 17 (`X-RateLimit-Limit: 60`, 429 del portal con `Retry-After`) | **Cumple** | Falta la pantalla del administrador en `web-interna` (16j); hoy se configura por API. Consecuencia aceptada en D46: al cambiar de ventana puede pasar hasta el doble del límite |
| **RNF12** | Migrar la infraestructura interna sin cambios para los consumidores externos | S (web-publica: J) | Proxy inverso: `RutasGateway.java:21` (`/api/v1/externa`); `ProxyService.reenviarApiExterna`; destinos por variable `biohub.gateway.rutas.*`; web-publica solo usa `VITE_API_URL` | `ProxyServiceTest.laApiExternaTraduceLaUrlPublicaALaInterna`, `laApiExternaConservaElIdentificadorDeLaFicha`; `GatewayIntegracionTest` (destino en un puerto aleatorio inyectado por propiedad). Migración real: en la iteración 15 la base pasó de MySQL local a Aiven solo por perfil (D36), sin cambiar la URL de Bruno ni de web-publica | **Cumple** | No hay URL pública ni TLS (D34: sin entorno compartido). Conviene un simulacro documentado: cambiar `SERVICIO_PUBLICA_URL` y repetir Bruno `gateway/` |
| **RNF13** | Portal y API en las 2 últimas versiones de Chrome, Firefox, Edge y Safari | J (web-publica, `src/ubicacion`) · S (web-interna) | Vite + React 19; CSP probada | CI: `npx playwright install --with-deps chromium firefox webkit` (`ci.yml:38`) + `npm run test:e2e`; `playwright.config.js:9-17` (Edge solo con `E2E_EDGE=1`) | **Parcial** | Solo se prueba la versión de cada motor que trae Playwright, no "las dos últimas". Edge no corre en CI. WebKit no es Safari real. `web-interna` no tiene E2E ni está en CI, y `src/ubicacion` no está montada (`App.jsx`, 16j) |

---

## 3. Tabla ASR1–ASR9

| ID | Escenario (estímulo → respuesta → medida) | Táctica del SAD | Implementación | Evidencia | Estado | Qué falta |
|---|---|---|---|---|---|---|
| **ASR1** (RNF1) | Pico ≥ 60x tras un anuncio → se desactivan funciones secundarias → ≥ 95 % de éxito, estabilización < 5 min | §4.7 servicio aparte que escala solo; §5.1 bulkhead, rate limiting, redundancia y escalado tras NGINX, degradación controlada, caché Redis; §6.5 Decorator de caché y Strategy completa/degradada | Servicio y base propios; Strategy + `MonitorCarga` con histéresis; caché tolerante a la caída de Redis; límite por IP en el gateway (120/min, D45); réplicas + NGINX solo en el compose de carga | Igual que RNF1 | **Parcial** | Corridas K1-K4 de §7. NGINX resuelve las réplicas solo al arrancar, así que no se pueden agregar en pleno pico (ARQ-30 c). Las réplicas no son parte de ningún despliegue (solo del banco de pruebas, como permite SAD §5.3) |
| **ASR2** (RNF2) | Carga científica durante el pico público → el módulo de carga mantiene su tiempo → ≤ 5 s | §4.7 recursos reservados y base de escritura propia; §5.1 bulkhead | Separación de servicio y de servidor de base (D39). Sin `cpus`/`mem_limit` para científico (ARQ-24) | Igual que RNF2 | **Parcial** | El registro (H), los límites de recursos (S) y la corrida K7 |
| **ASR3** (RNF3) | Reporte mensual sobre millones de registros → el registro diario sigue disponible → incremento ≤ 10 % | §4.7 y §5.1 procesamiento asíncrono con colas y base analítica propia (SD-08) | Cola + DLQ + consumidor de eventos y base `reportes`. **No hay** procesamiento de reportes | `ConsumidorEventosIntegracionTest` | **No cumple** | HU-09 con 202 + cola + worker + modelo analítico; corrida K8 |
| **ASR4** (RNF5) | Consultas públicas con carga científica simultánea → responde → ≤ 2 s | §4.7 y §5.1 CQRS con copia de lectura alimentada por eventos; caché distribuida; §6.5 Observer | Implementado y conectado (`10` §15h.2). ARQ-26 cerrado: `PublicadorEventos.java:53,83` pone `user_id` (SEC-J-11; E2E de QA ciclo 1 con el publicador real) | Igual que RNF5 | **Parcial** | Corrida K5 (`CIENTIFICA=0/1`). Sigue el riesgo de que `reportes` comparta el servidor de lectura con `consulta_publica` (D39) |
| **ASR5** (RNF8) | Biólogo necesita un atributo no estándar → lo agrega sin soporte → < 2 min, sin afectar registros existentes | §4.7 columna JSON en MySQL; SD-09 fábrica de atributos | No existe | — | **No cumple** | HU-02 (H) y la prueba de usabilidad |
| **ASR6** (RNF9) | Se almacenan coordenadas de una especie en peligro → se cifran antes de persistir → AES-256, siempre | §4.7 y §5.2 AES-256 con llaves en JKS separado de la base; las coordenadas no salen del servicio; auditoría; §6 Adapter, Decorator, Strategy, Observer | Todo implementado y conectado (`10` §15h.2). Llaves: proveedor `entorno` por defecto y `keystore` PKCS12 como alternativa (`application.yml:78` de científico) | Igual que RNF9, más `ProveedorDeLlavesKeyStoreTest`, `DecoradorAuditoriaIntegracionTest`, `EstrategiasVisibilidadTest`, `ArquitecturaUbicacionTest` | **Cumple** | Ajustar el texto del SAD: "entorno por defecto, JKS como adaptador" (ARQ-11/ARQ-25). La auditoría solo va a un log (ARQ-23, Could) |
| **ASR7** (RNF10) | Cualquier rol, incluso administrador, intenta modificar o borrar el historial → se rechaza → 100 % del historial preservado | §4.7 versiones append-only; §6.2.1 Builder; §5.2 usuario de BD con solo INSERT/SELECT sobre el historial | No existe. El usuario de aplicación tiene UPDATE/DELETE (`deploy/aiven/01-…sql.example:81`). Flyway cae al usuario de aplicación si falta `CIENTIFICO_MIGR_USER` (`servicio-cientifico/.../application.yml:31`, ARQ-16) | — | **No cumple** | HU-05 (H); permisos por tabla (dba); usuario de migración obligatorio en `aiven` |
| **ASR8** (RNF11) | Script externo genera demasiadas peticiones → el gateway limita el exceso → 60/min, configurable | §4.7 rate limiting y API Keys en el gateway (SD-07) | Implementado (D45-D47) | Igual que RNF11 | **Cumple** | Pantalla del administrador (S, 16j) |
| **ASR9** (RNF12) | Se migran los servidores internos → el punto de conexión no cambia → 0 cambios para los consumidores | §4.7 proxy inverso: los consumidores solo conocen la URL del gateway | API externa versionada `/api/v1/externa/fichas/**` (D45); destinos por variable | Igual que RNF12 | **Cumple** | Simulacro documentado; URL pública con TLS si hubiera despliegue |

---

## 4. Conformidad con el SAD: desviaciones

| # | SAD dice | Código hoy | ¿Justificada? | Comentario |
|---|---|---|---|---|
| 1 | §5.1: varias instancias del **gateway**, de la consulta pública y del **científico** detrás de NGINX, para que el gateway no sea punto único de falla | NGINX y réplicas **solo** para consulta-publica, y solo en el banco de carga (`pruebas-carga/compose-carga.yml`). Gateway y científico: una instancia en el host | **En parte.** SAD §5.3 permite demostrar el escalado de consulta pública con Compose. Para el gateway y el científico **no hay decisión registrada** | El gateway es punto único de falla y **ninguna prueba de carga lo atraviesa** (D33 mide directo contra consulta-publica). Hace falta un ADR (o una corrida con 2 gateways detrás de NGINX) |
| 2 | §5.2/§5.3: servicios internos en una red privada de contenedores, sin acceso desde internet | Servicios en el host en `127.0.0.1` (D2, D7); red privada y VMs retiradas | **Sí: D34** (consecuencias anotadas). D55 depende de esto | Los Dockerfile ponen `SERVER_ADDRESS=0.0.0.0` (`servicio-cientifico/Dockerfile:14`, SEC-J-I19). Si se dockeriza el backend, hace falta una red `internal` sin puertos publicados |
| 3 | §5.2: toda la comunicación externa pasa por el gateway **con TLS** | Gateway en HTTP local, sin `server.ssl`. TLS solo hacia Aiven (D35) | **No hay decisión explícita.** D34 solo habla de Keycloak (`sslRequired=external`) | Registrar que en el prototipo no hay TLS externo porque no hay entorno publicado (D34). Riesgo bajo mientras todo sea local |
| 4 | §4.5: Científico + Equipos en la base transaccional; Consulta Pública y Reportes con **bases propias** para no competir | Local: 4 MySQL (conforme). Aiven: 2 servicios (D39). El de lectura aloja `consulta_publica`, `reportes` y `gateway` | **Sí: D35/D39** (plan gratuito: un MySQL por organización) | La base analítica comparte servidor físico con la consulta pública. Cuando exista HU-09, un reporte pesado podría degradar ASR1/ASR4. Mitigación: ejecutar el reporte con lecturas por lotes y límite de conexiones (`MAX_USER_CONNECTIONS 10` para `reportes_app`, ARQ-17), o un tercer servicio |
| 5 | §4.7/§5.2: llaves en **Java KeyStore** | `entorno` por defecto; `keystore` PKCS12 como adaptador alternativo | **Sí: D15/D20/D40** (ARQ-11 cerrado en código) | Falta corregir el texto del SAD (ARQ-25) |
| 6 | §5.1: circuit breaker que responde un valor por defecto | Gateway: `CircuitoServicios` propio (5 fallos, 10 s abierto, 503 `SERVICIO_NO_DISPONIBLE` con `Retry-After`; `application.yml:96-99`) | **Sí: D52** (Resilience4j descartado) | Consulta pública usa Redis tolerante como "valor por defecto" (D25). Agregar al SAD §6.5 los patrones del gateway que lista D52 |
| 7 | §5.1: health checks y reinicio de instancias caídas | `/actuator/health` con probes en los servicios; healthchecks solo en la infraestructura del compose. Nadie reinicia los servicios | **No explícita** (se deduce de D2/D34) | Aceptable en el prototipo; declararlo junto al punto 1 |
| 8 | §5.1: bulkhead con recursos propios por contenedor | `cpus`/`mem_limit` solo en las réplicas de carga | **No:** ARQ-24 abierto (S) | Afecta la defensa de ASR2 |
| 9 | §6.2.1 Builder, §5.2 append-only + INSERT/SELECT | Ausentes | No (pendiente de H) | ASR7 |
| 10 | §6.4.2: Observer con dos oyentes (`ObservadorUbicacion` en la misma transacción + `@TransactionalEventListener` hacia el Adaptador de Mensajería) | El primero existe y está probado. El segundo **no existe**: solo se menciona en el Javadoc de `EventoDominio.java:10` | Pendiente de H (D53: registro publica eventos de dominio) | Sin ese oyente, ningún `hallazgo.*` real sale hacia consulta-publica ni reportes (ARQ-07) |
| 11 | §5.2: autorización centralizada | Gateway grueso (D48) + `@PreAuthorize` por método (D55) | **Sí: D48, D55** (aceptada por ciberseguridad con condiciones) | Condiciones SEC-J-19/SEC-J-20 **en curso**; SEC-J-21 (H) |
| 12 | §4.8: eventos solo con ubicación generalizada; publicador autenticado | Guarda en `PublicadorEventos` (D54) y detector en el consumidor; `user_id` verificado | Sí (D24, D54) | Los servicios de Juanita siguen con el usuario AMQP administrador (SEC-04: migrar los `.env` a `cientifico_mq`/`consulta_mq`) |

---

## 5. Cambios respecto a `trazabilidad.md` (2026-11-03) y `10-verificacion-sad.md` §15h (2026-11-04)

| ID | Antes | Ahora | Qué lo cambió |
|---|---|---|---|
| RNF11 / ASR8 | Sin código / Ausente (filtro stub, ARQ-03) | **Cumple** | PR #7: D45-D47, `GatewayIntegracionTest` y E2E de QA 17 |
| RNF12 / ASR9 | Parcial (faltaba la API externa versionada) | **Cumple** | PR #7: `/api/v1/externa/fichas/**` (D45) |
| ARQ-20 (circuit breaker, SAD §5.1) | Abierto | **Cerrado** | D52, `CircuitoServiciosTest`, `GatewayIntegracionTest.unServicioCaidoAbreElCircuitoYResponde503AlInstante` |
| ARQ-21 (bloqueo a 3 intentos) | Abierto | **Cerrado** | Realm `failureFactor: 3`, `KeycloakAdminAdapterIntegracionTest` |
| SEC-02 (denyAll en servicios) | Solo gateway | **Cerrado** en gateway, científico y consulta-publica; pendiente en equipos y reportes (N) | Iteración 17: d0be1be, a0e0d32, D55; `SeguridadEndpointsTest` (56 + 40 casos) |
| ARQ-26 (Alto) | Abierto | **Cerrado** | SEC-J-11; `PublicadorEventos.java:53,83`; E2E de QA ciclo 1 |
| ARQ-05 | Parcial (sin evidencia del segundo servicio Aiven) | **Cerrado** (documental) | 00-plan 17a: conectividad TLS `VERIFY_IDENTITY` a escritura y lectura; 6a5495a |
| ARQ-28 / ARQ-29 / ARQ-33 | Abiertos | Cerrados (ARQ-28b aceptado) | `10` §16, D53 |
| BUG-J2 / BUG-J3 (k6) | Abiertos | Cerrados | 0e959d2: `user_id` en k6, publicador en el compose, README de carga y metadatos D42 en el script |
| **Corridas k6 de ASR1** | `10` §15h.3: "cumplen el umbral de ASR1" | **No cuentan según D42**, aunque cumplan el umbral | Al versionarlas se ve que los JSON no tienen `replicas`, `cpus_por_replica`, `commit`, `red_k6` ni `exito_d42_pct` (criterio 1 de D42). El estado de ASR1 no cambia (Parcial), pero la evidencia queda más débil de lo que decía §15h |
| ARQ-30 / ARQ-31 / ARQ-32 | Abiertos | ARQ-31 **parcial** (README y script listos, faltan corridas con metadatos); ARQ-30 y ARQ-32 **abiertos** | Pospuestos por la usuaria hasta tener todos los servicios (00-plan, "Pendientes tras la 15h") |
| ASR3 | Parcial ("infraestructura") | **No cumple** | No cambió el código: cambió la regla (el procesamiento asíncrono del reporte no existe) |
| D55 / SEC-J-19, 20, 21 | — | Nuevos (Bajos); SEC-J-19/20 **en curso** | Iteración 17 |
| CI | Verde en 740bb37 (15h); `develop` en rojo tras el PR #6 | Pruebas inestables corregidas (b4053c3, d9a9031, 124d516); QA 17: 322/239/168 en UTC y Bogotá | **Verde** en GitHub Actions sobre `6a5495a` (run 42548966490, 5/5 jobs; verificado por el Tech Lead con `gh run view`) |
| `trazabilidad.md` | Líneas 9, 112 y 122 dicen que no hay scripts de carga y que el filtro es un stub | Desactualizado (la nota de la línea 10 ya remite a §15h) | Ya señalado en BUG-J3; no se corrige aquí (solo lectura) |

---

## 6. Lo que está bien hecho

- **ASR6 de punta a punta:** AES-256-GCM con AAD; sin columnas en claro, garantizado por un `CHECK`; Decorator único con eslabones *package-private*; Strategy con vista generalizada por defecto; doble barrera garantizada con ArchUnit (D44); Observer que revierte si el cifrado falla. Las pruebas de alteración ahora son deterministas (124d516).
- **ASR8/ASR9 del PR #7:** límite atómico en Redis con respaldo en memoria. Hay una prueba de concurrencia que comprueba que pasan **exactamente** las peticiones del límite, y una revocación inmediata que no se reabre por lecturas tardías (`unaLecturaTardiaNoRevivirUnaKeyRevocada`). La API externa es de solo lectura y no expone `/api/publica/eventos/**`.
- **Circuit breaker sencillo y probado** (D52): distingue una caída de un 4xx/5xx del servicio, que no abre el circuito.
- **CQRS de consulta pública** con "gana la versión mayor", invalidación de caché después del commit y Redis fuera del health. Con Redis caído, la consulta sigue respondiendo desde MySQL (`RedisCaidoIntegracionTest`).
- **Degradación automática real** (Strategy + monitor con histéresis), probada en web (`DegradacionDinamicaWebTest`), con el modo forzado como palanca operativa.
- **Banco de carga reproducible y aislado:** Compose efímero que no toca Aiven (D30), versión de k6 fija, cola de carga propia, `limpiar.sh`, criterio de evidencia escrito antes de medir (D42) y archivos `descartado-*` conservados con su motivo.
- **Seguridad por defecto en los servicios:** `denyAll` con matchers exactos, `@PreAuthorize` exigido por una prueba de arquitectura y matrices endpoint × rol exhaustivas.
- **Decisiones trazables:** las desviaciones importantes (D34, D39, D40, D45-D55) tienen alternativa descartada y consecuencias.

---

## 7. Top de faltantes priorizados

| # | ID | Faltante | Sev. | Resp. | Esf. |
|---|---|---|---|---|---|
| 1 | ASR7 / RNF10 | Historial append-only con `VersionHallazgo.Builder`, tabla de historial, `GRANT SELECT, INSERT` sin UPDATE/DELETE y usuario de migración obligatorio en `aiven` (ARQ-01, ARQ-16); prueba MySQL de UPDATE/DELETE rechazados para cada rol | **Crítico** | H (+ dba) | L |
| 2 | ASR3 / RNF3 | HU-09: solicitud 202, cola, worker y modelo analítico; medición del registro con y sin reporte (K8) | **Crítico** | N | L |
| 3 | ASR5 / RNF8 | HU-02: columna JSON + fábrica de atributos + pantalla; prueba de usabilidad (≥ 90 %, < 2 min) | **Crítico** | H | M |
| 4 | RNF4 / ASR2 | HU-01 con adjuntos y el oyente `@TransactionalEventListener` → `PublicadorEventos` (SAD §6.4.2, ARQ-07); completar `k6/registro-hallazgo.js` (K6) | **Alto** | H | M |
| 5 | ASR1 / RNF1 | Corridas k6 K1-K4 con metadatos D42, sobre `develop` | **Alto** | J | M |
| 6 | ASR4 / RNF5 | Par K5 `CIENTIFICA=0/1` | **Alto** | J | S |
| 7 | ASR2 / RNF2 | Corrida combinada K7 (pico x60 + registro) y `cpus`/`mem_limit` por servicio (ARQ-24) | **Alto** | H + J (medición), S (recursos) | M |
| 8 | SAD §5.1 | Gateway como punto único de falla: ADR que lo acepte en el prototipo, o 2 gateways detrás de NGINX con una corrida K9 a través del gateway | **Medio** | S + Tech Lead | S (ADR) / M |
| 9 | RNF6 / RNF7 | Pruebas de usabilidad UX-01..UX-04 con ≥ 5 personas, con registro de tiempo, pasos, error y % de comprensión | **Medio** | J (buscar, RNF7), H (registrar) | S |
| 10 | RNF13 | Edge en CI (`E2E_EDGE=1` o `channel: msedge`), E2E de `web-interna` en CI, montar `src/ubicacion` (16j) y una matriz manual documentada con las 2 últimas versiones | **Medio** | J (web-publica), S (web-interna) | M |
| 11 | D55 | SEC-J-19 y SEC-J-20 (**en curso**) antes de que `registro` integre controladores; SEC-J-21 en los endpoints con cuerpo | **Medio** | J (19, 20), H (21) | S |
| 12 | SAD (texto) | ARQ-25 y desviaciones 3, 5, 6 y 7 de §4: JKS como alternativa, degradación automática, patrones del gateway, sin TLS externo ni reinicio en el prototipo, D39 | **Medio** | Tech Lead + J | S |
| 13 | SEC-04 | Migrar los `.env` de científico y consulta-publica a `cientifico_mq`/`consulta_mq` | **Bajo** | J | S |
| 14 | SEC-J-17 / SEC-J-18 | Guarda de coordenadas con números/objetos; auditoría que falla abierta | **Bajo** | J | S |
| 15 | SEC-02 | `denyAll` en equipos y reportes | **Bajo** | N | S |

### Corridas k6 de D42 que faltan y qué medirían

Todas en el Compose efímero (D30), nunca contra Aiven, con `REPLICAS`, `CPUS` y commit sin cambios pendientes (que no salga `-sin-commit`), y repetidas sobre `develop` antes de la entrega (00-plan).

| Corrida | Configuración | Qué demuestra | Bloqueo |
|---|---|---|---|
| **K1** `asr1-degradacion-n60` | Umbral bajo (`…_DEGRADACION_EN_CURSO_ENTRADA=10` o `…_P95_ENTRADA_MS=20`); el script debe contar con un `Counter` las respuestas `modoDegradado: true` y guardar `/actuator/info` antes, durante y después | La frase del SRS "desactivando funciones secundarias" con ≥ 95 % de éxito, y que el modo degradado se apaga solo (estabilización < 5 min) | Hay que añadir el contador al script (no existe: `grep modoDegradado k6/` sin resultados) |
| **K2** `asr1-1replica-n100` y `asr1-2replicas-n100` (o `CARGA_CPUS=1` para saturar antes) | Mismo perfil; 1 vs 2 réplicas en un punto donde una sola no alcance | Que el escalado horizontal es necesario y ayuda (SAD §5.1/§5.3) | Ninguno |
| **K3** `asr1-escalado-en-caliente` | `resolver 127.0.0.11 valid=5s` + `server … resolve` en `nginx-carga.conf`; `--scale 1→2` durante el pico | "Ante un pico, se agregan instancias" y el tiempo de estabilización real | Cambio pequeño en NGINX (ARQ-30 c) |
| **K4** Repetir `asr1-1replica-n60` y `asr1-2replicas-n60` con metadatos | `REPLICAS`, `CPUS`, `COMMIT`, `RED_K6`; `exito_d42_pct` | Volver válida (según D42) la evidencia que ya existe | Ninguno |
| **K5** `asr4-sin` / `asr4-con` | `CIENTIFICA=0` y `1`, `R=300`, `EV=30`; DLQ = 0 y procesados = publicados | RNF5: p95 ≤ 2 s con carga científica simultánea, y cuánto se degrada | Ninguno |
| **K6** `rnf4-registro` | `registro-hallazgo.js` con adjunto de 1-2 MB | RNF4: ≤ 5 s en condiciones normales | HU-01 (H) |
| **K7** `asr2-registro-en-pico` | K6 en paralelo con el pico x60 de ASR1 | RNF2/ASR2: el registro sigue en ≤ 5 s | HU-01 (H) |
| **K8** `asr3-reporte-vs-registro` | K6 con y sin el reporte mensual en ejecución | RNF3/ASR3: incremento ≤ 10 % | HU-09 (N) + HU-01 (H) |
| **K9** (recomendada) `asr1-por-gateway` | Pico a través del gateway con el límite por IP elevado en el entorno de carga (D33) | Capacidad del gateway, punto único de falla de SAD §5.1 | Ninguno |

---

## 8. Parte de Juanita

| ID | Estado | Lo que está | Lo que falta (prioridad) |
|---|---|---|---|
| RNF1 / ASR1 | Parcial | Aislamiento, caché tolerante, degradación automática y forzada, réplicas + NGINX en el banco, scripts y README D42 | K1, K2, K3, K4 (Alto, M). El contador `modoDegradado` y el `resolver` de NGINX son cambios pequeños en `pruebas-carga/` |
| RNF5 / ASR4 | Parcial | CQRS + caché; ARQ-26 cerrado; script de carga científica corregido | K5 (Alto, S) |
| RNF9 / ASR6 | **Cumple** | Cifrado, llaves, Decorator, Strategy, Observer, ArchUnit, pruebas QA | SEC-J-17 y SEC-J-18 (Bajos); texto del SAD (ARQ-25); ARQ-23 (Could); decisión sobre SEC-J-I16 (usuario con `administrador` y `cientifico_alto_rango` a la vez) |
| RNF6 (buscar ficha) | Parcial | Flujo de 3 pasos con E2E en 3 motores, incluido el teclado | UX-04 con personas (Medio, S) |
| RNF7 | Parcial | Aviso y E2E, incluido el caso en que la API envía coordenadas por error | UX-01..UX-03 (Medio, S) |
| RNF13 (web-publica y `src/ubicacion`) | Parcial | Playwright Chromium/Firefox/WebKit en CI | Edge en CI; matriz de las "2 últimas versiones"; E2E de las pantallas de ubicación cuando S las monte (16j) |
| ASR9 (web-publica) | Cumple en su parte | Solo `VITE_API_URL`; servicios en `127.0.0.1` | — |
| D55 | Aceptada con condiciones | `denyAll` + `@PreAuthorize` + matrices | SEC-J-19 y SEC-J-20 **en curso** (otro agente); OBS-J17-1 se resuelve con el mismo handler |
| Configuración | — | — | SEC-04: migrar los `.env` a `cientifico_mq`/`consulta_mq`, con `BIOHUB_EVENTOS_PUBLICADOR_CIENTIFICO=cientifico_mq` (Bajo, S) |

**Orden sugerido para Juanita:**
1. Cerrar SEC-J-19 y SEC-J-20 (ya en curso).
2. Añadir el contador `modoDegradado` y el `resolver` de NGINX.
3. Cuando los servicios de todos estén en `develop`, correr K4 → K2 → K1 → K3 → K5 en una misma sesión y versionar los JSON.
4. Ejecutar UX-01..UX-04 con 5 personas y registrar los resultados en `07`.
5. Hacer los ajustes de RNF13.

Con eso, ASR1 y ASR4 pueden pasar a Cumple según D42 y RNF6 y RNF7 a Cumple en la parte de Juanita. RNF13 seguirá en Parcial mientras no exista una matriz de versiones.
