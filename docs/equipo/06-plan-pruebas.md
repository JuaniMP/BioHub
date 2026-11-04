# 06 – Plan de pruebas del esqueleto

Alcance: validar de forma independiente el esqueleto (infraestructura, seguridad base y el `ping` de punta a punta). No hay historias de usuario ni lógica de negocio, así que no hay matriz historia→caso; la trazabilidad es contra `04-contrato-api.md` y `03-arquitectura.md` (columna "Origen").

Convenciones: `G` = `http://localhost:8080`. `TOKEN(u)` = token de `POST http://localhost:8180/realms/biohub/protocol/openid-connect/token` con `grant_type=password`, `client_id=biohub-pruebas`, usuario `u` y la contraseña de `KEYCLOAK_TEST_USERS_PASSWORD` del `.env` (no se documenta). Usuarios: investigador1, auditor1, cientifico1, analista1, admin1. Tipo: U = unitaria/componente, I = integración, S = seguridad, F = infraestructura, E = E2E navegador. Todas se ejecutan de forma manual/por consola (curl, mvn, npm, docker exec, Playwright); no se añadieron tests automatizados al repo en esta pasada (ver nota al final).

## 1. Unitarias / componentes
| ID | Descripción | Pasos | Resultado esperado | Origen |
|---|---|---|---|---|
| UC-01 | `ProxyServiceTest` (gateway) | `mvn -B package` | 4 tests OK: destino por prefijo, reenvío de ruta+query+Authorization, propagación de error del servicio, 404 sin servicio | Contrato: enrutamiento |
| UC-02 | `GatewaySecurityTest` | idem | 5 tests OK: sin token 401 y no se reenvía, con token se reenvía, pública sin token, ping, CORS | Contrato: errores |
| UC-03 | `PingControllerTest` servicio-cientifico | idem | 2 OK (con JWT, sin token) | Contrato: ping |
| UC-04 | `PingControllerTest` servicio-equipos | idem | 2 OK | idem |
| UC-05 | `PingControllerTest` servicio-consulta-publica | idem | 2 OK | idem |
| UC-06 | `PingControllerTest` servicio-reportes | idem | 2 OK | idem |
| UC-07 | `npm test` web-interna | `cd web-interna && npm test` | 2 tests OK | Arquitectura: web |
| UC-08 | `npm test` web-publica | idem | 2 tests OK | idem |
| UC-09 | `npm run build` web-interna | `npm run build` | Build sin errores | – |
| UC-10 | `npm run build` web-publica | idem | Build sin errores | – |
| UC-11 | `npm audit --omit=dev` en ambas webs | ejecutar | 0 vulnerabilidades | Plan 00 |

## 2. Integración vía gateway
| ID | Descripción | Pasos | Resultado esperado |
|---|---|---|---|
| IG-01 | Matriz 5x5: 5 pings con token de cada usuario | `GET G/api/{gateway,cientifico,equipos,publica,reportes}/ping` con `Authorization: Bearer TOKEN(u)` | 25 respuestas 200; `usuario` = usuario del token; `roles` = su rol de negocio; `servicio` correcto |
| IG-02 | Ping público anónimo | `GET G/api/publica/ping` sin token | 200, `usuario":"anonimo"`, `roles":[]` |
| IG-03 | `roles` solo de negocio y `fecha` ISO-8601 | Revisar JSON de IG-01 | Sin `offline_access`, `uma_authorization`, `default-roles-*` |
| IG-04 | Query string con caracteres especiales | `GET G/api/cientifico/ping?a=1&b=%20x%26y&c=%C3%B1` y `?` vacío | 200, el proxy no rompe |
| IG-05 | POST con cuerpo JSON (UTF-8, comillas) | `POST G/api/cientifico/hallazgos` y `/ping` con token | Llega al servicio: 404 (ruta) / 405 (ping) del servicio, no 5xx del gateway |
| IG-06 | PUT/DELETE, POST sin cuerpo, cuerpo de 5 MB y `Transfer-Encoding: chunked` | curl con token a `/api/equipos/algo`, `/api/reportes/algo` | Respuesta del servicio (404), sin 5xx |
| IG-07 | Prefijo inexistente con token | `GET G/api/inexistente/ping`, `G/api/` | 404 |
| IG-08 | Códigos del servicio se propagan | `POST /api/cientifico/ping` (405), `GET /api/cientifico/noexiste` (404) | Mismo código y cuerpo JSON del servicio |
| IG-09 | Health público | `GET G/actuator/health` y `:8081..8084/actuator/health` | 200 `{"status":"UP"}` |
| IG-10 | Servicio destino caído → 502 `application/problem+json` | Detener un servicio y llamar | 502 sin detalles internos |
| IG-11 | Variantes de ruta | `/api/cientifico/ping/`, `/api/CIENTIFICO/ping` | 404 (el prefijo distingue mayúsculas) |
| IG-12 | `X-Forwarded-For` falsificado por el cliente | Enviar `X-Forwarded-For: 6.6.6.6` | 200; el gateway lo reescribe (código `ProxyService.copiarCabeceras`) |

## 3. Seguridad
| ID | Descripción | Pasos | Resultado esperado |
|---|---|---|---|
| SE-01 | Sin token en rutas protegidas | `GET /api/{gateway,cientifico,equipos,reportes}/ping` sin cabecera | 401 |
| SE-02 | Firma alterada (último carácter) | Token con firma modificada | 401 |
| SE-03 | Payload alterado | Cambiar un carácter del payload | 401 |
| SE-04 | `alg: none` | JWT sin firma | 401 |
| SE-05 | Token basura | `Bearer abc` | 401 |
| SE-06 | Esquema incorrecto / vacío | `Basic ...`, `Bearer ` | 401 |
| SE-07 | Token vencido | Esperar `exp` + 60 s (tolerancia de reloj) y reutilizar | 401 en gateway y en servicios directos |
| SE-08 | Token de otro realm | Token de `master` (`admin-cli`, credenciales `KEYCLOAK_ADMIN_*`) | 401 en las 5 rutas |
| SE-09 | Token del realm `biohub` sin `aud=biohub-api` | Token de `admin-cli` en realm `biohub` | 401 (gateway y servicio) |
| SE-10 | Ruta pública con token inválido | `GET /api/publica/ping` con `Bearer abc` y firma mala | 401 (no se degrada a anónimo) |
| SE-11 | Métodos no GET a `/api/publica` sin token | POST, PUT, PATCH, DELETE | 401 |
| SE-12 | POST a `/api/publica/ping` con token | POST | 405 del servicio |
| SE-13 | HEAD a `/api/publica/ping` sin token | HEAD | 401 (solo GET es público) |
| SE-14 | CORS desde origen permitido | `Origin: http://localhost:5173` y `:5174` | 200 + `Access-Control-Allow-Origin` igual al origen |
| SE-15 | CORS desde origen no permitido | `evil.example`, `null`, `127.0.0.1:5173` | 403 |
| SE-16 | Preflight | OPTIONS permitido / origen malo / cabecera `x-evil` | 200 con cabeceras / 403 / 403 |
| SE-17 | Acceso directo a 8081, 8082, 8084 | Sin token, token con firma mala | 401 |
| SE-18 | Acceso directo a 8083 | `/api/publica/ping` sin token (200 anónimo), ruta no pública (401), firma mala (401) | Según lo indicado |
| SE-19 | Token master directo a servicio | `8081` con token master | 401 |
| SE-20 | Recorrido de directorios codificado (`%2e%2e`) | `GET /api/cientifico/%2e%2e/%2e%2e/actuator/health` con token; y `/api/publica/%2e%2e/%2e%2e/actuator/health` sin token | El gateway debe rechazar (400/404); no debe salir del prefijo autorizado |
| SE-21 | Entradas malformadas | `/%zz`, `%00`, URL de 9000 caracteres | 4xx, nunca 5xx |
| SE-22 | Caracteres especiales válidos | `%C3%B1and%C3%BA`, `;jsessionid=1`, `//api/...`, `/api//...` | Sin 5xx; 404/401 |
| SE-23 | Actuator sensible cerrado | `GET /actuator/env` sin token y con token | 401 / 404 |
| SE-24 | Password grant desactivado en `web-interna` | grant password con `client_id=web-interna` | 400 `unauthorized_client` |
| SE-25 | Credenciales malas y client_credentials | Contraseña errónea; `client_credentials` con `biohub-pruebas` | 400 `invalid_grant` / `unauthorized_client` |
| SE-26 | Secretos | `.env` con permisos 600 e ignorado en `.gitignore` | Sí |

## 4. Infraestructura
| ID | Descripción | Pasos | Resultado esperado |
|---|---|---|---|
| IN-01 | 7 contenedores healthy | `docker ps` | mysql x4, redis, rabbitmq, keycloak: `healthy` |
| IN-02 | Flyway V1 en los 5 esquemas | `select * from <esquema>.flyway_schema_history` | versión 1 `base`, `success=1` en cientifico, equipos, consulta_publica, reportes, gateway |
| IN-03 | Esquemas esperados por instancia | `show databases` como root | cientifico+equipos / consulta_publica / reportes / gateway |
| IN-04 | `cientifico_app` no accede a `equipos` | `select` sobre `equipos.flyway_schema_history` | ERROR 1142 denied |
| IN-05 | `equipos_app` no accede a `cientifico` | idem a la inversa | ERROR 1142 denied |
| IN-06 | Privilegios mínimos | `show grants` | Solo `ALL` sobre su propio esquema |
| IN-07 | Redis exige contraseña | `PING` sin auth y con contraseña errónea | `NOAUTH` / `WRONGPASS` |
| IN-08 | Redis con contraseña correcta | `PING` con `REDIS_PASSWORD` | `PONG` |
| IN-09 | RabbitMQ management con credenciales válidas | `GET :15672/api/overview` | 200 |
| IN-10 | RabbitMQ sin o con credenciales malas | idem | 401 |
| IN-11 | Exchange `biohub.eventos` | `GET /api/exchanges/%2F` | existe, tipo `topic`, durable |
| IN-12 | Puertos solo en loopback | `ss -ltn` | 3307-3310, 6379, 5672, 15672, 8180 en 127.0.0.1 |
| IN-13 | servicio-consulta-publica usa Redis realmente | Requiere endpoint con caché | No hay endpoint aún |

## 5. E2E navegador (Playwright)
| ID | Descripción | Resultado esperado |
|---|---|---|
| E2E-01 | `http://localhost:5174` muestra "Conectado a servicio-consulta-publica" sin login | Texto visible |
| E2E-02 | `http://localhost:5173` redirige a Keycloak con PKCE | `code_challenge_method=S256`, `client_id=web-interna` |
| E2E-03 | Login con investigador1 → ping del Servicio Científico | servicio-cientifico, investigador1, investigador_campo |
| E2E-04 | Token no persistido en localStorage/sessionStorage | Sin `eyJ` |
| E2E-05 | Logout | Vuelve a la pantalla de login de Keycloak |
| E2E-06 | Sin errores de consola (CORS incluido) | Ninguno |
| E2E-07 | Variante con admin1 (E2E-02..06) | usuario admin1, rol administrador |

Los scripts de Playwright viven fuera del repo (scratchpad de la sesión: `e2e.mjs` y `e2e-usuario.mjs`, este último parametrizado con `USR` y `PASS`).

## Pendiente / recomendaciones para el tech-lead
- Convertir SE-01..SE-13, SE-20 y IG-01..IG-08 en pruebas automáticas de integración (p. ej. `@SpringBootTest` + WireMock para el destino) cuando haya historias; hoy solo existen las pruebas MockMvc de la sección 1.
- Cuando haya API Key + rate limiting: casos 429, `Retry-After`, key inválida/revocada, límite por IP.
- Cuando existan endpoints con roles: matriz rol x endpoint (403 para roles sin permiso).

---
# Iteración despliegue + RabbitMQ (tarea 13e)

## 6. Trazabilidad: criterios del evento -> casos
| Criterio (contrato 04, "Eventos") | Casos | Tipo |
|---|---|---|
| POST prueba: 202 con sobre tras publisher confirm | EV-01, EV-02, EV-03 | integración (MockMvc) / IT Testcontainers |
| POST prueba: 400 (mensaje >200, JSON roto) y borde exacto 200 | EV-04, EV-05, EV-06 | integración |
| POST prueba: 401 sin token / 403 sin rol `administrador` | EV-07, EV-08 | integración |
| POST prueba: 503 con broker caído, sin detalles internos | EV-09, EV-10 | integración / IT |
| Publicación: JSON, persistente, routing key = tipo, sin `__TypeId__`, `message_id` = id | EV-11, EV-12 | unitaria / IT |
| GET recibidos: 401 / 403 / 200 (consulta-publica y reportes) | EV-13..EV-15 | integración |
| Límite de 20 eventos, orden del más reciente al más antiguo | EV-16 | unitaria |
| DLQ: JSON roto | EV-17 | unitaria + IT |
| DLQ: tipo desconocido | EV-18 | unitaria + IT |
| DLQ: routing key distinta del tipo | EV-19 | unitaria + **IT nuevo (QA)** |
| DLQ: cuerpo > 64 KB / vacío | EV-20 | unitaria + **IT nuevo (QA)** |
| Cabecera `__TypeId__` de otra clase se ignora | EV-21 | IT (el evento válido lleva la cabecera) |
| Sobre incompleto, `datos` de prueba inválidos | EV-22 | unitaria |
| Permisos por usuario RabbitMQ (SEC-04) | EV-23, EV-24 | integración opt-in (QA) |

## 7. Casos de eventos
| ID | Descripción | Pasos / datos | Esperado | Dónde |
|---|---|---|---|---|
| EV-01 | Publicar con mensaje | POST `{"mensaje":"hola"}` admin | 202, sobre con `tipo=prueba.ping`, `origen=servicio-cientifico`, `usuario` | EventosPruebaControllerTest |
| EV-02 | Sin cuerpo | POST sin body | 202, `mensaje="ping"` | idem |
| EV-03 | Publicación real | publicador contra RabbitMQ real | mensaje en cola | PublicadorEventosIntegracionTest |
| EV-04 | Mensaje de 201 caracteres | POST | 400 problem+json | idem EV-01 |
| EV-05 | Mensaje de exactamente 200 | POST | 202 | idem |
| EV-06 | JSON roto | POST `{roto` | 400 | idem |
| EV-07 | Sin token | POST | 401 | idem |
| EV-08 | Rol distinto de administrador | POST | 403 | idem |
| EV-09 | Broker caído (mock) | POST | 503 sin texto de excepción | idem |
| EV-10 | Sin broker real | publicar a puerto cerrado | `EventoNoPublicadoException` | PublicadorEventosIntegracionTest |
| EV-11 | Routing key y persistencia | publicar y leer | rk = tipo, delivery_mode 2, message_id = id | PublicadorEventosTest |
| EV-12 | Sin `__TypeId__`, `application/json` | leer mensaje real | cabecera ausente | PublicadorEventosIntegracionTest |
| EV-13 | GET recibidos sin token | GET | 401 | EventosRecibidosControllerTest (x2 servicios) |
| EV-14 | GET recibidos sin rol | GET | 403 | idem |
| EV-15 | GET recibidos admin | GET | 200 `{servicio,total,eventos[]}` | idem |
| EV-16 | 25 eventos | consumir 25 | 20, el primero es el último recibido | ConsumidorEventosTest (x2) |
| EV-17..EV-22 | Rechazos del consumidor | ver matriz | `EventoInvalidoException` y, en broker real, mensaje en `<cola>.dlq` y cola principal vacía | ConsumidorEventosTest, ConsumidorEventosIntegracionTest, **ConsumidorEventosRechazosIntegracionTest** |
| EV-23 | `reportes_mq` declara su cola, consume y manda a DLQ con las definitions reales | `QA_RMQ_PORT=<p> QA_RMQ_USER=reportes_mq QA_RMQ_PASS=... mvn -pl servicio-reportes test -Dtest=ConsumidorBrokerDesplegadoTest` | pasa | opt-in |
| EV-24 | `cientifico_mq` publica con confirmación y NO puede leer colas | idem con `PublicadorBrokerDesplegadoTest` en servicio-cientifico | pasa; el broker responde ACCESS_REFUSED al leer | opt-in |

Los tests opt-in se omiten si `QA_RMQ_PORT` no está definido (no afectan a `mvn verify`).

## 8 y 9. Artefactos de despliegue en VMs y procedimiento E2E contra las VMs

Sección retirada en la iteración 15 (Tailscale eliminado; ver 09-despliegue.md).

---

---
---
# Parte de Juanita: ubicación y consulta pública

Fecha: 2026-10-26. Autor: QA. Rama `feature/ubicacion-consulta-publica`. Estado: **plan escrito antes de terminar la construcción**; no se ha ejecutado nada. Todo resultado se registrará en `07-reporte-qa.md` solo después de correrlo.

Fuentes (en orden de precedencia): `01-requisitos.md` §9 (D14, D15, D16, D17, P5) > `03-arquitectura.md` D14-D32 y §J1-J14 > `04-contrato-api.md` (fuente de verdad de campos, parámetros y códigos) > `05-modelo-datos.md` > `02-diseno-ux.md` > criterios de `01` §3 y SRS (RNF1, RNF5, RNF7, RNF9, RNF13).

## P0. Criterios de `01` §3 que quedan anulados o cambiados (no se prueban como están escritos)
| Criterio original | Cambio | Qué se prueba en su lugar |
|---|---|---|
| HU-UBI-01 "Especie no en peligro" (coordenadas en columnas en claro) | D15: se cifra todo; no existen columnas en claro | CF-08, DB-01..DB-03: una especie `NO_AMENAZADA` también queda solo cifrada |
| HU-06.3 "Ficha de especie no amenazada" con coordenadas y `ubicacionProtegida:false` | D14 | PB-12: ninguna ficha lleva coordenadas y `ubicacionProtegida` es siempre `true` |
| HU-13.1 "Hallazgo no amenazado" visible para `investigador1` | D21 / D32 P-J1 | HB-07: solo `cientifico_alto_rango` ve coordenadas, de cualquier especie |
| HU-UBI-02 variables `UBICACION_CLAVES_<v>` | D20 | CF-12..CF-18 con `UBICACION_CLAVES` + `UBICACION_CLAVE_ACTIVA` |
| HU-UBI-03 "estado ausente" normalizado a `EN_PELIGRO` | D32 P-J2 | US-08 (rechazo) y CP-10/CP-11 (consumidor) |
| HU-06.2 `size` máximo 50, `q` 2-100; paginación (`page` base 0) | `04` §1 | PB-05..PB-11 |

## P1. Reglas de ejecución y de datos (obligatorias)
1. **Ninguna prueba deja datos.** Todas las pruebas automatizadas usan Testcontainers (`mysql:8.4`, `rabbitmq:4-management`, `redis:7.4-alpine`) o mocks. Prohibido apuntar a `biohub-datos`/`biohub-backend`, a sus colas, a su Redis, y publicar `hallazgo.*` en el broker desplegado (D30).
2. **Entorno de carga:** Compose efímero con `-p biohub-carga`, red propia y puertos distintos de los de desarrollo, destruido con `docker compose -p biohub-carga down -v` al terminar (también si la prueba falla: `trap`/`finally`).
3. **Única excepción: E2E de humo contra las VMs**, solo si la dueña la autoriza por escrito y solo con estos casos: DX-01 a DX-04. Reglas:
   - Todo identificador creado lleva el prefijo `qa_tmp_` (nombre científico `qa_tmp_Especie_<aleatorio>`, usuario no se crea). Se registra cada id creado en un archivo temporal para limpiar.
   - **Paso de limpieza obligatorio** en `finally`: borrar solo lo creado y **verificar estado final idéntico al inicial** (conteos antes/después de `ficha_publica`, `ubicacion_hallazgo`, mensajes en colas y DLQ de `consulta-publica.eventos` y `reportes.eventos`, claves `biohub:publica:*` en Redis). Si el conteo final difiere, la prueba se declara **FALLIDA** y se escala a la dueña antes de seguir.
   - Como `servicio-cientifico` no tiene (por diseño) un endpoint de borrado de hallazgos (`eliminado_en` siempre NULL) y consulta-publica no tiene `DELETE`, **no se puede limpiar por API**. Por tanto el humo en las VMs queda limitado a **lecturas** y a casos negativos que no persisten nada (DX-01..DX-04). Está **prohibido** publicar un `hallazgo.registrado` válido en las VMs. Si se necesitara, la limpieza sería con SQL por `hallazgo_id` `qa_tmp_` realizada por la dueña (decisión pendiente, ver RQ-13).
   - Credenciales leídas de del `.env` del servicio sin imprimir; ni tokens ni contraseñas en scripts ni en el reporte.
4. **Datos de prueba:** coordenadas centinela únicas y reconocibles (para poder buscarlas): `4.6097, -74.0817` y `5.1234567, -73.1234567`. Si alguna aparece en una respuesta pública, log, caché o evento, el caso falla. Se generan fixtures con UUID aleatorio por test.
5. **Tokens simulados:** `@WebMvcTest` con `jwt()`; para integración HTTP real, un emisor de JWT local (JWKS en memoria) o `MockOAuth2Server`, con `iss`, `aud=biohub-api`, `exp`. Los 5 roles: `investigador_campo`, `auditor_cientifico`, `cientifico_alto_rango`, `analista_ambiental`, `administrador` (usuarios `investigador1`, `auditor1`, `cientifico1`, `analista1`, `admin1`).
6. **Comandos previstos** (se confirmarán al ejecutar): `mvn -B -pl servicio-cientifico,servicio-consulta-publica verify` (unitarias + integración con Testcontainers; requiere Docker), `mvn -pl servicio-consulta-publica test -Dgroups=rendimiento`, `cd web-publica && npm test`, `cd web-interna && npm test`, `npx playwright test` (e2e), `k6 run pruebas-carga/<script>.js`.

Niveles usados en las tablas: **U** unitario, **I** integración (Testcontainers/MockMvc/WebMvcTest), **E2E** navegador, **CAR** carga/rendimiento, **SEG** seguridad, **USA** usabilidad/manual con personas. Casos marcados **[BLOQ]** dependen de algo externo (P8).

## P2. Matriz de trazabilidad (criterio de aceptación -> casos -> nivel)
| Historia / criterio | Casos | Niveles |
|---|---|---|
| HU-UBI-01 cifrado AES-256-GCM, solo columna cifrada | CF-01, CF-02, DB-01..DB-04 | U, I, SEG |
| HU-UBI-01 nonce distinto por cifrado | CF-03, CF-04 | U, I |
| HU-UBI-01 AAD = id del hallazgo | CF-05, CF-06 | U, I |
| HU-UBI-01 texto alterado | CF-07, MP-12, HB-17 | U, I |
| HU-UBI-01 todas las especies cifradas (D15) | CF-08, DB-02 | I |
| HU-UBI-01 coordenadas inválidas | CF-09, US-06 | U |
| HU-UBI-01 sin fuga en logs | CF-10, LG-01..LG-03 | I, SEG |
| HU-UBI-02 llaves fuera de BD y repo | CF-11, DB-05, SG-14 | I, SEG |
| HU-UBI-02 clave_version por registro | CF-02, CF-13 | I |
| HU-UBI-02 rotación sin pérdida | CF-14, RC-01 | I |
| HU-UBI-02 llave retirada | CF-15, MP-11, HB-16 | U, I |
| HU-UBI-02 sin llave activa / llave inválida / id inválido o repetido | CF-12, CF-16..CF-18 | U, I |
| HU-UBI-02 re-cifrado masivo (Should) | RC-01..RC-06 | I, SEG |
| HU-UBI-01/02 colisión de nonce, concurrencia, llave retirada sin arrancar | CF-19..CF-21, DB-08 | I |
| HU-UBI-03 alta, duplicado, versión antigua | US-01..US-05 | I |
| HU-UBI-03 datos incompletos / inválidos | US-06..US-09 | U |
| HU-UBI-03 `MANDATORY` sin transacción, rollback, fechas | US-10, US-11, US-15 | I, U |
| HU-UBI-03 sin coordenadas | US-12 | I |
| HU-UBI-03 actualización no cambia coordenadas | US-13, US-14 | I |
| HU-06.1 evento válido crea ficha | CP-01 | I |
| HU-06.1 evento con coordenadas -> DLQ | CP-04..CP-07 | U, I |
| HU-06.1 repetido / desorden / concurrencia | CP-02, CP-03, CP-12, CP-22 | I |
| HU-06.1 mal formado, límites, tipos, caracteres especiales | CP-08..CP-11, CP-13, CP-21..CP-25 | U, I |
| HU-06.1 aislamiento de BD | CP-16, SG-15 | I, SEG |
| HU-06.1 no se expone `usuario` | CP-14, PB-14 | I |
| D24 origen / `user_id` | CP-15 [BLOQ parcial] | U, I |
| HU-06.2 búsqueda por nombre, tildes, mayúsculas | PB-01..PB-04 | I |
| HU-06.2 operadores FULLTEXT y comodines LIKE | PB-11, PB-22 | I, SEG |
| HU-06.2 filtros combinados, vacío, sin filtros, paginación | PB-03..PB-08 | I |
| HU-06.2 parámetros inválidos / inyección | PB-09..PB-11, PB-20..PB-23 | I, SEG |
| HU-06.2 sin sesión | PB-15, SG-01 | I, SEG |
| HU-06.3 ficha sin coordenadas, `ubicacionProtegida` | PB-12..PB-14, PB-16 | I, SEG |
| HU-06.3 inexistente / UUID inválido | PB-17, PB-18 | I |
| HU-06.3 intento de obtener coordenadas | PB-16, SG-02, SG-03 | SEG |
| HU-06.3 / RNF7 explicación en la web | WP-05, UX-01 | E2E, USA |
| HU-06.4 caché Redis | CH-01..CH-04, CH-09 | I |
| HU-06.4 Redis caído y recuperación | CH-05..CH-07 | I |
| HU-06.4 mysql-lectura caída | CH-08, CH-10 | I |
| HU-06.4 caché sin datos sensibles | CH-11, CH-12 | I, SEG |
| HU-06.4 modo degradado (Should) | CH-13..CH-15 | I |
| HU-06.4 RNF5 <= 2 s frío / con carga científica | CG-02, CG-04 | CAR |
| HU-06.4 RNF1 pico x60 | CG-01, CG-03, CG-05..CG-07 | CAR |
| HU-06.5 reclasificación | CP-17..CP-21 | I |
| HU-06.6 estados de la UI | WP-01..WP-04, WP-06 | E2E |
| HU-06.6 navegadores (RNF13) | WP-10, WP-11 | E2E, USA (manual) |
| HU-06.6 sin credenciales / sin coordenadas en el DOM / solo gateway / textos maliciosos | WP-07, WP-08, WP-09, WP-14 | E2E, SEG |
| HU-06.2 / HU-06.6 flujo, navegación, accesibilidad, responsive | WP-12, WP-13, WP-15..WP-18 | E2E |
| HU-07.1 mapa alto rango, `no-store` | MP-01, MP-02 | I |
| HU-07.1 5 roles y sin token / vencido / otro emisor / sin `aud` | MP-03..MP-09, SG-04..SG-08 | I, SEG |
| HU-07.1 validación en cada petición | MP-10 | I |
| HU-07.1 descifrado solo en memoria | MP-13, DB-06, LG-02 | I, SEG |
| HU-07.1 filtros, vacío, inválidos, truncado, omitidos | MP-14..MP-21 | I |
| HU-07.2 pantalla de mapa | WC-02..WC-06, WI-05..WI-09, WI-13, WI-14 | U, E2E |
| HU-07.1 texto especial y bordes de coordenadas | MP-20, MP-21 | SEG, I |
| HU-07.3 auditoría (Could) | MP-22, MP-23 | I |
| HU-13.1 filtros combinables, orden, paginación | HB-01..HB-06, HB-10, HB-11 | I |
| HU-13.1 coordenadas por rol | HB-07..HB-09 | I, SEG |
| HU-13.1 bbox | HB-12, HB-13 | I, SEG |
| HU-13.1 sin token / público no accede / sin rol | HB-14, HB-15, SG-09 | I, SEG |
| HU-13.1 extras (parámetros mágicos, inyección, caché, eliminados, S4) | HB-24..HB-28 | I, SEG |
| HU-13.1 filtros inválidos | HB-18..HB-22 | I |
| HU-13.2 < 3 s con 10.000, límite de descifrado, índices | CG-08, HB-23, DB-07 | CAR, I |
| HU-13.3 pantalla de búsqueda | WC-01, WC-07, WC-08, WI-01..WI-04, WI-10, WI-12 | U, E2E |
| HU-08.J contrato estable | PB-24..PB-27, PB-29 | I |
| HU-08.J / RNF1 rate limit del gateway (Santiago) | SG-17..SG-19, CG-11 | SEG, CAR |
| Integración con Hernando (extremo a extremo) | IE-01..IE-04 | I |
| RNF9 / ASR6 cero coordenadas en BD, logs, eventos, caché | DB-01..DB-06, LG-01..LG-03, CH-11, CP-05 | I, SEG |
| RNF7 >= 90 % | UX-01..UX-03 | USA |
| RNF13 | WP-10, WP-11, WI-11 | E2E, USA |
| RNF6 <= 3 pasos (de 02 §10) | UX-04 | USA |
| Seguridad transversal (rol, JWT, CORS, errores sin trazas) | SG-01..SG-16 | SEG |
| Humo en VMs acordado | DX-01..DX-04 | E2E, SEG |

## P3. Casos del módulo ubicación (servicio-cientifico)
Formato: `| ID | Descripción | Datos / pasos | Esperado | Nivel | Criterio |`. Precondición común salvo que se indique: Flyway aplicado en MySQL 8.4 efímero, `application-test.yml` con llaves de test (`v1` activa). Cada test de integración revierte su transacción o usa un contenedor desechable.

### P3.1 Cifrado y llaves (CF)
| ID | Descripción | Datos / pasos | Esperado | Nivel | Criterio |
|---|---|---|---|---|---|
| CF-01 | Ida y vuelta | `cifrar(id, (4.6097,-74.0817))` y `descifrar` | Mismas coordenadas (igualdad exacta de `double`); `coord_cifrada` mide 32 bytes, nonce 12 | U | HU-UBI-01 |
| CF-02 | Fila guardada solo con columnas cifradas | `UbicacionService.registrar` con coordenadas; leer fila con JDBC crudo | `coord_cifrada`, `coord_nonce`, `clave_version='v1'`, `cifrado_en` no nulos; no existe ninguna columna de latitud/longitud | I | HU-UBI-01/02 |
| CF-03 | Nonce distinto con misma entrada | Cifrar 1.000 veces la misma coordenada y el mismo id | 1.000 nonces distintos y 1.000 textos cifrados distintos | U | HU-UBI-01 |
| CF-04 | Dos hallazgos, mismas coordenadas | Registrar dos hallazgos con `(4.6097,-74.0817)` | `coord_cifrada` y `coord_nonce` distintos entre filas | I | HU-UBI-01 |
| CF-05 | AAD: copiar a otro hallazgo | Copiar `coord_cifrada`+`coord_nonce`+`clave_version` de A a la fila B por SQL; leer B como alto rango | `CoordenadasNoDescifrablesException(INTEGRIDAD)`; B sale `NO_DISPONIBLES`; sin coordenadas en cuerpo ni log | I | HU-UBI-01 |
| CF-06 | AAD: mayúsculas del UUID | Cifrar con UUID canónico y descifrar con el mismo UUID en mayúsculas | Debe fallar o el código debe normalizar a minúsculas; definir cuál se implementó y fijar el comportamiento | U | HU-UBI-01 / J3.1 |
| CF-07 | Texto alterado | Cambiar 1 byte (cada posición 0..31, y el nonce) | Siempre `INTEGRIDAD`; nunca devuelve coordenadas aproximadas | U | HU-UBI-01 |
| CF-08 | Especie `NO_AMENAZADA` / `SIN_EVALUAR` también cifradas | Registrar con cada uno de los 3 estados | En los 3 casos solo columna cifrada (D15) | I | D15 |
| CF-09 | Coordenadas inválidas | lat 90.0001, -90.0001, lon 180.0001, -200, `NaN`, `Infinity`, solo latitud, solo longitud | `UbicacionInvalidaException` con `campo` y mensaje de `04` §6; no se guarda nada; el mensaje no contiene el valor | U | HU-UBI-01 |
| CF-09b | Bordes válidos | (90,180), (-90,-180), (0,0), (-0.0,0) | Se aceptan, ida y vuelta exacta | U | regla 3 |
| CF-10 | `toString()` enmascarado | `Coordenadas`, `CoordenadasCifradas`, `RegistroUbicacionComando`, `PropiedadesCifrado` | Ninguno imprime valores, bytes ni llaves | U | HU-UBI-01 |
| CF-11 | Llaves fuera del repo | `git grep -nE '[A-Za-z0-9+/]{43}='` sobre el repo; leer `.env.example` | Sin llaves reales; `UBICACION_CLAVES` y `UBICACION_CLAVE_ACTIVA` vacías | SEG | HU-UBI-02 |
| CF-12 | Arranque sin llave activa | Contexto con `UBICACION_CLAVE_ACTIVA` vacía; y con activa `v9` ausente de la lista | Falla el arranque con `"UBICACION_CLAVE_ACTIVA no está configurada o no existe en UBICACION_CLAVES"` (sin valores) | I | HU-UBI-02 |
| CF-13 | `clave_version` por registro | Activa `v1`, registrar; reiniciar con activa `v2` (v1 disponible), registrar otro | Filas con `v1` y `v2` respectivamente | I | HU-UBI-02 |
| CF-14 | Rotación sin pérdida | Tras CF-13 leer ambas como alto rango | Ambas descifran con su llave | I | HU-UBI-02 |
| CF-15 | Llave retirada | Fila `v1`, contexto solo con `v2` | `LLAVE_RETIRADA`; log ERROR con `hallazgoId` y versión, sin datos sensibles; el servicio sigue respondiendo | I | HU-UBI-02 |
| CF-16 | Llave de longitud inválida | 31, 33, 0 bytes; base64 inválido; con espacios | No arranca; mensaje `"UBICACION_CLAVES: la llave 'v1' no es válida (se esperan 32 bytes en base64)"` sin el valor | U | HU-UBI-02 |
| CF-17 | Id de llave inválido o repetido | `V1`, `v0`, `v10000`, `v1,v1`, `abc` | No arranca | U | HU-UBI-02 |
| CF-18 | Formato de lista | Entradas con espacio, coma final, vacía en medio | Comportamiento definido (rechazo claro o tolerancia documentada); nunca arranca con una llave truncada | U | J3.3 |
| CF-19 | Colisión (llave, nonce) | Forzar mismo nonce con `SecureRandom` simulado | El `UNIQUE` salta y el servicio reintenta una vez con otro nonce | I | J3.1 |
| CF-20 | Concurrencia | 50 hilos registrando a la vez | Sin nonces repetidos, sin errores de pool, sin pérdida | I | HU-UBI-01 |
| CF-21 | Llave activa cambiada con `v1` retirada y filas v1 existentes | Arrancar con activa `v2` y sin `v1` | Arranca (la ausencia de `v1` no es error de arranque), las filas v1 salen `NO_DISPONIBLES`; **riesgo R1 documentado** | I | J3.4 |

### P3.2 `UbicacionService` (US)
| ID | Descripción | Datos / pasos | Esperado | Nivel | Criterio |
|---|---|---|---|---|---|
| US-01 | Alta feliz | Comando completo, departamento `"15"` | `APLICADA`; fila con `version=1`, `departamento_codigo='15'` | I | HU-UBI-03 |
| US-02 | Departamento por nombre y sin tildes | `"Boyacá"`, `"boyaca"`, `"BOYACÁ"` | Los tres resuelven a `15` | I | HU-UBI-03 |
| US-03 | Alta duplicada | Mismo `hallazgoId` dos veces | Segunda: `IGNORADA_DUPLICADA`, sin cambios | I | HU-UBI-03 |
| US-04 | `actualizar` versión mayor | v1 guardada, `actualizar` v2 con otro estado y especie | `APLICADA`; coordenadas intactas (mismos bytes y nonce) | I | HU-UBI-03 |
| US-05 | `actualizar` versión igual o menor | v3 guardada; llegan v3 y v2 | `IGNORADA_VERSION_ANTIGUA`, nada cambia | I | HU-UBI-03 |
| US-06 | Datos inválidos | `hallazgoId` null, `version` 0, nombre vacío/151 caracteres, fecha futura, investigador vacío/101, depto `"99"`, estado null | `UbicacionInvalidaException` con `campo` y mensaje de `04` §6 por cada uno; sin escritura | U | HU-UBI-03 |
| US-07 | Borde de nombre | Nombre de exactamente 150 | `APLICADA` | U | HU-UBI-03 |
| US-08 | Estado ausente | `estadoConservacion=null` | Se rechaza (no se asume `EN_PELIGRO`) | U | D32 P-J2 |
| US-09 | Caracteres especiales | Nombre `Ñandú "x" <b>&</b> 'a'; DROP`; emoji; mayúsculas con tilde | Se guarda literal y se devuelve literal (la búsqueda posterior lo encuentra) | I | seguridad |
| US-10 | Sin transacción | Llamar `registrar` fuera de `@Transactional` | `IllegalTransactionStateException` | I | J4 |
| US-11 | Rollback completo | `registrar` lanza dentro de una transacción que ya insertó una fila de prueba | Todo revertido (fila de prueba incluida) | I | J4 |
| US-12 | Sin coordenadas | latitud y longitud null | Fila con 4 columnas de cifrado NULL; aparece en búsqueda con `SIN_COORDENADAS`; no aparece en mapa | I | HU-UBI-03 |
| US-13 | `actualizar` de hallazgo desconocido | UUID nunca registrado | `UbicacionNoEncontradaException` | I | J4 |
| US-14 | `actualizar` no toca investigador | Comando sin investigador | Investigador original intacto | I | J4 |
| US-15 | Fecha de hoy y de mañana (zona `America/Bogota`) | Fecha = hoy / mañana | hoy acepta; mañana rechaza (probar a las 23:30 y 00:30 con `Clock` fijo) | U | `04` §6 |

### P3.3 Cifrado en reposo (DB) - ver también P5
| ID | Descripción | Datos / pasos | Esperado | Nivel | Criterio |
|---|---|---|---|---|---|
| DB-01 | Columnas | Consulta de P5 §1 | Solo `coord_cifrada`, `coord_nonce` | I | RNF9 |
| DB-02 | Valores centinela no aparecen | Consultas de P5 §2 tras registrar los 3 estados | 0 filas | I | RNF9 |
| DB-03 | Volcado | `mysqldump` de `cientifico` y búsqueda de centinelas (texto y hex) | 0 coincidencias | SEG | RNF9 |
| DB-04 | Sin copia en `consulta_publica` | Dump de `consulta_publica` tras consumir eventos | 0 coincidencias | SEG | D14 |
| DB-05 | Llaves no están en la BD | Buscar en dump el base64 de las llaves de test | 0 coincidencias | SEG | HU-UBI-02 |
| DB-06 | Descifrado no se persiste | Llamar mapa y búsqueda como alto rango; repetir dump, `GET` de Redis y logs | 0 coincidencias | SEG | HU-07.1 |
| DB-07 | Índices | `SHOW INDEX FROM ubicacion_hallazgo` y `EXPLAIN` de las consultas de `05` §7 | Existen los 4 índices; `EXPLAIN` usa índice (no `ALL`) en filtros de especie, fecha, investigador, depto | I | HU-13.2 |
| DB-08 | CHECK de grupo | `UPDATE` poniendo solo `coord_cifrada` NULL | Rechazado por `ck_ubicacion_hallazgo_cifrado_completo` | I | `05` §3.1 |

### P3.4 Búsqueda interna `GET /api/cientifico/ubicacion/hallazgos` (HB)
Datos base HB: 12 hallazgos: especies `Atelopus ebenoides`, `Atelopus muisca`, `Rana x`; fechas 2026-01..2026-06; investigadores `investigador1`, `investigador2`; deptos 15, 25, 05; estados mezclados; 2 sin coordenadas. Roles de prueba: cada uno de los 5 y "sin rol" (autenticado con `aud` correcto pero sin ninguno de los roles).
| ID | Descripción | Datos / pasos | Esperado | Nivel | Criterio |
|---|---|---|---|---|---|
| HB-01 | Sin filtros | `GET` sin parámetros, `investigador1` | 200, `page=0,size=20`, orden `fechaRecoleccion` desc con desempate por id | I | HU-13.1 |
| HB-02 | Dos filtros | `especie=Atelopus&desde=2026-01-01&hasta=2026-06-30` | Solo coincidencias; `total` correcto | I | HU-13.1 |
| HB-03 | Cuatro filtros | especie+fechas+`investigador=investigador1`+`departamento=15` | Solo coincidencias | I | HU-13.1 |
| HB-04 | Departamento por código, nombre y sin tilde | `15`, `Boyacá`, `boyaca` | Mismo resultado | I | `04` §1 |
| HB-05 | Prefijo y sin distinguir tildes/mayúsculas | `especie=ATELOPUS`, `especie=telopus` | 1.º encuentra, 2.º no (es prefijo) | I | `04` §1 |
| HB-06 | Sin resultados | `especie=zzzz` | 200, `content=[]`, `total=0`, `totalPaginas=0` | I | HU-13.1 |
| HB-07 | Matriz de coordenadas por rol | Hallazgo `EN_PELIGRO` y otro `NO_AMENAZADA`, ambos con coordenadas; pedir con cada rol | `cientifico_alto_rango`: `VISIBLES` con `latitud`/`longitud` exactas. Los otros 4 roles: sin las claves `latitud`/`longitud` (la clave no existe en el JSON), `estadoCoordenadas="PROTEGIDAS"`, `ubicacionProtegida=true`, `omitidos=0` | I | HU-13.1, D21 |
| HB-08 | Rol sin permiso: barrido de cuerpo | Serializar la respuesta de los 4 roles y buscar centinelas (texto, hex, base64 del cifrado) y las subcadenas `lat`, `lon`, `coord` | Ninguna coincidencia (tampoco `coord_cifrada`) | SEG | RF12, RNF9 |
| HB-09 | Minimización en SQL | Capturar el SQL ejecutado (p6spy o `DataSource` proxy) para un rol sin permiso | La consulta no selecciona `coord_*` | I | J7 |
| HB-10 | Paginación | 45 hallazgos: `page=2&size=20`; `page=0&size=100`; `size=1` | 5 elementos y `total=45`; 45; 1 con `totalPaginas=45` | I | HU-13.1 |
| HB-11 | Página fuera de rango | `page=50` con 12 hallazgos | 200, vacío, `total=12` | I | borde |
| HB-12 | `bbox` rol sin permiso | `bbox=1,2,3,4` con `investigador1`, `auditor1`, `analista1`, `admin1` | 403 `ROL_INSUFICIENTE`, sin coordenadas | I | HU-13.1 |
| HB-13 | `bbox` alto rango | idem con `cientifico1` | 400 `NO_SOPORTADO` | I | `04` §5.1 |
| HB-14 | Sin token | Sin `Authorization` | 401 sin cuerpo, `WWW-Authenticate: Bearer` | I | HU-13.1 |
| HB-15 | Autenticado sin ninguno de los 5 roles | JWT válido sin roles de negocio | 403 `ROL_INSUFICIENTE` | I | HU-10 c.2 |
| HB-16 | Llave retirada (alto rango) | Fila `v1`, solo `v2` configurada | Esa fila: `estadoCoordenadas="NO_DISPONIBLES"`, sin `latitud`/`longitud`, `omitidos=1`; resto bien; 200 | I | J3.4 |
| HB-17 | Texto alterado (alto rango) | Alterar 1 byte en BD | Igual que HB-16; log `"Integridad de ubicación fallida hallazgoId=…"` sin valores | I | HU-UBI-01 |
| HB-18 | `desde` > `hasta` | `desde=2026-06-30&hasta=2026-01-01` | 400 `PARAMETRO_INVALIDO`, campo `hasta` | I | HU-13.1 |
| HB-19 | Fecha mal formada | `desde=2026-13-01`, `desde=30/06/2026`, `desde=2026-02-30`, `desde=abc` | 400 "fecha inválida, formato AAAA-MM-DD" | I | HU-13.1 |
| HB-20 | Límites de `size`/`page`/textos | `size=0`, `101`, `-1`, `abc`; `page=-1`; `especie=a` (1), 101 caracteres; `investigador` vacío tras recorte y 101 caracteres | 400 con `errores[]` por campo (vacío tras recorte equivale a ausente: 200) | I | HU-13.1 |
| HB-21 | Paginación profunda | `(page+1)*size` = 10.000 y 10.001 (`page=99,size=100` y `page=100,size=100`) | 200 y 400 respectivamente (`04` §1; nota: `03` §J7 dice `page*size > 10.000`, ver riesgo RQ-5) | I | `04` §1 |
| HB-22 | Departamento desconocido | `departamento=99`, `Atlantida` | 400 | I | `04` §1 |
| HB-23 | Descifrado limitado a la página | `size=50`, alto rango, 10.000 filas; contar llamadas a `descifrar` | <= 50 | I | HU-13.2 |
| HB-24 | Parámetros desconocidos | `?incluirCoordenadas=true&foo=1` con `investigador1` | Igual que sin ellos; sin coordenadas | SEG | `04` §1 |
| HB-25 | Texto especial en filtros | `especie=%27%20OR%20%271%27%3D%271`, `especie=%25`, `especie=_`, `especie=\`, `investigador=' OR 1=1 --` | 200 vacío o solo coincidencias literales; `%` y `_` no actúan como comodines; sin error SQL | SEG | RNF9/OWASP |
| HB-26 | `Cache-Control` | Cualquier respuesta (200, 400, 401, 403) | `no-store` | I | `04` §3 |
| HB-27 | Hallazgos eliminados | Fila con `eliminado_en` no nulo (insertada por SQL) | No aparece | I | `05` §9 |
| HB-28 | `investigador1` ve hallazgos de otro investigador | S4 | Aparecen (sin coordenadas) | I | S4 |

### P3.5 Mapa `GET /api/cientifico/ubicacion/mapa` (MP)
| ID | Descripción | Datos / pasos | Esperado | Nivel | Criterio |
|---|---|---|---|---|---|
| MP-01 | Alto rango feliz | `cientifico1`; hallazgos de los 3 estados | 200 `FeatureCollection`; `coordinates=[lon,lat]` exactos (orden GeoJSON); `total`, `devueltos`, `truncado=false`, `omitidos=0`, `limite=1000` | I | HU-07.1 |
| MP-02 | `Cache-Control` | MP-01 | `no-store` (también en errores) | I | HU-07.1 |
| MP-03 | Matriz roles | `investigador1`, `auditor1`, `analista1`, `admin1`, "sin rol" | 403 `ROL_INSUFICIENTE` (`application/problem+json`), cuerpo sin coordenadas ni `features` | I | HU-07.1 |
| MP-04 | Sin token | Sin `Authorization` | 401 | I | HU-07.1 |
| MP-05 | Token vencido | `exp` pasado (> tolerancia de 60 s) | 401 | I | HU-07.1 |
| MP-06 | Otro emisor | `iss` distinto | 401 | SEG | HU-07.1 |
| MP-07 | Sin `aud=biohub-api` | `aud` ausente/otro | 401 | SEG | HU-07.1 |
| MP-08 | Firma alterada / `alg:none` / Basic / `Bearer ` vacío | Variantes | 401 | SEG | HU-07.1 |
| MP-09 | 403 antes que 400 | Rol sin permiso con `desde>hasta` | 403 (no revela validación) | SEG | J8 |
| MP-10 | Revocación | Token con rol, luego token nuevo sin rol | Primer token 200; segundo 403; no hay caché de permisos | I | HU-07.1 |
| MP-11 | Llave retirada | Mezcla de filas `v1` y `v2`, `v1` retirada | 200; filas `v1` omitidas, `omitidos` correcto, `devueltos+omitidos` consistente con `total` | I | HU-07.1 |
| MP-12 | Texto alterado | 1 fila alterada | Igual que MP-11 | I | HU-UBI-01 |
| MP-13 | Descifrado solo en memoria | Tras 20 llamadas inspeccionar Redis, tablas, logs, archivos temporales, heap con `jmap -histo` no aplicable: revisar campos `static` y cachés (`@Cacheable` ausente en `ubicacion`) | Sin rastro; ningún `@Cacheable` en el módulo | SEG | HU-07.1 |
| MP-14 | Filtros combinados | `especie=Atelopus&departamento=15` | Solo coincidencias | I | HU-07.1 |
| MP-15 | Vacío | Filtros sin coincidencias | 200, `features=[]`, `total=0`, `devueltos=0`, `truncado=false` | I | HU-07.1 |
| MP-16 | Fechas inválidas | `desde>hasta`, `desde=2026-02-30` | 400 | I | HU-07.1 |
| MP-17 | Truncado | 1.001 y 1.500 con coordenadas (también 1.000 exactos) | 1.000 exactos: `truncado=false`; 1.001: `devueltos=1000`, `truncado=true`, `total=1001`; orden fecha desc | I | HU-07.1 |
| MP-18 | `hallazgoId` | UUID existente con coordenadas; sin coordenadas; inexistente; mal formado; junto a otros filtros | 1 feature; 0; 0; 400 campo `hallazgoId`; los demás filtros se ignoran | I | `04` §5.2 |
| MP-19 | Hallazgos sin coordenadas | 2 de 12 sin coordenadas | No aparecen y no cuentan en `total` | I | HU-UBI-03 |
| MP-20 | Parámetros de texto especiales | Igual que HB-25 | Sin error SQL | SEG | OWASP |
| MP-21 | Coordenadas de borde | (90,180), (-90,-180), (0,0) | Features con valores exactos | I | regla 3 |
| MP-22 | Auditoría de acceso permitido (Could) | `cientifico1` llama | Un log en `biohub.auditoria.ubicacion` con usuario, endpoint, filtros, devueltos, omitidos; sin coordenadas | I | HU-07.3 |
| MP-23 | Auditoría de acceso denegado | `investigador1` llama | Un log con usuario y marca DENEGADO | I | HU-07.3 |

### P3.6 Re-cifrado `POST /api/cientifico/ubicacion/recifrado` (RC, Should)
| ID | Descripción | Datos / pasos | Esperado | Nivel | Criterio |
|---|---|---|---|---|---|
| RC-01 | Re-cifrado feliz | 1.200 filas `v1`, activa `v2` con ambas configuradas, `admin1` | 200 `{claveActiva:"v2", revisados:1200, recifrados:1200, fallidos:0, pendientesPorVersion:{}}`; todas `v2`; nonces nuevos; coordenadas idénticas al leerlas como alto rango | I | HU-UBI-02 |
| RC-02 | Idempotencia | Repetir | `revisados:0`, `pendientesPorVersion:{}` | I | `04` §5.3 |
| RC-03 | Filas no descifrables | 2 filas alteradas | `fallidos:2`; siguen en su versión; ids solo en log | I | `04` §5.3 |
| RC-04 | Roles | Los otros 4 roles, sin token | 403 / 401; el `administrador` **no** recibe coordenadas en la respuesta | SEG | D20 |
| RC-05 | Concurrencia | Dos llamadas simultáneas | Una 200 y otra 409 `CONFLICTO` | I | `04` §5.3 |
| RC-06 | Interrupción y reanudación | Cortar a mitad (excepción en el lote 2) | Lotes confirmados persisten; al repetir termina; sin filas corruptas | I | `05` §12 |

### P3.7 Fugas en logs (LG)
| ID | Descripción | Datos / pasos | Esperado | Nivel | Criterio |
|---|---|---|---|---|---|
| LG-01 | Logs de alta, búsqueda y mapa | `OutputCaptureExtension` en todos los flujos felices y de error; buscar centinelas, base64 de llaves, hex del cifrado | 0 coincidencias | SEG | HU-UBI-01 |
| LG-02 | Nivel `DEBUG`/`TRACE` | Repetir con `logging.level.root=TRACE` y `org.hibernate.SQL`/`BasicBinder` en TRACE | 0 coincidencias en parámetros enlazados de `coord_cifrada` y `Authorization` (riesgo: el binder imprime parámetros) | SEG | RNF9 |
| LG-03 | Mensajes de error al cliente | Forzar 400/500 | Sin trazas, sin SQL, sin nombres de clase, `server.error.include-*` en `never` | SEG | D29 |

## P4. Casos de consulta pública (servicio-consulta-publica)

### P4.1 Consumo de eventos (CP)
Fixtures: JSON de `datos` válido según el JSON Schema de `04` §7.3, en `src/test/resources/eventos/`. Los eventos los publica el propio test en un RabbitMQ Testcontainers con las colas/DLX reales de `deploy/rabbitmq` (`consulta-publica.eventos` y `.dlq`); nunca en el broker de las VMs. "-> DLQ" significa: mensaje presente en `consulta-publica.eventos.dlq`, cola principal vacía, tabla `ficha_publica` sin cambios, y la cola principal sigue procesando el siguiente mensaje.
| ID | Descripción | Datos / pasos | Esperado | Nivel | Criterio |
|---|---|---|---|---|---|
| CP-01 | Evento válido crea ficha | `hallazgo.registrado` v1, `NO_AMENAZADA`, depto `15` | Fila en `ficha_publica`; región `Andina` derivada del catálogo; `GET /api/publica/fichas/{id}` 200 | I | HU-06.1 |
| CP-02 | Duplicado | Mismo evento dos veces (mismo `id` y `version`) | Una sola fila; sin error; log DEBUG con id | I | HU-06.1 |
| CP-03 | Desorden | Llega v2 y luego v1 del mismo hallazgo | Ficha sigue en v2 con datos de v2 | I | HU-06.1 |
| CP-04 | Coordenadas al primer nivel | `datos` con `latitud`/`longitud`, estado `EN_PELIGRO` | -> DLQ; log WARN `"Evento con coordenadas rechazado id=…"` sin valores | I | HU-06.1 |
| CP-05 | Todas las claves prohibidas | Parametrizado: `latitud, longitud, lat, lon, lng, coordenadas, coordinates, geometry, geom, ubicacionExacta` en minúsculas, MAYÚSCULAS y `Mixto` | Cada una -> DLQ; el log no contiene los valores | U + I | D24 |
| CP-06 | Claves prohibidas anidadas | Dentro de `datos.extra.{a:[{lat:1}]}`, en arreglos y a 5 niveles | -> DLQ | U | D24 |
| CP-07 | `NO_AMENAZADA` con coordenadas | Estado `NO_AMENAZADA` + `coordenadas` | -> DLQ (D14: ya no se publica nada) | I | D14 |
| CP-08 | JSON roto / vacío / cuerpo no UTF-8 | `{roto`, `""`, bytes 0xFF | -> DLQ | I | HU-06.1 |
| CP-09 | Obligatorios ausentes | Quitar uno a uno: `hallazgoId, version, nombreCientifico, familia, fechaRecoleccion, estadoConservacion, departamento` | Cada uno -> DLQ | U + I | HU-06.1 |
| CP-10 | `estadoConservacion` ausente | Sin el campo | -> DLQ (no se asume peligro) | U | D32 |
| CP-11 | `estadoConservacion` desconocido | `"CRITICA"`, `"en_peligro"` | Ficha guardada con `EN_PELIGRO`; WARN; no DLQ | U + I | regla 2 |
| CP-12 | Misma versión, contenido distinto | v2 con nombre A y luego v2 con nombre B; y dos mensajes v2/v3 concurrentes del mismo hallazgo (2 hilos) | El de misma versión se ignora (queda A) -> se documenta como comportamiento esperado (riesgo RQ-3); concurrencia: sin fila duplicada, gana v3, sin deadlock | I | D23 |
| CP-13 | Sobre inválido | Cuerpo de 64 KB + 1 byte; `tipo` desconocido; routing key distinta del tipo; sobre sin `id` | Cada uno -> DLQ | I | HU-06.1 |
| CP-14 | `usuario` no se guarda | Sobre con `usuario="investigador1"` | Ninguna columna lo contiene; `GET` de ficha y listado no lo contienen; tampoco en Redis ni logs INFO | I | HU-06.1 |
| CP-15 | Origen y publicador | `origen="otro-servicio"` -> DLQ. Con `verificar-publicador=true`: `user_id` != `cientifico_mq` -> DLQ; `user_id=cientifico_mq` -> OK. Con `false`: no se exige | Según lo descrito. **[BLOQ parcial]**: `setUserId` en el publicador real no existe aún (SEC-D-03, Santiago); se prueba con `MessageProperties` simulado | U + I | D24 |
| CP-16 | Aislamiento | Revisar `spring.datasource` y ejecutar con el usuario de BD de consulta: `SELECT * FROM cientifico.ubicacion_hallazgo`; revisar que no haya dependencia de `ubicacion` ni cliente HTTP a `servicio-cientifico` | Acceso denegado (ERROR 1142); solo `mysql-lectura`; ArchUnit: `consulta-publica` no importa paquetes de `cientifico` | I | HU-06.1 |
| CP-17 | Reclasificación a `EN_PELIGRO` | Ficha v1 `NO_AMENAZADA` cacheada; llega `hallazgo.reclasificado` v2 `EN_PELIGRO` | Ficha v2 con `EN_PELIGRO`, `ubicacionProtegida=true`; la entrada de caché se invalidó (la siguiente respuesta ya trae v2) | I | HU-06.5 |
| CP-18 | Cambio de especie | `nombreCientifico` A -> B | `q=B` la encuentra; `q=A` ya no; búsquedas cacheadas se vacían | I | HU-06.5 |
| CP-19 | Reclasificación de hallazgo desconocido | (a) snapshot completo; (b) snapshot incompleto | (a) crea la ficha; (b) -> DLQ | I | HU-06.5 |
| CP-20 | `hallazgo.actualizado` | Mismo esquema, v mayor | Se aplica | I | D23 |
| CP-21 | Cola no se bloquea | 1 inválido seguido de 1 válido | El válido se procesa; DLQ tiene 1 | I | HU-06.1 |
| CP-22 | mysql-lectura caída durante consumo | Detener contenedor, publicar válido, esperar 3 reintentos, reiniciar contenedor, mover DLQ -> cola | Tras reintentos va a DLQ; al reenviar se aplica una sola vez | I | J5 |
| CP-23 | Límites de campos | `nombreCientifico` 150/151; `familia` 100/101; `descripcion` 5.000/5.001; `nombreComun` 150/151; `tipoEspecimen` 80/81 | El límite exacto se acepta; +1 -> DLQ | U | `04` §7.3 |
| CP-24 | Caracteres especiales | Nombre `Ñandú <script>alert(1)</script> 'x' "y" ; --`, descripción con emoji, saltos de línea y `\u0000` | Se guarda literal (sin ejecutar nada); la API lo devuelve como texto JSON escapado; `\u0000` se rechaza o se limpia sin error 500 | I | OWASP |
| CP-25 | Tipos inválidos | `version`: 0, -1, 1.5, `"2"`, `null`; `fechaRecoleccion`: `2026-02-30`, `30/06/2026`; `hallazgoId`: `"abc"`; departamento `"99"`; campos extra no prohibidos (`foo`) | Inválidos -> DLQ; el campo extra se ignora y el evento se acepta | U + I | `04` §7.3 |

### P4.2 Endpoints públicos (PB)
Datos base PB: 45 fichas (para paginación), entre ellas `Atelopus ebenoides` (Bufonidae, 15, `EN_PELIGRO`, nombre común "Rana arlequín de Boyacá"), `Atelopus muisca` (Bufonidae, 25), `Rana de prueba`; estados repartidos. Se cargan por eventos (no por SQL) para ejercitar el flujo completo. Base `G` = servicio directo (8083) o gateway según el caso.
| ID | Descripción | Datos / pasos | Esperado | Nivel | Criterio |
|---|---|---|---|---|---|
| PB-01 | Búsqueda por nombre científico | `q=atelopus` | 200; incluye `Atelopus ebenoides`; `total>=1` | I | HU-06.2 |
| PB-02 | Sin distinguir mayúsculas ni tildes | `q=ATELOPUS`, `q=rana arlequin`, `q=Arlequín` | Todas encuentran la ficha (nombre común con tilde) | I | HU-06.2 |
| PB-03 | Familia y departamento combinados | `familia=bufonidae&departamento=15` y `departamento=Boyacá` y `=boyaca` | Solo fichas de ambos; idéntico por código y por nombre | I | HU-06.2 |
| PB-04 | Familia exacta | `familia=Bufo` (prefijo) | 0 resultados (es igualdad) | I | `04` §4.1 |
| PB-05 | Sin filtros | Sin parámetros | `page=0`, `size=20`, orden por `nombreCientifico` asc, desempate estable entre páginas (sin repetidos ni saltos) | I | HU-06.2 |
| PB-06 | Paginación | 45 fichas: `page=2&size=20`; `page=0&size=50`; `size=1` | 5 y `total=45`; 45; 1 y `totalPaginas=45` | I | HU-06.2 |
| PB-07 | Sin resultados | `q=zzzz` | 200, `content=[]`, `total=0`, `totalPaginas=0` | I | HU-06.2 |
| PB-08 | Bordes de `size` | `size=1`, `50`; `0`, `51`, `500`, `-1`, `abc`, `1.5` | 200 para 1 y 50; 400 `PARAMETRO_INVALIDO` con `errores[campo=size,"debe estar entre 1 y 50"]` | I | HU-06.2 |
| PB-09 | Bordes de `page` | `page=0`; `-1`, `abc`; paginación profunda `page=199&size=50` (10.000) y `page=200&size=50` | 200; 400 (`"debe ser un entero mayor o igual a 0"`); 200; 400 (`"no se puede paginar más allá de 10.000 resultados"`) | I | `04` §4.1 |
| PB-10 | Bordes de `q` | 1, 2, 3, 100, 101 caracteres; solo espacios; `q=` vacío | 400 para 1 y 101 (`"debe tener entre 2 y 100 caracteres"`); 200 para 2, 3, 100; espacios/vacío = ausente (200 sin filtro) | I | HU-06.2 |
| PB-11 | Operadores de FULLTEXT | `q=+-`, `q=*`, `q="`, `q=()`, `q=@@`, `q=-atelopus`, `q=atelopus*`, `q=ab`, `q=a b c` | Sin 500; solo operadores no devuelve "todo" por error (definir: lista vacía o por prefijo literal); `ab` usa la rama `LIKE 'q%'`; resultados coherentes | I | J7 |
| PB-12 | Ficha de cada estado | Fichas `EN_PELIGRO`, `NO_AMENAZADA`, `SIN_EVALUAR` | 200 con campos de `04` §4.2; `ubicacionProtegida=true` en las 3 | I | HU-06.3, D14 |
| PB-13 | Barrido de coordenadas | Para cada ficha y cada listado: parsear JSON y buscar recursivamente claves `lat`, `lon`, `lng`, `coord…`, `geom`, `geometry`, `ubicacionExacta` y los valores centinela (texto y como números) | Ninguna aparece; el cuerpo crudo tampoco contiene `latitud`/`longitud` | SEG | RNF9, D14 |
| PB-14 | Datos del investigador | Ficha y listado | Sin `investigador`, `usuario`, `preferred_username` | SEG | HU-06.1 |
| PB-15 | Sin sesión | Sin `Authorization` | 200 (no 401) | I | HU-06.2 |
| PB-16 | Parámetro y rol no cambian nada | `?incluirCoordenadas=true`, JWT de cada uno de los 5 roles | Cuerpo byte a byte idéntico al anónimo | SEG | HU-06.3 |
| PB-17 | Ficha inexistente | UUID válido no existente | 404 `NO_ENCONTRADO` problem+json | I | HU-06.3 |
| PB-18 | `id` inválido | `abc`, `123`, `3f2a…` truncado, UUID con espacios, `%00`, `' OR 1=1`, `../`, 100 caracteres | 400 `PARAMETRO_INVALIDO` campo `id` "identificador inválido"; nunca 500 ni traza | I | HU-06.3 |
| PB-19 | Opcionales | Ficha sin `nombreComun`, `tipoEspecimen`, `descripcion` | Claves presentes con `null` | I | `04` §1 |
| PB-20 | Inyección SQL en `q` | `q=' OR '1'='1`, `q=1; DROP TABLE ficha_publica;--`, `q=" UNION SELECT 1,2,3--` | 200 vacío o coincidencias literales; tabla intacta tras cada una; sin mensaje de SQL | SEG | HU-06.2 |
| PB-21 | Inyección en `familia`, `departamento`, `page`, `size` | Mismos payloads | 200 vacío o 400; nunca 500 | SEG | HU-06.2 |
| PB-22 | Comodines LIKE | `q=%%`, `q=_ _`, `q=\\` en la rama `LIKE` | `%`/`_` se escapan: no devuelven todo | SEG | J7 |
| PB-23 | Unicode y especiales | `q=ñandú`, `q=日本`, emoji, `q=%00`, `q=<script>`, URL de 9.000 caracteres | Sin 5xx; 400/200 según longitud; sin reflejar el valor en HTML | SEG | OWASP |
| PB-24 | Contrato de listado | Validar respuesta contra JSON Schema derivado de `04` §4.1 | Claves exactas `content,page,size,total,totalPaginas,modoDegradado,filtrosIgnorados`; cada item exactamente `id,nombreCientifico,nombreComun,familia,estadoConservacion,departamento,departamentoCodigo,region` | I | HU-08.J |
| PB-25 | Contrato de ficha | Esquema de `04` §4.2 | Claves exactas (sin extras) | I | HU-08.J |
| PB-26 | Cabeceras de caché | 200 ficha, 200 listado, 400, 404, 503 | `public, max-age=60`; `public, max-age=30`; `no-store` en todos los errores | I | `04` §3 |
| PB-27 | Formato de error | Todos los 4xx/5xx | `Content-Type: application/problem+json`, claves `type,title,status,detail,instance,codigo`; `detail` en español, sin trazas ni nombres de clase/SQL | I | D29 |
| PB-28 | Departamento desconocido | `departamento=99`, `Narnia` | 400 campo `departamento` "departamento desconocido" | I | `04` §4.1 |
| PB-29 | Métodos no permitidos | POST, PUT, DELETE, PATCH a `/api/publica/fichas` y `/{id}` | 405 (directo); 401 sin token vía gateway (SE-11) | I | seguridad |

### P4.3 Caché, resiliencia y modo degradado (CH)
Contenedores: MySQL, Redis y RabbitMQ Testcontainers; para latencias, Toxiproxy (Testcontainers) delante de Redis/MySQL.
| ID | Descripción | Datos / pasos | Esperado | Nivel | Criterio |
|---|---|---|---|---|---|
| CH-01 | Segunda consulta desde caché | `GET` ficha dos veces; contar sentencias SQL (datasource proxy) | La segunda: 0 sentencias; clave `biohub:publica:fichas::<uuid>` existe con TTL <= 300 s | I | HU-06.4 |
| CH-02 | Listados con TTL 60 s | `GET` listado; `TTL` de la clave `busquedas::…` | <= 60; clave contiene `q`,`f`,`d`,`p`,`s`,`deg` normalizados (misma clave para `Atelopus` y ` atelopus `) | I | `03` J6 |
| CH-03 | Invalidación por evento | Cachear ficha y listado; aplicar evento que la modifica | La siguiente consulta devuelve los datos nuevos; `fichas::id` y todas las `busquedas::*` borradas **solo después del commit** | I | HU-06.4 |
| CH-04 | `sync=true` | 200 hilos piden a la vez una ficha con caché vacía | 1 sola consulta SQL | I | J6 |
| CH-05 | Redis caído, ficha | `container.stop()`; `GET` ficha | 200 desde MySQL, sin 5xx; WARN | I | HU-06.4 |
| CH-06 | Redis caído, búsqueda | idem listado | 200; WARN no más de 1 cada 30 s (100 peticiones -> <= 1-2 WARN) | I | HU-06.4 |
| CH-07 | Redis vuelve | `start()` sin reiniciar la app; `GET` | Se vuelve a usar (clave aparece) sin reinicio | I | HU-06.4 |
| CH-08 | mysql-lectura caída, ficha fuera de caché | `stop()` MySQL; `GET` ficha nueva | 503 problem+json `SERVICIO_NO_DISPONIBLE`, en < 3 s (Hikari 2 s), sin detalles internos | I | HU-06.4 |
| CH-09 | mysql-lectura caída, ficha en caché | Cachear y detener MySQL | 200 desde caché | I | HU-06.4 |
| CH-10 | Redis lento | Toxiproxy latencia 2 s a Redis | La petición no espera más de ~300 ms por llamada a Redis y responde 200 | I | J6 |
| CH-11 | Caché sin datos sensibles | `SCAN` + `GET` de todas las claves tras una sesión completa; regex de claves prohibidas y centinelas | 0 coincidencias; valores solo con campos de la lista pública | SEG | HU-06.4 |
| CH-12 | No se cachean errores | Provocar 400, 404, 503 | Ninguna clave nueva por esos casos | I | J6 |
| CH-13 | Modo degradado | `CONSULTA_MODO_DEGRADADO=true`; `?q=atelopus&familia=X&departamento=15` | 200, `modoDegradado=true`, `filtrosIgnorados=["familia","departamento"]`; resultados solo por `q` | I | HU-06.4 |
| CH-14 | Degradado: solo se listan los enviados | `?q=atelopus` | `filtrosIgnorados=[]` (o solo los enviados) | I | `04` §4.1 |
| CH-15 | Degradado: ficha y paginación | `GET` ficha, `page/size` | Funcionan; sin 5xx | I | HU-06.4 |
| CH-16 | Evict con Redis caído | Redis detenido, llega evento, Redis vuelve | El dato viejo puede vivir como máximo el TTL (5 min / 60 s); se verifica el límite con reloj controlado (riesgo aceptado R-J) | I | `03` J6 |
| CH-17 | Salud | Redis detenido | `/actuator/health` sigue `UP` | I | J6 |

## P5. Verificación de cifrado en reposo (RNF9 / ASR6)
Se ejecuta en MySQL Testcontainers (y en el Compose efímero de carga), **nunca** en las VMs. Procedimiento tras registrar con `UbicacionService` (o por la API) hallazgos de los tres estados, con centinelas `(4.6097, -74.0817)` y `(5.1234567, -73.1234567)`:

1. **No existen columnas de coordenadas en claro** (DB-01). Esperado: solo `ubicacion_hallazgo.coord_cifrada` y `coord_nonce`.
```sql
SELECT table_schema, table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema IN ('cientifico','consulta_publica')
  AND column_name REGEXP 'lat|lon|lng|coord|geom|ubicacion_exacta'
ORDER BY 1,2,3;
```
2. **Los valores no aparecen** (DB-02). Esperado: todas devuelven 0.
```sql
-- texto y HEX de los dos centinelas (doubles IEEE-754 big-endian: 4.6097=4012705532617C1C, -74.0817=C052853A92A30553)
SELECT COUNT(*) FROM cientifico.ubicacion_hallazgo
 WHERE CAST(coord_cifrada AS CHAR CHARACTER SET latin1) LIKE '%4.6097%'
    OR CAST(coord_cifrada AS CHAR CHARACTER SET latin1) LIKE '%74.0817%'
    OR HEX(coord_cifrada) LIKE '%4012705532617C1C%'
    OR HEX(coord_cifrada) LIKE '%C052853A92A30553%';
-- búsqueda en todas las columnas textuales de ambos esquemas (generada desde information_schema):
SELECT CONCAT('SELECT "',table_name,'.',column_name,'" c, COUNT(*) n FROM ',table_schema,'.',table_name,
              ' WHERE CAST(`',column_name,'` AS CHAR) LIKE "%4.6097%" OR CAST(`',column_name,'` AS CHAR) LIKE "%74.0817%"')
FROM information_schema.columns WHERE table_schema IN ('cientifico','consulta_publica');
-- ejecutar cada sentencia generada: n = 0 en todas
```
3. **El dato sí está cifrado, es válido y distinto por fila** (control positivo, para no aprobar con una tabla vacía): `SELECT COUNT(*), COUNT(DISTINCT coord_nonce), MIN(LENGTH(coord_cifrada)), MAX(LENGTH(coord_cifrada)), COUNT(DISTINCT clave_version) FROM ubicacion_hallazgo WHERE coord_cifrada IS NOT NULL;` -> `COUNT(*) = COUNT(DISTINCT coord_nonce)` y `LENGTH = 32`.
4. **Volcado completo** (DB-03/DB-04): `mysqldump --hex-blob --all-databases` y `grep -c` de los centinelas en texto, en HEX y en base64 (`printf 4.6097 | base64`); idem sobre el archivo de datos (`strings` sobre `ibd` en el contenedor) y sobre binlog si está activo. Esperado: 0.
5. **Otras superficies** (DB-06, CH-11, LG-01): respuesta cruda de `consulta-publica`, claves/valores de Redis, mensajes del broker (`GET /api/queues/.../get` con `ackmode=ack_requeue_true` en el Testcontainer), logs de ambos servicios a nivel TRACE. Esperado: 0.
6. **Control negativo de la prueba** (que el detector funciona): insertar a propósito una fila de prueba con `4.6097` en una columna textual de una tabla temporal del contenedor y comprobar que las consultas del paso 2 y el `grep` **sí** la detectan; luego destruir el contenedor.
7. **Llaves**: el base64 de cada llave de test no aparece en ningún volcado, log, imagen (`docker history`, `docker save | grep`) ni en el repo (CF-11, DB-05, SG-14).

## P6. Pruebas de seguridad transversal (SG)
| ID | Descripción | Datos / pasos | Esperado | Nivel | Criterio |
|---|---|---|---|---|---|
| SG-01 | Público sin token | `GET /api/publica/fichas` por gateway sin `Authorization` | 200 | SEG | HU-06.2 |
| SG-02 | JWT válido en ruta pública | Con token de `cientifico1` | Cuerpo idéntico al anónimo | SEG | HU-06.3 |
| SG-03 | Parámetros "mágicos" | `?incluirCoordenadas=true&debug=1&rol=cientifico_alto_rango&admin=true` y cabeceras `X-Roles`, `X-User` | Sin efecto | SEG | HU-06.3 |
| SG-04 | Mapa sin token por gateway y directo (8081) | `GET` | 401 en ambos | SEG | HU-07.1 |
| SG-05 | Token vencido por gateway y directo | `exp` + 61 s | 401 | SEG | HU-07.1 |
| SG-06 | Firma alterada, `alg:none`, payload alterado | | 401 | SEG | SE-02..04 |
| SG-07 | Token de otro realm | Realm `master` | 401 | SEG | SE-08 |
| SG-08 | Sin `aud=biohub-api` | | 401 | SEG | SE-09 |
| SG-09 | Interna no expuesta como pública | `GET /api/publica/ubicacion/hallazgos`, `/api/publica/ubicacion/mapa` sin token | 404 (no existe); la ruta `/api/cientifico/ubicacion/**` sigue exigiendo JWT | SEG | HU-13.1 |
| SG-10 | CORS | `Origin` de web-publica y web-interna permitido; `evil.example`, `null` | Cabecera `Access-Control-Allow-Origin` solo para permitidos; 403 para el resto | SEG | RNF / 4.2 |
| SG-11 | XSS reflejado | `q=<script>alert(1)</script>` | `Content-Type: application/json`, `X-Content-Type-Options: nosniff`; no se refleja como HTML | SEG | OWASP |
| SG-12 | Cabeceras de respuesta | Todas | Sin `Server` con versión, sin `X-Powered-By`; `Cache-Control` según `04` §3 | SEG | hardening |
| SG-13 | Recorrido de directorios | `/api/publica/fichas/%2e%2e/%2e%2e/actuator/env`, `/api/cientifico/ubicacion/%2e%2e/…` | 4xx del gateway; no sale del prefijo | SEG | SE-20 |
| SG-14 | Secretos | `git grep`, `.env.example`, `docker history`, `/actuator/env` y `/actuator/configprops` | Sin llaves; actuator solo `health,info` | SEG | HU-UBI-02 |
| SG-15 | Aislamiento de BD | Usuario `cientifico_app` sobre `consulta_publica.*` y usuario de lectura sobre `cientifico.*` | Ambos ERROR 1142 | SEG | HU-06.1 |
| SG-16 | Acceso directo a servicios internos | `8081`/`8083` desde fuera de loopback (en local: `ss -ltn`) | Solo loopback | SEG | IN-12 |
| SG-17 | Rate limit `/api/publica/**` **[BLOQ]** | 130 peticiones en un minuto desde una IP | Tras 120: 429 `LIMITE_EXCEDIDO` con `Retry-After`; cuerpo problem+json; el límite se libera al pasar la ventana | SEG | HU-08.J |
| SG-18 | Rate limit detrás de proxy **[BLOQ]** | `X-Forwarded-For` falsificado | No elude el límite (solo se confía en el proxy propio) | SEG | R5 |
| SG-19 | Rate limit no aplica a `/api/cientifico/ubicacion/**` con JWT válido **[BLOQ]** | 130 peticiones | Sin 429 inesperado (o límite documentado) | SEG | `04` §8 |

## P7. Pruebas de carga x60 (RNF1 / RNF5 / HU-13)

### P7.1 Herramienta y entorno
- `pruebas-carga/README.md` (leído): carpeta **vacía de scripts**; recomienda **k6** o JMeter, objetivo el gateway `http://localhost:8080`, tokens leídos de variables de entorno (duran 5 min), solo entorno local o de pruebas. `03` J13 fija **k6 en `pruebas-carga/`**, contra un Compose efímero `-p biohub-carga`. Se usa **k6** (script `pruebas-carga/consulta-publica-x60.js`, a escribir en la fase de ejecución).
- **Estado del entorno de QA:** `k6` no está instalado en la máquina (`which k6` vacío). Para ejecutar hay que instalarlo (binario oficial o `docker run grafana/k6`); se pide autorización antes de instalar nada. Playwright: solo existe Chromium en caché.
- Entorno: Compose efímero con MySQL lectura, Redis, RabbitMQ, `servicio-consulta-publica` y `api-gateway`; para el caso científico simultáneo se añade MySQL transaccional y `servicio-cientifico`. Límites de recursos del contenedor iguales a los del despliegue (`deploy/backend/docker-compose.yml`) para que el resultado sea representativo; si no, se anota la diferencia en el reporte.
- **Siembra:** 10.000 fichas (20 % `EN_PELIGRO`) publicadas como eventos al broker efímero o con el generador de `05` §13 en el perfil de carga; 10.000 hallazgos cifrados para la búsqueda interna.
- **Objetivo de las pruebas de RNF1/RNF5:** `servicio-consulta-publica` directo (puerto del Compose efímero), porque con el rate limit propuesto (120/min por IP) todas las peticiones salen de la misma IP y el gateway devolvería 429 casi siempre, lo que mediría el limitador y no el pico (riesgo RQ-6). Se añade CG-11 contra el gateway solo para documentar el comportamiento del limitador.

### P7.2 Cómo medir la línea base N
N es "el promedio habitual", que no existe como dato real; se define operativamente (supuesto S2 de `01`):
1. Escenario `base`: 10 usuarios virtuales con pausa aleatoria de 1-3 s, mezcla 60 % `GET ficha` (distribución Zipf sobre 500 ids), 30 % `GET listado` (20 consultas distintas) y 10 % `GET ficha` inexistente (404), durante 5 min tras 1 min de calentamiento.
2. **N = peticiones/s promedio del tramo estable (req/s)** reportado por k6 (`http_reqs` rate), junto con p50/p95 y tasa de aciertos de caché. Se repite 3 veces; N = mediana. Se registra N en el reporte (si N < 3 req/s se sube el número de VUs, para que 60N sea significativo).
3. Pico = **60 x N** req/s durante **2 min** con el ejecutor `constant-arrival-rate` (tasa fija, no VUs fijos, para evitar el efecto "coordinated omission"), `preAllocatedVUs` suficientes y `maxVUs` alto; sin pausa.

### P7.3 Casos
| ID | Descripción | Carga | Umbral / esperado | Nivel | Criterio |
|---|---|---|---|---|---|
| CG-01 | Línea base | P7.2, 5 min | Registra N, p50, p95 (<= 500 ms con caché caliente), errores 0 % | CAR | RNF1/S2 |
| CG-02 | RNF5 en frío | 10.000 fichas, caché vacía (`FLUSHALL` en el Redis efímero), 100 búsquedas **distintas** secuenciales y luego 100 con 10 VUs | p95 <= 2 s; 0 5xx | CAR | RNF5 |
| CG-03 | Pico x60 sobre una misma ficha | 60N req/s, 2 min, una ficha, caché fría al inicio | >= 95 % respuestas 2xx (objetivo: 100 %), **0 5xx**, p95 <= 2 s; consultas SQL a la ficha ~1 por expiración (estampida evitada) | CAR | RNF1 |
| CG-04 | Con carga científica simultánea | CG-03 + publicación continua de eventos (50/s) + registro/búsqueda interna en `servicio-cientifico` (5 VUs, alto rango) | p95 público <= 2 s; DLQ vacía | CAR | RNF5/ASR4 |
| CG-05 | Estabilización | Tras CG-03, bajar a 1N | p95 <= 2 s y 0 errores en ventanas de 10 s consecutivas en < 5 min medidos desde el fin del pico (y desde el inicio del pico, el tiempo hasta que la ventana de 10 s cumple p95 <= 2 s por primera vez) | CAR | RNF1 |
| CG-06 | Pico con Redis caído | CG-03 y a los 30 s `docker stop redis`; a los 90 s `start` | >= 95 % 2xx; sin 5xx; p95 puede subir pero <= 2 s con modo normal; mide el tiempo de recuperación; si no cumple, se activa el modo degradado (CG-07) | CAR | RNF1 |
| CG-07 | Pico en modo degradado | CG-03 con `CONSULTA_MODO_DEGRADADO=true` y Redis caído | >= 95 % 2xx; `modoDegradado=true`; `q` funciona | CAR | RNF1 |
| CG-08 | Búsqueda interna 10.000 | `-Dgroups=rendimiento`; 100 búsquedas con 2 filtros, `cientifico1` (con descifrado de la página, `size` 20/50/100) y `investigador1` | p95 < 3 s (ambos); descifrados <= `size` | CAR | HU-13.2 |
| CG-09 | Ráfaga de eventos | 1.000 eventos válidos en 10 s mientras se leen fichas a 1N | Drenado < 60 s, DLQ vacía, lecturas sin 5xx, p95 <= 2 s (el `clear` de `busquedas` no hunde el acierto, riesgo R8) | CAR | RNF5 |
| CG-10 | Resistencia | 1N durante 15 min | Sin crecimiento de memoria > 20 %, sin fuga de conexiones Hikari ni de Lettuce | CAR | estabilidad |
| CG-11 | Gateway con rate limit **[BLOQ]** | 130 req/min de una IP; luego 60N desde una IP | 429 con `Retry-After` pasado el límite; las respuestas 429 no se cuentan como "fallo de servidor" pero sí se reportan; documentar que RNF1 se mide sin limitador | CAR | RNF1 / R5 |

**Umbrales de k6 (`options.thresholds`)**: `http_req_failed{scenario:pico}: rate<0.05` (5xx y red), `checks{tipo:2xx}: rate>0.95`, `http_req_duration{scenario:pico}: p(95)<2000`, `http_req_duration{scenario:base}: p(95)<500`, `dropped_iterations: count<1 % de lo planificado` (si k6 no logra generar 60N, el resultado no vale y se repite con más recursos en el generador). El generador de carga corre en otra máquina o con CPU limitada para no competir con el sistema bajo prueba; se anota CPU del generador (<80 %).
**Evidencia:** salida de k6 (`--summary-export`, `--out json`), `docker stats` por muestreo cada 5 s, log del servicio, tamaño de la DLQ. Los scripts leen credenciales solo de variables de entorno.

## P8. Dependencias externas y cómo se prueba mientras tanto
| Dependencia | Casos bloqueados | Qué se hace mientras tanto | Desbloqueo |
|---|---|---|---|
| Eventos reales `hallazgo.*` de Hernando (publicador, `version` creciente, estado, sin coordenadas) | IE-01..IE-04 | Fixtures JSON validados contra el JSON Schema de `04` §7.3; el test publica en RabbitMQ Testcontainers. Se prueba también un "contrato de productor": un test en el lado de Hernando (o QA) que serialice el evento real y lo valide contra el Schema y contra la lista de claves prohibidas | Cuando exista `registro`: ejecutar IE-01..IE-04 en Compose efímero |
| `UbicacionService` invocado por `registro` en la transacción (MANDATORY, rollback) | IE-02, IE-03 | US-10/US-11 con una clase de prueba que simula a `registro`. Se vigila que `registro` no importe otras clases de `ubicacion` (ArchUnit) | idem |
| Rate limit y 429 del gateway, `Retry-After`, CORS de web-publica (Santiago) | SG-17..SG-19, CG-11 | La respuesta 429 se simula con un mock en Playwright (`page.route`) para probar el mensaje de la UI y el botón reintentar (WP-04). Los umbrales de carga se miden directo al servicio | Cuando Santiago publique el limitador |
| `setUserId` en `PublicadorEventos` y `BIOHUB_EVENTOS_VERIFICAR_PUBLICADOR=true` (SEC-D-03) | CP-15 (parte `user_id`) | `MessageProperties` simulado | Cuando Santiago lo active |
| Montaje de `rutasUbicacion` en la navegación de web-interna y `leaflet` en `package.json` (Santiago) | WI-01..WI-14 en navegador real | Pantallas probadas como componentes (WC-01..WC-08, Vitest) y con Playwright sobre un arnés/entrada de desarrollo de la carpeta `src/ubicacion/` con `fetch`/rol simulados | Cuando Santiago monte las rutas |
| Keycloak local con los 5 usuarios (E2E interno) | WI-* con login real | Playwright con `keycloak-js` simulado; un solo flujo con Keycloak real del Compose local (no VMs) cuando esté disponible | idem |
| Variables `UBICACION_CLAVES`/`UBICACION_CLAVE_ACTIVA` en `deploy` (Santiago) | DX-02/DX-03 (humo VM), arranque en VM | Llaves de test en `application-test.yml`; CF-12 prueba el fallo de arranque | Cuando el despliegue las inyecte |
| Despliegue de Juanita en las VMs y autorización de la dueña | DX-01..DX-04 | No se ejecutan | Autorización por escrito |
| k6 no instalado | CG-* | Escribir los scripts y validarlos con `k6 inspect` en cuanto esté; no se declara ningún resultado sin ejecutarlo | Instalar k6 (pedir permiso) |
| Firefox/WebKit/Edge para Playwright (solo hay Chromium) | WP-11, WI-11 | Matriz manual de 4 navegadores x 2 versiones (`02` §8); si se autoriza, `npx playwright install firefox webkit` | Autorización de instalación |

Casos de integración extremo a extremo con Hernando (todos **[BLOQ]** hasta que `registro` exista), en Compose efímero:
| ID | Descripción | Esperado | Nivel | Criterio |
|---|---|---|---|---|
| IE-01 | `POST` de registro real con coordenadas centinela -> fila cifrada -> evento -> ficha pública -> `GET` | Evento sin coordenadas; ficha 200 sin coordenadas; `cientifico1` ve las exactas en el mapa; `investigador1` no | I | HU-UBI-03, HU-06.1 |
| IE-02 | Falla de `registrar` (depto inválido) | 400 de registro; ni hallazgo ni ubicación ni evento (rollback + evento solo tras commit) | I | J4 |
| IE-03 | Reclasificación real | `hallazgo.reclasificado` con `version` > anterior; `UbicacionService.actualizar` y evento con **la misma versión**; ficha actualizada | I | HU-06.5 |
| IE-04 | Contrato de productor | El evento serializado por `PublicadorEventos` (sin `__TypeId__`, routing key = tipo, `message_id` = `id`) valida contra el JSON Schema y no contiene claves prohibidas en ningún nivel | I | D23/D24 |

## P9. E2E en navegador (Playwright)
Herramienta: **Playwright** (viable: Chromium 1148 en caché del equipo; `web-publica` y `web-interna` ya usan Vite/Vitest). Los tests se guardarán en `web-publica/e2e/` y `web-interna/e2e/` con `playwright.config` propio (se avisa a Santiago por el `package.json` de web-interna). Dos modos: **(M)** API simulada con `page.route` (rápido, determinista, para estados y reglas de seguridad de UI) y **(R)** contra el Compose efímero con datos sembrados (un solo flujo de humo por web). Nunca contra las VMs.

### P9.1 web-publica
| ID | Descripción | Datos / pasos | Esperado | Nivel | Criterio |
|---|---|---|---|---|---|
| WP-01 | Estado de carga | (M) respuesta de `/fichas` retrasada 2 s | Indicador de carga visible (`aria-busy`/rol status); desaparece al llegar | E2E | HU-06.6 |
| WP-02 | Resultados | (M) lista de 3 fichas; escribir `atelopus` + Enter | Tarjetas con nombre, familia, departamento, estado; la petición lleva `q=atelopus` y `departamento` como **código** DANE | E2E | HU-06.2 |
| WP-03 | Sin resultados | (M) `content=[]` | Texto "No se encontraron especies" y sugerencia de revisar ortografía | E2E | HU-06.2 |
| WP-04 | Error de red / 429 / 503 | (M) `route.abort()`, 429 y 503 | "No pudimos cargar la información, intenta de nuevo" + botón de reintento; al reintentar con éxito se muestra el resultado; el 429 muestra mensaje comprensible | E2E | HU-06.6 |
| WP-05 | Ficha en peligro: aviso de ubicación protegida | (M) ficha `EN_PELIGRO` Boyacá | `h3` "Ubicación protegida"; texto de `02` §6 completo (incluye "lugar exacto", "caza o al tráfico ilegal", línea de peligro de extinción y la final); departamento y región visibles; ningún texto con "coordenadas", "cifrado" ni "georreferenciación"; no está en tooltip/modal/acordeón; el aviso se ve sin hacer scroll en 360 x 640 o con un único scroll | E2E | HU-06.3, RNF7 |
| WP-06 | Ficha inexistente y errores | (M) 404, 400, 500, 503 en `/especies/:id`; ruta desconocida `/xyz` | Pantalla 404 / mensaje apropiado en español, sin trazas; botón volver a buscar | E2E | HU-06.6 |
| WP-07 | Coordenadas inyectadas | (M) respuesta que trae `latitud:4.6097, longitud:-74.0817, coordenadas:{…}` | El DOM completo (`page.content()`), atributos, `title`, `alt`, `data-*` y `console` no contienen `4.6097`, `74.0817` ni `latitud`/`longitud` (lista blanca de `02` §9.1) | E2E | RNF9, `02` §9.2 |
| WP-08 | Sin credenciales | Recorrer búsqueda y ficha | `localStorage`, `sessionStorage`, cookies vacíos de tokens; ninguna petición con `Authorization`; el bundle (`dist/`) no contiene `eyJ`, `secret`, `apikey`, `X-API-Key` | E2E + SEG | HU-06.6 |
| WP-09 | Solo se habla con el gateway | Registrar todas las peticiones | Todas al origen del gateway/servidor de la app; ninguna a teselas, fuentes, analítica o CDNs | E2E | `02` §9.2 |
| WP-10 | Consola limpia (Chromium) | Flujo completo | 0 `console.error`/`warning` y 0 errores de página (incluye CORS) | E2E | RNF13 |
| WP-11 | Matriz de navegadores | Chrome, Firefox, Edge, Safari: últimas 2 versiones; buscador + ficha; móvil real (Safari iOS, Chrome Android) | Funciona, sin errores de consola, fecha/diseño correctos | USA (manual) | RNF13 |
| WP-12 | Volver sin perder la búsqueda | Buscar, abrir ficha, atrás del navegador | Mismos `q`/filtros/página y resultados (la URL los conserva) | E2E | F4 |
| WP-13 | URL compartible | Abrir `/especies/<uuid>` directo (R) | Carga la ficha sin pasar por el buscador | E2E | D31 |
| WP-14 | Texto malicioso en datos | (M) nombre `<img src=x onerror=alert(1)>`, descripción con `<script>` | Se muestra como texto; no se ejecuta (sin diálogo, sin nodos `img` inyectados) | E2E + SEG | OWASP |
| WP-15 | Accesibilidad | Teclado (Tab/Enter/Esc), foco visible, `axe-core` en P1 y P2, contraste AA, `lang="es"` | 0 violaciones graves/críticas; todo operable con teclado | E2E | WCAG AA (Should) |
| WP-16 | Responsive | 360x640, 768x1024, 1440x900 | Sin scroll horizontal; botones >= 44 px | E2E | `02` §7 |
| WP-17 | Entradas del usuario | `q` de 101 caracteres y de 1 | La UI valida antes de llamar (o muestra el 400 de forma amable) | E2E | HU-06.2 |
| WP-18 | Flujo de humo real (R) | Compose efímero con 45 fichas sembradas por eventos: buscar -> ficha | Resultados y ficha reales; sin coordenadas en red (`Network`) ni DOM | E2E | HU-06.x |

### P9.2 Páginas internas `web-interna/src/ubicacion/`
Casos sobre componentes (Vitest + Testing Library, `fetch` y rol simulados) llevan prefijo WC; los de navegador, WI.
| ID | Descripción | Datos / pasos | Esperado | Nivel | Criterio |
|---|---|---|---|---|---|
| WC-01 | Celda de coordenadas por rol | Mock con coordenadas **incluso para roles sin permiso** | Con `investigador_campo`, `auditor_cientifico`, `analista_ambiental`, `administrador`: celda "Restringido" y el DOM no contiene los centinelas; con `cientifico_alto_rango`: valores | U | HU-13.3, `02` §9.2.4 |
| WC-02 | Mapa no llama sin el rol | Rol `investigador_campo` abre `/ubicacion/mapa` | Sin `fetch` a `/mapa`; mensaje "No tienes permiso para ver esta información" | U | HU-07.2 |
| WC-03 | 403 en mapa | Rol alto rango pero API responde 403 | Mensaje de acceso denegado; 0 marcadores; estado limpio | U | HU-07.2 |
| WC-04 | Conversión `[lon,lat]` -> Leaflet `[lat,lon]` | Feature con `[-73.1234567, 5.1234567]` | Marcador en `[5.1234567, -73.1234567]` (un único punto de conversión) | U | `04` §5.2 |
| WC-05 | Truncado y omitidos | `truncado:true`, `omitidos:2`; en búsqueda `estadoCoordenadas="NO_DISPONIBLES"` | Aviso para afinar filtros; aviso de omitidos; celda "No disponible" | U | HU-07.2 |
| WC-06 | Coordenadas no persisten | Tras render, cambiar de ruta y recibir 401/403 | `localStorage`, `sessionStorage`, URL y `console.*` sin coordenadas; estado descartado al desmontar/401/403 | U | `03` J2 |
| WC-07 | Validaciones de formulario | `desde > hasta`, especie de 1 y 101 caracteres, `size` fuera de rango | Mensaje en español; no se llama a la API | U | HU-13.1 |
| WC-08 | Adaptación de campos | Respuesta con campos extra | La UI ignora lo no listado; no rompe con `latitud` ausente | U | `02` §9 |
| WI-01 | Búsqueda alto rango | (M) `cientifico1`, filtros especie+fechas | Tabla con columna de coordenadas con valores; paginación | E2E | HU-13.3 |
| WI-02 | Búsqueda sin permiso | (M) `investigador1`; la respuesta trae por error coordenadas | Columna "Restringido"; DOM sin centinelas | E2E | HU-13.3 |
| WI-03 | Sin resultados | (M) vacío | "No hay hallazgos que coincidan con los filtros" | E2E | HU-13.3 |
| WI-04 | Sesión vencida | (M) 401 | Redirige al login (flujo de Santiago) sin dejar datos previos en pantalla | E2E | HU-13.3 |
| WI-05 | Mapa con marcadores | (M) 3 features | 3 marcadores; al pulsar: especie, fecha, coordenadas; zoom máximo 12; teselas solo de OSM | E2E | HU-07.2 |
| WI-06 | Mapa sin permiso | (M) `investigador1` abre `/ubicacion/mapa` | Pantalla "No tienes permiso…"; 0 peticiones a `/mapa` (verificado en red) | E2E | HU-07.2 |
| WI-07 | Mapa vacío | (M) `features=[]` | "No hay hallazgos para los filtros seleccionados" | E2E | HU-07.2 |
| WI-08 | Truncado | (M) `truncado:true,total:1500` | Aviso de afinar filtros | E2E | HU-07.2 |
| WI-09 | Saltar de búsqueda a mapa | (M) alto rango pulsa "Ver en el mapa" | Navega con `hallazgoId` y centra 1 marcador | E2E | F7 |
| WI-10 | Coordenadas fuera de almacenamiento | Recorrer búsqueda y mapa | Ni storage, ni URL, ni consola con centinelas | E2E + SEG | `03` J2 |
| WI-11 | Navegadores | Matriz manual 4 navegadores | Funciona sin errores | USA (manual) | RNF13 |
| WI-12 | Error 5xx/red en búsqueda y mapa | (M) 500, 503, abort | Mensaje en español con reintento; sin trazas | E2E | UX |
| WI-13 | Login real local con Keycloak (R) **[BLOQ: montaje de Santiago]** | `cientifico1` y `investigador1` en Compose local | Mapa accesible solo al primero; el segundo ve I3 | E2E | HU-07.2/HU-10 |
| WI-14 | Teselas en nivel alto | Acercar al máximo | No se piden teselas de nivel > 12 (riesgo R6) | E2E | `03` R6 |

## P10. Pruebas de usabilidad
| ID | Descripción | Datos / pasos | Esperado | Nivel | Criterio |
|---|---|---|---|---|---|
| UX-01 | **RNF7**: comprensión de ubicación protegida | Protocolo de `02` §10 (resumen abajo) | >= 90 % de respuestas correctas sin ayuda | USA | RNF7 |
| UX-02 | Cobertura de dispositivos | Mitad de los participantes en teléfono, mitad en escritorio | Sin diferencia > 10 puntos entre grupos | USA | RNF7 |
| UX-03 | Iteración si falla | Si < 90 % (y >= 80 %): ajustar texto/posición (`02` §6) y repetir con otros 5 | Se registra cada versión del texto y su resultado | USA | RNF7 |
| UX-04 | RNF6: llegar a una ficha desde el inicio en <= 3 pasos | Contar pasos de 10 sesiones | Mediana <= 3; error <= 5 % | USA | RNF6 |
| UX-05 | Revisión de textos y accesibilidad manual | Lista WCAG AA básica + lector de pantalla (NVDA/VoiceOver) sobre P1/P2/I1/I2 | Textos en español, tuteo, sin jerga ("coordenadas", "cifrado") en la web pública | USA | Should |

**Protocolo RNF7 (resumen de `02` §10):** 5 a 10 participantes (idealmente 10) sin relación con el proyecto, ni ver la ficha antes; teléfono y escritorio. Tarea: "Busca la especie *Atelopus ebenoides* y abre su ficha. Dime dónde se encontró." Pregunta posterior, sin ayuda ni pistas: "¿Por qué crees que no ves el punto exacto en el mapa o las coordenadas?". **Correcta:** menciona proteger la especie, evitar que la cacen/capturen/dañen, tráfico ilegal o que está en peligro. **Incorrecta:** "falla del sistema", "no cargó", "no tienen el dato", "no sé". Éxito: >= 90 % de correctas (con 10 participantes, hasta 1 error; con 5, **ninguno**: 4/5 = 80 % no cumple). Se registran edad aproximada, dispositivo, tiempo, respuesta literal y quién clasificó (dos evaluadores independientes; desempate por QA). No se les dan credenciales ni datos reales; usan el entorno efímero o un build local con datos de prueba. Los resultados se guardan sin datos personales.

## P11. Humo contra el entorno desplegado (única excepción, con autorización)
Solo lectura y negativos que no persisten datos; ver P1 punto 3. Requiere que Juanita esté desplegada (perfil `aiven`, ver 09-despliegue.md) y que la dueña autorice.
| ID | Descripción | Pasos | Esperado | Nivel | Criterio |
|---|---|---|---|---|---|
| DX-01 | Estado inicial | Registrar (sin imprimir secretos) conteos de `ficha_publica`, `ubicacion_hallazgo`, mensajes en `consulta-publica.eventos` y `.dlq`, claves `biohub:publica:*` | Conteos guardados en archivo temporal fuera del repo | E2E | regla de datos |
| DX-02 | Lectura pública | `GET /api/publica/fichas?size=1` y `?q=qa_tmp_noexiste` (anónimo); `GET /api/publica/fichas/00000000-0000-4000-8000-000000000000` | 200 / 200 vacío / 404 problem+json; sin coordenadas; `Cache-Control` correcto | E2E + SEG | HU-06.2 |
| DX-03 | Negativos internos | `mapa` sin token (401); con `investigador1` (403, sin coordenadas); `hallazgos` con `investigador1` (200, sin claves de coordenadas); `recifrado` con `investigador1` (403) **sin llamarlo con admin** | Códigos esperados | E2E + SEG | HU-07.1 |
| DX-04 | Limpieza y verificación | Repetir los conteos de DX-01 | **Idénticos** a los iniciales; si no, FALLO y aviso a la dueña. Se borra el archivo temporal. | E2E | regla de datos |

## P12. Resumen cuantitativo y riesgos de calidad

### Resumen cuantitativo
Total: **259 casos** (identificados por ID; un caso con dos niveles se cuenta en cada uno). Aún **no se ha ejecutado ninguno**.
| Nivel | Casos | Grupos principales |
|---|---|---|
| Unitario (U) | 30 (25 solo U + 5 U/I) | CF (10), US (4), CP (8 con U/I), WC (8) |
| Integración (I) | 137 (132 solo I + 5 U/I) | CF, US, DB, HB, MP, RC, CP, PB, CH, IE |
| Seguridad (SEG) | 50 (45 solo SEG + 5 E2E/SEG) | SG (19), PB (7), MP (6), DB (4), HB (3), LG (3), CH/CF/RC (1 c/u), WP/WI/DX |
| E2E navegador | 34 (29 solo E2E + 5 E2E/SEG) | WP (17), WI (13), DX (4) |
| Carga / rendimiento (CAR) | 11 | CG-01..CG-11 |
| Usabilidad (USA) | 7 (5 + 2 matrices manuales de navegadores) | UX-01..UX-05, WP-11, WI-11 |

Casos por área: cifrado y llaves CF 22 (incl. CF-09b), `UbicacionService` US 15, cifrado en reposo DB 8, logs LG 3, búsqueda interna HB 28, mapa MP 23, re-cifrado RC 6, eventos CP 25, públicos PB 29, caché CH 17, seguridad SG 19, integración con Hernando IE 4, carga CG 11, web-publica WP 18, componentes internos WC 8, web-interna WI 14, usabilidad UX 5, humo VMs DX 4.

### Casos bloqueados o condicionados por dependencias
| Estado | Casos | Motivo |
|---|---|---|
| Bloqueados (firme) | IE-01..IE-04 (4) | Falta `registro` y su publicador de eventos (Hernando) |
| Bloqueados (firme) | SG-17, SG-18, SG-19, CG-11 (4) | Falta rate limit/429 y confianza en `X-Forwarded-For` (Santiago) |
| Bloqueado parcialmente | CP-15 (1) | Falta `setUserId` en el publicador (SEC-D-03); se prueba con propiedades simuladas |
| Bloqueado | WI-13 (1) | Falta el montaje de `rutasUbicacion` y Keycloak local de Santiago |
| Requieren autorización de la dueña y despliegue | DX-01..DX-04 (4) | Única excepción contra las VMs |
| Condicionados por herramienta | CG-01..CG-10 (10), WP-11/WI-11 (Firefox/WebKit/Edge) | k6 no instalado; solo Chromium en Playwright |
| Con alternativa mientras tanto | WI-01..WI-12, WI-14 (13) | Se cubren con WC-01..WC-08 y arnés de desarrollo hasta que Santiago monte las rutas |
Total bloqueados o condicionados: **14 firmes/autorización + 10 de carga por herramienta + 13 de web-interna con alternativa**.

### Riesgos de calidad detectados en el diseño
| # | Riesgo | Por qué importa | Mitigación / caso |
|---|---|---|---|
| RQ-1 | Documentos con criterios contradictorios: `01` §3 sigue describiendo columnas en claro y coordenadas públicas de especies no amenazadas; `05` §7.2 y `05` §10 fueron corregidos solo en parte (P-J1, P-J3) | Un desarrollador o tester puede implementar/validar el criterio viejo | P0 lista qué se anula; pedir al tech-lead que marque como "anulado" los escenarios en `01` |
| RQ-2 | Fuga por logs a nivel TRACE/DEBUG (parámetros enlazados de Hibernate, `Authorization`, `toString` de DTOs de Hernando) | Rompe RNF9 sin tocar la BD | LG-01, LG-02 con TRACE; revisar los DTOs de `registro` |
| RQ-3 | Idempotencia solo por `version`: una corrección de datos con la misma versión se pierde en silencio, y un `hallazgo.*` falsificado con versión alta sobrescribe la ficha pública (sin verificación de `user_id` hasta SEC-D-03) | Datos públicos incorrectos o manipulados | CP-12, CP-15; confirmar con Hernando que cada cambio sube `version` |
| RQ-4 | Desacuerdo `UbicacionService` vs. evento: ambos deben llevar la misma `version`; sin outbox, un fallo entre commit y publicación deja la ficha vieja (R2) | Portal desactualizado tras una reclasificación | IE-03; prueba de "evento perdido" (matar el broker en el commit) |
| RQ-5 | Inconsistencia del límite de paginación: `04` dice `(page+1)*size <= 10.000`, `03` J7 dice `page*size > 10.000` | Off-by-one que da 200 vs 400 | PB-09, HB-21 fijan la regla de `04`; avisar al backend |
| RQ-6 | La carga x60 contra el gateway medirá el limitador (una sola IP, 120/min), no el pico; además k6 puede ser el cuello de botella | Resultado falso de RNF1 | P7.1: medir directo al servicio y gateway por separado; vigilar `dropped_iterations` |
| RQ-7 | N (el "promedio habitual") no existe como dato; el pico 60N depende de cómo se defina N | RNF1 se vuelve discutible | P7.2 define N operativamente y lo declara en el reporte; validar con Juanita |
| RQ-8 | `q` compuesto solo de operadores o de palabras < 3 letras cae a `LIKE 'q%'`; escape incorrecto de `%`/`_`/`\` | Devolver toda la tabla (DoS) o error 500 | PB-11, PB-22 |
| RQ-9 | Vaciado de `busquedas` en cada evento (R8): una ráfaga de registros durante el pico reduce el acierto de caché y manda tráfico a MySQL | Puede romper RNF5 justo cuando hay mucha carga | CG-09, CG-04 |
| RQ-10 | Pérdida de la llave = pérdida irrecuperable de las coordenadas; retirar una versión con filas pendientes | Pérdida de datos científicos | CF-15, CF-21, RC-03; pedir procedimiento de custodia y prueba de restauración (backup + llave) |
| RQ-11 | Un rol revocado sigue vigente hasta 5 min (R4); `administrador` y el propio investigador no ven coordenadas por regla de negocio, pero el `administrador` puede lanzar re-cifrado (el servicio descifra internamente) | Percepción de acceso indebido | MP-10 documenta el límite; RC-04 verifica que la respuesta no contiene coordenadas |
| RQ-12 | Pruebas con Testcontainers requieren Docker en el agente de CI; Playwright solo tiene Chromium; k6 no instalado | Cobertura incompleta o pruebas omitidas en silencio | Declarar en el reporte qué se omitió (nunca marcar "aprobado" lo que no corrió) |
| RQ-13 | Sin endpoint de borrado, los datos de prueba en las VMs no se pueden limpiar por API | Contaminación del portal público real | P1.3: humo limitado a lectura/negativos; los positivos solo en entornos efímeros |
| RQ-14 | Teselas OSM revelan al proveedor la zona consultada (R6) y `web-interna` cargará un recurso externo | Fuga de intención de búsqueda | WI-14 y revisión de la política CSP con Santiago |

## P13. Actualización QA 15h (2026-11-04)
WP-14 (texto malicioso en datos) y WP-08 (sin tokens ni almacenamiento, sin `Authorization`) quedaron automatizados en `web-publica/e2e/portal.spec.js` (bloque "QA 15h"; Chromium y Firefox). Siguen sin automatizar: CF-06, WP-15, WP-16, WP-18, CG-02/06/07/08/09/10 y parcialmente LG-01/02, WP-10/12/13. Detalle y resultados en `07-reporte-qa.md`, iteración 15h.
