# 00 – Plan: esqueleto BioHub (Ingeniería de Software 2)

## Objetivo
Dejar el monorepo de BioHub listo para que el equipo implemente historias de usuario:
estructura, infraestructura local (Docker), seguridad base (Keycloak + JWT) y una prueba de punta a punta (`/ping`).
**Sin lógica de negocio ni modelo de datos.**

## Arquitectura (decidida, no se cambia)
Arquitectura basada en servicios + API Gateway propio (Spring Boot, sin Spring Cloud Gateway) + eventos asíncronos en topología broker (RabbitMQ).

## Stack
- Backend: Java 21, Spring Boot 3.5.16, Maven (pom agregador en la raíz).
- Frontend: React 19 + Vite 8, keycloak-js 26 (web-interna).
- Infra: MySQL 8.4 (4 instancias), Redis 7.4, RabbitMQ 4 (management), Keycloak 26.8.1 (start-dev).

## Puertos
| Componente | Puerto |
|---|---|
| api-gateway | 8080 |
| servicio-cientifico | 8081 |
| servicio-equipos | 8082 |
| servicio-consulta-publica | 8083 |
| servicio-reportes | 8084 |
| web-interna / web-publica | 5173 / 5174 |
| keycloak | 8180 |
| mysql transaccional / lectura / analitica / gateway | 3307 / 3308 / 3309 / 3310 |
| redis | 6379 |
| rabbitmq / management | 5672 / 15672 |

## Tablero
| # | Tarea | Responsable | Estado |
|---|---|---|---|
| 0 | Verificar herramientas (Git, Java 21, Maven, Node 22, Docker) | Tech Lead | Hecho |
| 1 | Monorepo + pom agregador + paquetes | Tech Lead | Hecho |
| 2 | Dependencias por servicio | Tech Lead | Hecho |
| 3 | docker-compose + .env + init MySQL | Tech Lead | Hecho |
| 4 | Realm Keycloak | Tech Lead | Hecho |
| 5 | Migraciones Flyway V1 | Tech Lead | Hecho |
| 6 | Ping de punta a punta (servicios, gateway, webs) | Tech Lead | Hecho |
| 7a | .gitignore, .editorconfig | Tech Lead | Hecho |
| 7b | README y documentación | documentador-tecnico | Hecho |
| 8 | Verificación (build, compose, pings con token, E2E navegador) | Tech Lead | Hecho |
| 8b | Validación de la prueba de punta a punta | qa-tester | Hecho – Aprobado con observaciones |
| 8c | Revisión de seguridad de la configuración | ciberseguridad | Hecho – Aprobado con observaciones |
| 8d | Corrección de hallazgos QA/seguridad + re-verificación desde cero | Tech Lead | Hecho |
| 9 | Git: main, develop, ramas feature, push | Tech Lead | Hecho |
| 10 | Protección de main/develop: solo por pull request, sin aprobación obligatoria, sin force push ni borrado | Tech Lead | Hecho |
| 11 | Decisión de despliegue: webs en Vercel; backend on-premise en 2 VMs (D11) | Equipo | Reemplazada en la iteración 15 (MySQL en Aiven) |
| 12 | Reparto de ramas por persona en el README (Juanita: ubicación/consulta pública; Santiago: seguridad/acceso + infra compartida; Hernando: registro; Nicolás: reportes/equipos) | Tech Lead | Hecho |
| 13 | Preparar despliegue (Dockerfiles, compose prod, Keycloak prod) | devops | Reemplazada por la iteración 15 |

## Agentes no usados y por qué
product-owner, ux-ui-designer, dba, arquitecto-software, devops (compose lo hizo el Tech Lead; CI/CD y dockerizar servicios quedan pendientes): no hay requisitos ni modelo de datos todavía y la arquitectura ya está decidida.
Entrarán con cada historia de usuario.

## Resultados de verificación (Tech Lead)
- `mvn package`: OK, 31 tests tras las correcciones (gateway 23, cada servicio 2).
- web-interna / web-publica: `npm install`, `npm test` (2+2), `npm run build` OK; `npm audit --omit=dev`: 0 vulnerabilidades.
- `docker compose up -d`: 7/7 contenedores healthy. Realm importado.
- Pings por gateway con token de cada usuario de prueba: 200. Sin token: 401; token falso / de otro realm: 401; ruta inexistente: 404; servicio caído: 502; CORS de otro origen: 403.
- E2E navegador (Playwright): login PKCE con investigador1 → ping del Servicio Científico mostrado; token no persistido en storage.

## Pendientes (asignados a historias)
- SEC-02 reglas por rol y `denyAll` por defecto · SEC-04 usuarios RabbitMQ por servicio · SEC-05 API Keys + rate limit · SEC-11 usuario Flyway separado · SEC-12 variables por servicio en despliegue.
- CI/CD (GitHub Actions) y Dockerfiles de los servicios (devops) – no pedidos en el esqueleto; ver tarea 13 y D11.

---
# Iteraciones 13 y 14 (retiradas)

Despliegue de bases, Redis y RabbitMQ en VMs locales con una VPN de malla, y su endurecimiento. **Retirado en la iteración 15**: la VPN se eliminó del proyecto y MySQL pasa a Aiven. Se conserva de esas iteraciones:
- PR #2 fusionado a `develop` (5a8d206): eventos RabbitMQ de punta a punta, usuarios RabbitMQ por servicio; `mvn -B verify` 75 tests, 0 fallos.
- Contrato del evento `hallazgo.registrado` (registro/Hernando publica, Juanita y Nicolás consumen) en `04-contrato-api.md`.
- Cada integrante conserva sus responsabilidades: no se toca código de registro, equipos/reportes ni seguridad/acceso.

---
# Iteración 15: retiro de la VPN de malla, MySQL en Aiven, CI y cierre de la parte de Juanita

**Fecha:** 2026-11-03 · **Rama:** `feature/ubicacion-consulta-publica` (en `~/biohub`; se retiró el worktree `~/biohub-ubicacion`) · **Modo de presupuesto:** equilibrado, sin tope.

**Objetivo:** quitar la VPN de malla de todo el repo; conectar los servicios de Juanita a MySQL en Aiven (SSL) con perfil `local` (Docker) y perfil `aiven`; Flyway trazable; GitHub Actions (build web-publica, JUnit, Bruno); completar y verificar la parte de Juanita contra SRS/SAD.

**Alcance:** solo la parte de Juanita: `servicio-cientifico` módulo `ubicacion`, `servicio-consulta-publica`, `web-publica`. Otros servicios solo se tocan lo mínimo para que el de Juanita funcione, y se marca en la matriz. la VPN de malla anterior se retira de todo el repo (autorizado por Juanita).

**Fuentes de verdad:** `docs/entregas/` (SRS, SAD y C4 en PDF + texto extraído; no versionado, excluido en `.git/info/exclude`).

**Decisiones**
- D-I15-1: se mantiene la convención de Flyway por fecha (`V1__base.sql`, `VAAAAMMDD_HHMM__...`, `out-of-order`) porque el esquema `cientifico` lo comparten dos integrantes. Bases de Aiven nuevas: sin baseline.
- D-I15-2: Aiven con un servicio MySQL y una base + usuario por servicio; URL configurable para poder separar escritura y lectura en servicios distintos (riesgo ASR2/ASR4, lo documenta arquitectura).
- D-I15-3: RabbitMQ, Redis y Keycloak siguen en Docker local; en CI, como contenedores.
- D-I15-4: commits solo a nombre de Juanita, sin Co-Authored-By; sin push/merge.

| # | Tarea | Responsable | Modelo | Estado |
|---|---|---|---|---|
| 15a | Reconocimiento, documentos a `docs/entregas/`, línea base de tests | Tech Lead | opus/medium | Hecho |
| 15b | Matriz de trazabilidad `trazabilidad.md` | product-owner | sonnet/medium | Hecho (13 de Juanita: 4 Cumple, 9 Parcial; 14 ambigüedades A1–A14) |
| 15c | Verificación del código contra el SAD; impacto de Aiven/Flyway en ASR; quitar la VPN de malla de `03-arquitectura.md` | arquitecto-software | opus/high | Hecho (504a36f; 25 hallazgos: de Juanita 2 Altos ARQ-05, ARQ-06 y 11 Medios/Bajos; ARQ-12 y ARQ-15 corregidos en 8bdac84) |
| 15d | Quitar la VPN de malla (resto del repo); perfiles local/aiven con SSL; `.env.example` | devops | sonnet/medium | Hecho (fe1bb43, 52ac256; perfil aiven probado con SSL contra MySQL local, falta Aiven real) |
| 15e | Migraciones compatibles con Aiven; convención en README de cada servicio | dba | sonnet/high | Hecho (6d64b9e, 62f304c; 6 migraciones compatibles, sin baseline) |
| 15f | GitHub Actions + colecciones Bruno | devops + qa-tester | sonnet/medium | Hecho (Bruno 44/44 requests, 77/77 tests en local; actionlint OK; job en GitHub sin ejecutar aún) |
| 15g | Completar la parte de Juanita (SEC-J-01..03 y faltantes) | backend-senior / frontend-senior | sonnet/high · sonnet/medium | Hecho (d92c920, 31fd86b, 98e0021, 0e959d2; 2 ciclos de corrección: ARQ-26 Alto + SEC-J-10/11/14/15/16, BUG-J2/J3) |
| 15h | Verificación final: build, tests, k6, auditoría, ASR en código | qa-tester · ciberseguridad · arquitecto | sonnet/medium · opus/high · opus/high | Hecho (QA APROBADO; seguridad APROBADO CON OBSERVACIONES, 0 Crít/Alt; ASR1 y ASR4 Parciales por D42; cientifico 241/0/2 omitidos opt-in, consulta-publica 190/0) |

## Modelos y consumo
| Fase | USD acumulado | Agente que más consumió | Cambios de modelo (motivo) |
|---|---:|---|---|
| 15a Reconocimiento | 0.80 | sesión principal (opus) | — |
| 15b–15e Diseño, retiro de la VPN, Aiven, Flyway | 9.36 | arquitecto-software (opus, 3.45) | — (todos según matriz equilibrado) |
| 15f–15g (sesión 2026-11-03, sin medir aparte) | — | — | — |
| 15h verificación + 2 ciclos (sesión 2026-11-04) | 10.89 en esta sesión (≥ 20.25 acumulado; 15f–15g no se midió) | ciberseguridad (opus, 4.08 en 2 tareas) | backend ciclo 2 sonnet/**medium** (matriz: high): solo reglas ArchUnit ya diseñadas por seguridad |
| 15h ciclo 1 re-verificación | (incluido en la fila anterior) | — | ciberseguridad opus/**medium** (matriz: high): solo re-verifica un diff acotado de su propia auditoría |
| 15h CI en GitHub + arreglo WebKit/Bruno (sesión 2026-11-04) | 13.80 en esta sesión (≥ 23.16 acumulado) | ciberseguridad (opus, 4.08) | — |
| Cierre 2026-11-04: PR #3–#5 a develop, Aiven escritura y lectura reales, credenciales al equipo | 20.02 en esta sesión (≥ 29.38 acumulado) | sesión principal (opus, 10.77) | devops sonnet/medium se cortó por límite de uso; el Tech Lead terminó la limpieza y el aprovisionamiento |

## Pendientes tras la 15h
- Corridas k6 que exige D42: degradación activa, 1 vs 2 réplicas en saturación (ARQ-30, ARQ-31) y par ASR4 `CIENTIFICA=0/1` (ARQ-32).
- SEC-J-12, SEC-J-13 (guarda de coordenadas en el publicador, sube de prioridad), SEC-J-04/05/07/08(3)/09, SEC-04/SEC-D-03 (un usuario AMQP por servicio, Santiago).
- ARQ-05 (segundo servicio Aiven sin evidencia), ARQ-07 (Hernando), ARQ-28/29/33, Aiven real sin probar. CI en GitHub: verde en run 41891195794 (740bb37), los 4 jobs.
- Condición D44: nada de registro (Hernando) entra sin pasar las reglas ArchUnit actuales.
- Hecho por la usuaria: DLQ purgada, `.env` actualizado, contraseñas de `avnadmin` rotadas, credenciales y llave de cifrado en gestor de contraseñas, zips enviados por privado.
- Usuaria: agregar en Aiven (Allowed IP addresses) las IP que envíen Hernando, Nicolás y Santiago.
- Corridas k6 (D42) pospuestas por decisión de la usuaria hasta que los servicios de todos estén completos; repetir todo sobre `develop` antes de la entrega.
- Cuando `develop` esté estable: PR `develop` → `main`.

# Iteración 16: pendientes propios de Juanita (seguridad y arquitectura)

**Fecha:** 2026-11-04 · **Rama:** `feature/ubicacion-consulta-publica` · **Modo de presupuesto:** equilibrado, sin tope.

**Alcance:** solo hallazgos cuyo responsable es Juanita. Fuera de alcance: SEC-J-05, SEC-J-07 (web-interna), SEC-J-09, SEC-04/SEC-D-03 (Santiago); ARQ-07 (Hernando); k6 de D42 (pospuesto por la usuaria); el proxy de teselas de SEC-J-04 (Santiago). Decisiones nuevas: D53 (ARQ-29) y D54 (SEC-J-13) en `03-arquitectura.md`.

| # | Tarea | Responsable | Modelo | Estado |
|---|---|---|---|---|
| 16a | ARQ-33: comentarios de `application-aiven.yml` (cientifico y consulta-publica) | Tech Lead | opus/medium | Hecho |
| 16b | SEC-J-13, SEC-J-12, SEC-J-08.3, ARQ-28, ARQ-29 en `servicio-cientifico` | backend-senior | sonnet/high | Hecho (786f4e1, 57ab7ad, 1e449ac, 25d2175, aeb67d8; queda ARQ-28b aceptado) |
| 16c | SEC-J-04 (zoom con un punto y `referrerPolicy`) en `web-interna/src/ubicacion` | frontend-senior | sonnet/medium | Hecho (47b5cc9) |
| 16d | Verificación: tests y re-auditoría del diff | qa-tester · ciberseguridad | sonnet/medium · opus/medium | Hecho: QA APROBADO (27a6dd9, BUG-J4 tests herméticos frente al `.env`; cientifico 264/0/2 omitidos, consulta-publica 194/0, web-interna 46/46, web-publica 32/32). Seguridad APROBADO CON OBSERVACIONES (d249733): SEC-J-12 Cerrado; SEC-J-04, 08 y 13 Parciales; nuevos SEC-J-17 y SEC-J-18 (Bajos) |

**Pendientes tras la iteración 16 (de Juanita):** SEC-J-17 (guarda de coordenadas eludible; condición previa para publicar `hallazgo.*` reales), SEC-J-18 (auditoría falla abierta), SEC-J-I14 (verificar teselas OSM con `no-referrer` en el navegador), ARQ-28b. Usuaria: borrar las VMs retiradas `biohub-backend` y `biohub-datos` (el borrado desde la sesión fue bloqueado por permisos).

**Modelos y consumo (iteración 16):**
| Fase | USD | Agente que más consumió | Cambios de modelo (motivo) |
|---|---:|---|---|
| 16a–16d (sesión 2026-11-04 tarde) | 4.91 en esta sesión (≥ 34.29 acumulado) | backend-senior (sonnet, 1.79) | ciberseguridad opus/**medium** (matriz: high): re-verificación de un diff acotado, como en 15h |

---
# Iteración 16: seguridad y acceso (parte de Santiago)

**Fecha:** 2026-11-05 · **Rama:** `feature/seguridad-acceso`.

**Alcance:** `api-gateway`, Keycloak y roles, infraestructura compartida. Las pantallas de `web-interna` van al final, cuando existan los endpoints de negocio. Guía para el equipo: `11-seguridad-y-acceso.md`. Decisiones: D45 a D51.

| # | Tarea | Estado |
|---|---|---|
| 16a | Reglas por rol y `denyAll` en el gateway (SEC-02) | Hecho |
| 16b | Realm: bloqueo a 3 intentos, cliente `biohub-gateway`, usuario `sinrol1` | Hecho |
| 16c | Migración, consumidores y API Keys solo con hash (HU-12) | Hecho |
| 16d | Límite en Redis con respaldo en memoria (RNF11, ASR8) | Hecho |
| 16e | Filtro de API Key y límite por IP con falla cerrada (SEC-05) | Hecho |
| 16f | API externa `/api/v1/externa/fichas/**` (ASR9) | Hecho |
| 16g | Usuarios contra Keycloak y revocación inmediata (HU-10, RF19) | Hecho |
| 16h | Usuarios RabbitMQ por servicio (SEC-04) y `maxmemory` en Redis | Hecho; falta que cada servicio migre su `.env` |
| 16i | Gateway en CI y carpeta Bruno `gateway/` | Hecho; el workflow no se ha ejecutado en GitHub |
| 16j | Router, menú por rol, montaje de `src/ubicacion/`, pantallas de API Keys y usuarios | Pendiente, al final |
| 16k | Circuit breaker en el gateway (ARQ-20, D52) | Hecho |
| 16l | Acciones de GitHub en versiones con Node 24 (`checkout@v5`, `setup-java@v5`, `setup-node@v5`, `upload-artifact@v6`) | Hecho; falta verlo en una corrida de GitHub |

**Verificación (2026-11-05):**
- `mvn -pl api-gateway test`: 156 pruebas, 0 fallos, 0 omitidas (168 tras 16k) (Testcontainers con MySQL con reglas de Aiven, Redis y Keycloak con el realm real).
- Colección Bruno completa en un entorno desechable, con el JDK y Node locales: 73 de 73 peticiones, con `servicio-cientifico` conectado como `cientifico_mq` y `servicio-consulta-publica` como `consulta_mq`.
- Pruebas de mutación manuales: con multipart activado y con `failureFactor: 5`, las pruebas correspondientes fallan.
- Sin ejecutar: la migración en Aiven real y los servicios de Nicolás con su usuario `reportes_mq`.

**Hallazgos durante la verificación:** el proxy dejaba vacíos los adjuntos multipart (D51) y SEC-D-03 estaba equivocado sobre `biohub.eventos.dlx` (D50).

**CI tras el PR #6 (2026-11-08):** `develop` quedó en rojo por dos pruebas inestables de otra parte, sin relación con el gateway: `ValidadorDatosHallazgoTest.contenidoEstrictoFechaYCaracteresDeControl` (consulta-publica) calcula "mañana" con la zona del servidor y el validador con la de Bogotá, así que falla entre las 00:00 y las 05:00 UTC; y `RecifradoServiceTest.lasFilasIlegiblesQuedanPendientesYSeCuentanComoFallidas` (científico) falló una vez y pasó en las corridas siguientes. Dueña: Juanita.

---
# Iteración 17: integrar el PR #7 y aplicar `11-seguridad-y-acceso.md` (parte de Juanita)

**Fecha:** 2026-11-08 · **Rama:** `feature/ubicacion-consulta-publica` · **Modo de presupuesto:** equilibrado, sin tope.

**Alcance:** Aiven (lista de IP y conectividad), merge de `develop` con el PR #7, secret del gateway y reimportación de Keycloak, `denyAll` + `@PreAuthorize` + pruebas 401/403 en `ubicacion` (servicio-cientifico) y `servicio-consulta-publica`, CI rojo (`ValidadorDatosHallazgoTest` por zona horaria, `RecifradoServiceTest` inestable). Reglas: gateway solo con perfil `local`, nada de push sin confirmar, commits sin Co-Authored-By.

| # | Tarea | Responsable | Modelo | Estado |
|---|---|---|---|---|
| 17a | IP en Aiven escritura (propia y de compañeros); conectividad escritura y lectura (TLS `VERIFY_IDENTITY`) | Usuaria · Tech Lead | opus/medium | Hecho |
| 17b | Merge de `develop` (da4152a); D45/D46 de ubicación renumeradas a D53/D54 (7e4a52f) | Tech Lead | opus/medium | Hecho |
| 17c | `KEYCLOAK_GATEWAY_CLIENT_SECRET` en `.env` raíz; reimportar Keycloak; verificar `sinrol1`, bloqueo a 3 intentos y client del gateway | Tech Lead | opus/medium | Hecho |
| 17d | `denyAll`, `@PreAuthorize` en cada endpoint y pruebas 401/403/2xx (cientifico-ubicacion y consulta-publica) | backend-senior | sonnet/high | Hecho (d0be1be, a0e0d32; D55) |
| 17e | CI: zona horaria de `ValidadorDatosHallazgoTest` y causa raíz de `RecifradoServiceTest` | backend-senior | opus/high | Hecho (b4053c3, d9a9031, y el resto de pruebas con 0xFF) |
| 17f | E2E por el gateway (perfil local): 2xx / 403 con `sinrol1` / 401 sin token; API Keys y límites | qa-tester | sonnet/medium | Hecho: APROBADO (f695c8a). cientifico 322/0, consulta-publica 239/0, gateway 168/0 en TZ UTC y Bogotá; E2E por gateway local 50/50 |
| 17g | Auditoría: denyAll, `@PreAuthorize`, secretos en la rama, arreglo del recifrado | ciberseguridad | opus/high | Hecho: APROBADO CON OBSERVACIONES (45ce862). D55 aceptada con condiciones; SEC-J-19/20/21 Bajos; sin secretos en árbol ni historial |

**Modelos y consumo (iteración 17):**
| Fase | USD | Agente que más consumió | Cambios de modelo (motivo) |
|---|---:|---|---|
| 17a–17c (Aiven, merge, Keycloak) | 2.27 en esta sesión | sesión principal (opus) | — |
| 17d–17g (denyAll, CI, QA, seguridad) | 8.95 en esta sesión | sesión principal (opus, 3.98); backend-senior 17e (opus, 2.12) | backend-senior 17e en **opus**/high (matriz: sonnet/high): causa raíz de una prueba inestable que toca cifrado |

**Pendientes tras la iteración 17 (de Juanita):**
- SEC-J-19 (endurecer `ArquitecturaSeguridadTest`) y SEC-J-20 (403 problem+json fuera de `ubicacion`): condición de D55 antes de que `registro` integre controladores. OBS-J17-1 (403/401 vacío en `/api/publica/eventos/recibidos`) se resuelve con el mismo handler.
- Confirmar con el grupo las 4 rutas fuera de la matriz §2: `GET /api/cientifico/ping`, `POST /api/cientifico/eventos/prueba`, `GET /api/publica/ping`, `GET /api/publica/eventos/recibidos`; y D55.
- §3 de `11`: confirmar que `/api/publica/fichas` y `/fichas/{id}` no cambian; si 120/min por IP alcanza; aviso de 429 con `Retry-After` en `web-publica`.
- SEC-04: migrar `servicio-cientifico/.env` y `servicio-consulta-publica/.env` a `cientifico_mq`/`consulta_mq`.
- Aiven: escritura y lectura ya tienen las IP del equipo con /32 (según la usuaria, 2026-11-08); falta acordar con el grupo quitar `0.0.0.0/0` y `::/0`.
- Propuesta: una entrada del CI con `TZ: America/Bogota`.

