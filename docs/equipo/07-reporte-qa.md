# 07 – Reporte de QA del esqueleto

Fecha: 2026-10-21. Entorno: Compose con 7 contenedores, servicios Java desde sus jars (8080-8084), Vite en 5173/5174, Keycloak 26.8.1. No se modificó código ni configuración; solo se escribieron este reporte y `06-plan-pruebas.md`. Los IDs de casos son los del plan.

## Resumen
| Concepto | Cifra |
|---|---|
| Casos del plan | 69 |
| Pasa | 66 |
| Falla | 1 (SE-20) |
| No ejecutado | 2 (IG-10, IN-13) |
| `mvn -B package` | BUILD SUCCESS; 17 tests, 0 fallos, 0 errores (gateway 9 = 4 ProxyServiceTest + 5 GatewaySecurityTest; cientifico 2, equipos 2, consulta-publica 2, reportes 2) |
| `npm test` web-interna / web-publica | 2/2 y 2/2 (vitest 5.0.5) |
| `npm run build` ambas webs | OK (JS 248 kB y 220 kB sin comprimir) |
| `npm audit --omit=dev` | 0 vulnerabilidades en ambas |
| Cobertura de código | No disponible (no hay JaCoCo ni `vitest --coverage` configurado) |

Nota: `mvn package` reescribió los jars de `target/` mientras los servicios corrían desde ellos; tras eso los 5 `/actuator/health` siguieron en 200.

## Resultado por caso
### Unitarias / componentes
UC-01 a UC-11: **Pasa** (cifras arriba).

### Integración vía gateway
| ID | Resultado | Evidencia |
|---|---|---|
| IG-01 | Pasa | 25/25 respuestas 200; usuario y rol coinciden con el token (ej. `auditor1 -> ["auditor_cientifico"]`) |
| IG-02 | Pasa | `{"servicio":"servicio-consulta-publica","usuario":"anonimo","roles":[]...}` 200 |
| IG-03 | Pasa | Solo un rol de negocio por usuario; `fecha` ISO con Z |
| IG-04 | Pasa | 200 con query con `%20`, `%26`, `%C3%B1` y con `?` vacío |
| IG-05 | Pasa | POST JSON a `/api/cientifico/hallazgos` -> 404 del servicio; a `/ping` -> 405 del servicio |
| IG-06 | Pasa | PUT/DELETE/POST sin cuerpo/5 MB/chunked -> 404 del servicio (con 100-continue en el de 5 MB) |
| IG-07 | Pasa | `/api/inexistente/ping` y `/api/` -> 404 (cuerpo vacío) |
| IG-08 | Pasa | 405 y 404 JSON del servicio llegan intactos |
| IG-09 | Pasa | 200 `{"status":"UP","groups":["liveness","readiness"]}` en gateway y servicios |
| IG-10 | **No ejecutado** | Exige detener un servicio y se pidió no detener nada. Solo cubierto en código (`GatewayExceptionHandler`) y sin prueba unitaria del 502 |
| IG-11 | Pasa | `/ping/` y `/CIENTIFICO/ping` -> 404 |
| IG-12 | Pasa (limitado) | 200; no se pudo observar qué cabecera recibe el servicio |

### Seguridad
| ID | Resultado | Evidencia |
|---|---|---|
| SE-01 | Pasa | 401 en las 4 rutas |
| SE-02, SE-03, SE-04, SE-05, SE-06 | Pasa | 401 en todos (firma, payload, `alg:none`, basura, Basic, `Bearer ` vacío) |
| SE-07 | Pasa | Token admin1 con `exp`=1792625222: 200 a las 23:23:50; a las 23:28:15 (>exp+60 s) 401 en gateway (`gateway`, `cientifico`, `publica`) y directo a 8081 y 8083 |
| SE-08 | Pasa | Token `master`/`admin-cli`: 401 en las 5 rutas y en 8081 directo |
| SE-09 | Pasa | Token `admin-cli` del realm biohub (`aud` ausente): 401 en gateway y 8081; la validación de audiencia funciona |
| SE-10 | Pasa | `/api/publica/ping` con token basura o con firma mala -> 401 (no cae a anónimo) |
| SE-11 | Pasa | POST/PUT/PATCH/DELETE sin token -> 401 |
| SE-12 | Pasa | POST con token -> 405 |
| SE-13 | Pasa | HEAD sin token -> 401 (ver observación O-3) |
| SE-14 | Pasa | `Access-Control-Allow-Origin` igual al origen para 5173 y 5174 |
| SE-15 | Pasa | 403 "Invalid CORS request" para `evil.example`, `null`, `127.0.0.1:5173` (también sin token y en `/api/publica`) |
| SE-16 | Pasa | Preflight permitido 200 con Allow-Methods/Max-Age 3600; origen malo y `x-evil` -> 403 |
| SE-17 | Pasa | 8081, 8082, 8084: sin token 401, firma mala 401, token válido 200 |
| SE-18 | Pasa | 8083: sin token 200 anónimo; `/api/otra` sin token 401; firma mala 401 |
| SE-19 | Pasa | 401 |
| SE-20 | **Falla** | Ver BUG-01 |
| SE-21 | Pasa | `%zz`, `%00`, URL de 9000 caracteres -> 400 (HTML de Tomcat, ver BUG-02), sin 5xx |
| SE-22 | Pasa | `ñ` codificada 404; `;jsessionid` 401; `//api/...` 401; `/api//cientifico/ping` 401; sin 5xx |
| SE-23 | Pasa | `/actuator/env`: 401 sin token; 404 con token (no expuesto) |
| SE-24 | Pasa | `web-interna` password grant: 400 `unauthorized_client` |
| SE-25 | Pasa | `invalid_grant`; `client_credentials`: 401 `unauthorized_client` |
| SE-26 | Pasa | `.env` modo 600; `.env` en `.gitignore` |

### Infraestructura
| ID | Resultado | Evidencia |
|---|---|---|
| IN-01 | Pasa | 7/7 `healthy` (keycloak reporta "Up 9 minutes" frente a 14 de los demás: se reinició o recreó antes de mi sesión; sin impacto) |
| IN-02 | Pasa | version 1 `base` `success=1` en los 5 esquemas |
| IN-03 | Pasa | transaccional: cientifico+equipos; lectura: consulta_publica; analitica: reportes; gateway: gateway |
| IN-04 | Pasa | `cientifico_app` -> `equipos`: ERROR 1142 SELECT denied |
| IN-05 | Pasa | `equipos_app` -> `cientifico`: ERROR 1142 SELECT denied |
| IN-06 | Pasa | `GRANT ALL PRIVILEGES ON <su esquema>.*` y `USAGE ON *.*` |
| IN-07 | Pasa | `NOAUTH Authentication required` y `WRONGPASS` |
| IN-08 | Pasa | `PONG` |
| IN-09 | Pasa | 200 en `/api/overview` |
| IN-10 | Pasa | 401 sin y con credenciales malas |
| IN-11 | Pasa | `biohub.eventos`, topic, durable; 3 conexiones AMQP abiertas (servicios) |
| IN-12 | Pasa | Todos en 127.0.0.1 |
| IN-13 | **No ejecutado** | No existe aún endpoint que use la caché Redis desde servicio-consulta-publica |

### E2E navegador
E2E-01 a E2E-07: **Pasa**. Con investigador1 y con admin1: web-pública conectada; redirección a Keycloak con `S256`/`web-interna`; ping muestra usuario y rol correctos; sin token en storage; logout vuelve al login de Keycloak; consola sin errores.

## Bugs
Ninguno es Crítico ni Alto.

### BUG-01 (Medio) – Recorrido de directorios codificado escapa del prefijo autorizado en el gateway
- Responsable: backend (api-gateway). Archivo: `api-gateway/src/main/java/co/edu/unbosque/biohub/gateway/service/ProxyService.java:51-59` (usa `request.getRequestURI()` crudo y `build(true)` sin normalizar) y `resolverDestino` (:40-48).
- Reproducir: `GET http://localhost:8080/api/cientifico/%2e%2e/%2e%2e/actuator/health` con token válido; y sin token `GET http://localhost:8080/api/publica/%2e%2e/%2e%2e/actuator/health`.
- Esperado: el gateway rechaza (400/404). Obtenido: 200 `{"status":"UP",...}`. El gateway autoriza y enruta según la ruta cruda, pero Tomcat del servicio decodifica y normaliza a `/actuator/health`. La ruta pública llegó sin autenticación a una ruta no pública del servicio (aquí solo `health`, que ya es público; el riesgo es futuro: reglas por prefijo/rol en el gateway o rutas públicas del servicio se pueden saltar).
- Evidencia: salida de curl indicada (200). Con `/api/cientifico/%2e%2e/%2e%2e/actuator/env` el servicio respondió 404 con `"path":"/actuator/env"`, confirmando que la ruta efectiva salió de `/api/cientifico`.
- Corrección sugerida: en `ProxyService.reenviar` rechazar con 400 si la ruta (cruda o decodificada) contiene `..`, `%2e`, `%2f`, `%5c`, `;` o `\`; o normalizar con `URI.normalize()` y re-verificar que el resultado empieza por `/api/<prefijo>/`. Añadir prueba en `ProxyServiceTest`.

### BUG-02 (Bajo) – Formato de error inconsistente
- Responsable: backend (api-gateway). Archivos: `SecurityConfig.java` (401 sin cuerpo), `ProxyService.java:54` (`notFound().build()` sin cuerpo), Tomcat.
- Reproducir: `GET /api/cientifico/%zz` con token -> 400 con HTML de Tomcat; `GET /api/inexistente/ping` -> 404 sin cuerpo mientras `/api/cientifico/noexiste` -> 404 JSON `{"timestamp",...}`; origen no permitido -> texto plano "Invalid CORS request"; 401 sin cuerpo.
- Esperado: errores en un formato único (p. ej. `application/problem+json` como el 502 del contrato). Obtenido: cuatro formatos distintos. El contrato no los define, pero la web los tratará igual.
- Corrección sugerida: `ProblemDetail` para 404 de prefijo y un `AuthenticationEntryPoint`/`AccessDeniedHandler` JSON; documentar en el contrato.

### BUG-03 (Bajo) – Cabecera `Vary` duplicada en respuestas proxeadas
- Responsable: backend. Archivo: `ProxyService.java:73` (excluye `access-control-*` pero no `Vary`).
- Reproducir: `curl -i -H "Authorization: Bearer <t>" -X POST localhost:8080/api/equipos/algo`: aparecen `Vary:` y `vary:` repetidas (3 + 3 valores).
- Corrección sugerida: no copiar `vary` del servicio, o fusionar valores.

## Observaciones (no son bugs del esqueleto)
- O-1: `admin-cli` del realm `biohub` (cliente por defecto de Keycloak) permite password grant y emite tokens para usuarios del realm. Sus tokens no tienen `aud=biohub-api`, así que la API los rechaza (SE-09), pero conviene deshabilitar `directAccessGrantsEnabled` en ese cliente o dejar solo `biohub-pruebas`. Solo desarrollo.
- O-2: El preflight CORS de `/api/publica/**` anuncia POST/PUT/PATCH/DELETE (`SecurityConfig.java:61,66`) aunque el prefijo es solo GET; el servidor igualmente devuelve 401/405. Considerar CORS más estricto por prefijo cuando se implemente la API pública.
- O-3: HEAD a `/api/publica/**` da 401 (la regla es solo GET). Aceptable por contrato; decidir si se desea soportar HEAD.
- O-4: Spring Security admite 60 s de tolerancia de reloj: un token vencido sigue siendo válido hasta `exp`+60 s.
- O-5: No hay pruebas automáticas del 502 (servicio caído) ni de timeouts del gateway; el caso IG-10 quedó sin verificar en vivo.
- O-6: No hay medición de cobertura.

## Veredicto: APROBADO CON OBSERVACIONES
El esqueleto cumple el contrato: build y tests verdes (17 Java + 4 web), matriz 5x5 de pings correcta, autenticación robusta frente a tokens manipulados, vencidos, de otro realm y sin audiencia, servicios no accesibles sin JWT, CORS correcto, infraestructura sana con Flyway aplicado, aislamiento entre esquemas, Redis con contraseña y E2E con dos usuarios. No se rechaza porque no hay defectos Críticos/Altos y el único fallo (BUG-01, Medio) no expone hoy nada sensible, pero debe corregirse antes de añadir reglas de autorización por ruta o rutas públicas con datos. Quedan sin verificar: 502 con servicio caído (IG-10) y uso de Redis desde la aplicación (IN-13).

## Seguimiento del Tech Lead
| Bug | Resolución |
|---|---|
| BUG-01 | **Falso positivo, pero se reforzó.** Reproducido con `curl -v`: sin `--path-as-is`, curl normaliza `%2e%2e` en el cliente y envía `GET /actuator/health` (público). Con `--path-as-is` el gateway respondía 401 (firewall de Spring). Aun así se agregó validación de rutas no normalizadas en `ProxyService` (400) y `HttpStatusRequestRejectedHandler(400)`. Re-verificado: 400. |
| BUG-02 | **Mejorado**: prefijo inexistente → 404 ProblemDetail; URI inválida → 400 ProblemDetail; cuerpo grande → 413 ProblemDetail. 401 sin cuerpo y 403 CORS en texto quedan como están (Bajo). |
| BUG-03 | **Corregido**: el proxy ya no copia `Vary` del servicio; ahora solo salen los 3 `Vary` distintos de CORS. |
| IG-10 (502 no ejecutado) | Ejecutado por el Tech Lead en la primera verificación: servicio-reportes detenido → 502 ProblemDetail. |

Re-verificación completa tras `docker compose down -v`: 7/7 healthy, Flyway V1 en 5 esquemas, 5 pings 200, negativos OK, E2E navegador OK con investigador1 y admin1.
Tests automatizados: Maven 31 (gateway 23: ProxyServiceTest 18 + GatewaySecurityTest 5; 2 por servicio), web-interna 2, web-publica 2.

---
# Iteración despliegue + RabbitMQ (tarea 13e)
Fecha: 2026-10-22. Rama `feature/despliegue`. Casos en `06-plan-pruebas.md` (secciones 6-9).

## Resumen de ejecución
| Prueba | Resultado |
|---|---|
| `mvn -B verify` (raíz), con Docker | BUILD SUCCESS. **73 tests, 0 fallos, 0 errores, 0 omitidos** |
| api-gateway | 23 |
| servicio-cientifico | 16 (Testcontainers: `PublicadorEventosIntegracionTest` 2) |
| servicio-equipos | 2 |
| servicio-consulta-publica | 16 (Testcontainers: 2 clases IT) |
| servicio-reportes | 16 (Testcontainers: 2 clases IT) |
| `npm test` web-interna / web-publica | 2 / 2 pasan (sin cambios) |
| Tests opt-in contra RabbitMQ con las definitions de despliegue | 2 de 2 pasan |

- Backend-senior reportó 71; la primera corrida de `verify` dio 71 (0 omitidos: los Testcontainers sí se ejecutaron; se vieron arrancar los contenedores `rabbitmq:4-management`). Los 2 adicionales son tests agregados por QA (abajo). No se midió cobertura (sin JaCoCo en el proyecto).
- Tests agregados por QA (solo `src/test`):
  - `servicio-reportes/.../eventos/ConsumidorEventosRechazosIntegracionTest` y `servicio-consulta-publica/.../eventos/ConsumidorEventosRechazosIntegracionTest`: con broker real, un evento cuya routing key (`hallazgo.registrado`) difiere de su `tipo` (`prueba.ping`) y otro de más de 64 KB terminan en la DLQ, la cola principal queda vacía y no se almacena nada. Antes solo estaban cubiertos a nivel unitario.
  - Opt-in (se omiten sin `QA_RMQ_PORT`): `ConsumidorBrokerDesplegadoTest` (reportes) y `PublicadorBrokerDesplegadoTest` (científico), que usan los usuarios `reportes_mq` y `cientifico_mq`.
- Problema del propio test/entorno corregido: mi primera corrida opt-in falló con `ACCESS_REFUSED` porque yo leí la contraseña de la plantilla incluyendo el comentario de fin de línea; no era un defecto del despliegue (con la contraseña limpia pasan).

## Cobertura de criterios del evento
Cubiertos y verdes: POST 202/400/401/403/503; GET recibidos 401/403/200 en ambos consumidores; límite de 20 y orden; DLQ por JSON roto, tipo desconocido, routing key distinta del tipo y >64 KB (con broker real); cabecera `__TypeId__` ignorada (el IT envía una clase inexistente y el evento se consume); mensaje exacto de 200 válido y 201 inválido.
Verificado además contra un RabbitMQ real cargado con el `definitions.json` generado por `generar-definitions.sh` (con contraseñas ficticias, en contenedor local, sin tocar ningún entorno compartido): 4 usuarios (`biohub` administrator, `cientifico_mq`, `consulta_mq`, `reportes_mq`), sin `guest`; `reportes_mq` declara su topología, consume y envía a DLQ; `cientifico_mq` publica con confirmación y recibe `ACCESS_REFUSED` al leer `reportes.eventos`; el evento publicado por `cientifico_mq` llegó a `reportes.eventos` (1 mensaje, cola durable con los argumentos DLX esperados). `consulta_mq` no se probó en vivo (su regla es simétrica a la de `reportes_mq`).

## Artefactos de despliegue (estático)

Sección retirada en la iteración 15 (Tailscale eliminado; ver 09-despliegue.md).

## Bugs
No se encontraron bugs Críticos, Altos ni Medios. Hallazgos menores:

BUG-04 (`.env.example` de `deploy/`): sección retirada en la iteración 15 (Tailscale eliminado; ver 09-despliegue.md).

### Observaciones (para ciberseguridad / Tech Lead)
- O-9: Los tests opt-in dependen de variables de entorno; no hay prueba automática en CI de las definitions reales. Sugerencia: añadir un IT que cargue `definitions.json` generado en Testcontainers.
- O-10: Sin cobertura medida (JaCoCo) ni E2E automatizado del flujo de eventos por el gateway; queda el procedimiento manual (plan, sección 9).

## Veredicto: APROBADO CON OBSERVACIONES
El código de RabbitMQ cumple el contrato (73/73 tests con broker real, 0 omitidos) y los permisos por usuario funcionan contra un RabbitMQ real con las definitions generadas. La verificación de los artefactos de despliegue en VMs fue retirada en la iteración 15 (Tailscale eliminado; ver 09-despliegue.md).

---
# Iteración 15h – verificación final (2026-11-04)
Rama `feature/ubicacion-consulta-publica`, con los cambios sin commit del árbol de trabajo (refactor de patrones en ubicación, degradación automática, verificación del publicador AMQP activa por defecto, `pruebas-carga/`). Todo se ejecutó en esta sesión; no se confió en el reporte de backend.

## 1. Backend (`mvn -o test`, Docker disponible, Testcontainers reales)
| Módulo | Tests | Fallos | Errores | Omitidos | Resultado |
|---|---|---|---|---|---|
| servicio-cientifico | **229** | 0 | 0 | **1** | BUILD SUCCESS (1 min 41 s) |
| servicio-consulta-publica | **190** | 0 | 0 | 0 | BUILD SUCCESS (1 min 37 s) |

- Coinciden exactamente con lo reportado por backend-senior. El omitido es `PublicadorBrokerDesplegadoTest` (opt-in: requiere `QA_RMQ_PORT/USER/PASS`); no es un fallo.
- Corridos con el cambio del Tech Lead ya aplicado (`verificar-publicador:true` por defecto en `ConsumidorEventos`); `PublicadorNoPermitidoIntegracionTest` (4) pasa.
- No se midió cobertura (el proyecto no tiene JaCoCo).

## 2. web-publica (como CI)
| Paso | Resultado |
|---|---|
| `npm run build` | OK (121 módulos) |
| `npm test` (Vitest) | **32/32** (3 archivos) |
| `npm audit --omit=dev --audit-level=high` | 0 vulnerabilidades |
| E2E Playwright, Chromium + Firefox | **28/28** con la suite original; con los 2 tests nuevos de QA (abajo) quedan 15 tests x 2 navegadores = 30 |
| E2E Playwright, WebKit | **No ejecutable en esta máquina**: 14/14 fallan en `browserType.launch: Host system is missing dependencies` (faltan librerías del sistema; `sudo npx playwright install-deps webkit`). Es limitación del entorno, no de la app; CI sí instala `--with-deps`. Se corrió con `E2E_SIN_WEBKIT=1`, que la propia config prevé. RNF13 en Safari queda sin verificar localmente |
| `web-interna` `npm test` (páginas de ubicación, fuera de CI) | 9 pasan, **2 archivos no cargan** (`PaginaBusqueda.test.jsx`, `PaginaMapa.test.jsx`): `@testing-library/user-event` está en `package.json` pero no instalado en este `node_modules`. Es de entorno (falta `npm ci`), no un bug de código; no se corrigió. Nota: CI no corre los tests de web-interna |

Tests agregados por QA (solo `web-publica/e2e/portal.spec.js`, bloque "QA 15h"): **WP-14** (texto malicioso `<img onerror>`, `<script>`, `<svg onload>` en los datos se muestra como texto, no se ejecuta, sin diálogos) y **WP-08** (búsqueda y ficha sin `Authorization`, `localStorage`/`sessionStorage`/cookies vacíos). Ambos pasan en Chromium y Firefox.

## 3. Colección Bruno (viable y ejecutada)
Se reprodujo el job de CI con la infraestructura del compose del repo ya levantada (Keycloak, RabbitMQ, Redis) y un MySQL 8.4 **efímero** en el puerto 3399 (`qa-mysql-bruno`, creado con `infra/ci/init-mysql.sql`; ya eliminado), sin tocar los MySQL de desarrollo. Los dos servicios se arrancaron con los jar recién empaquetados, perfil `local`, llave ficticia de CI y `BIOHUB_EVENTOS_PUBLICADOR_CIENTIFICO` = usuario de RabbitMQ (`rabbitUser` del entorno `local` de Bruno), es decir verificación activa. Se sembró con `infra/ci/SembrarHallazgos.java`.
- `bru run --env local`: **44/44 requests, 77/77 tests, PASS** (10,5 s). Incluye los 4 eventos `setup/` con `user_id`, el caso de coordenadas (el servicio registró el incidente SEC-J-03 y lo descartó, esperado) y recifrado 401/403/200.
- Servicios detenidos y contenedor eliminado al terminar. Con `--env ci` no se corrió (usa el usuario `biohub_ci`, que no existe en este broker); el entorno `local` es el equivalente.
- Aviso menor: pasé `--reporter-junit` a un directorio inexistente y bru devolvió código 2 después de pasar todo; no es un fallo de la colección (CI usa `resultados/`, que se crea).

## 4. `pruebas-carga/`
- Sintaxis de los `.sh` correcta (`bash -n`). Humo ejecutado con `construir-imagen.sh` + `compose-carga.yml` (1 réplica + NGINX) + `datos/sembrar.sh` (2000 fichas) + `correr-k6.sh k6/consulta-publica-pico.js -e N=2 -e MULT=5` con tiempos cortos: 168 peticiones, 0 % no-200, 0 % 5xx, p95 10,6 ms. Los scripts de pico **funcionan y son reproducibles** con esos comandos. Después: `down -v`, `limpiar.sh` (0 fichas) y borrado del JSON de humo. No se interpreta contra el ASR (lo hace el arquitecto).
- Se probó también `carga-cientifica-simultanea.js` (CIENTIFICA=1, 15 s): ver BUG-J2.
- BUG-J3: el README está desactualizado (abajo).

## 5. Bugs
**BUG-J2 – Media – responsable: backend (carga) / Tech Lead.** La prueba `carga-cientifica-simultanea.js` (ASR4/RNF5, CG-04) ya no mide lo que dice: con la verificación del publicador activa por defecto, consulta-publica rechaza todos sus eventos.
- Pasos: `./construir-imagen.sh`; `docker compose -p biohub-carga -f compose-carga.yml --env-file ../.env up -d --scale consulta-publica=1`; `./datos/sembrar.sh`; `./correr-k6.sh k6/carga-cientifica-simultanea.js -e CIENTIFICA=1 -e R=10 -e EV=5 -e DUR=15s`.
- Esperado: los eventos se procesan (SELECT ... FOR UPDATE + INSERT, invalidación de caché) mientras se mide la lectura pública; DLQ vacía (CG-04).
- Obtenido: k6 reporta `eventos=76 ok=100 %` (solo mide que el broker enrutó), pero el servicio registró 228 veces `Evento hallazgo.* con publicador no permitido` y la DLQ `consulta-publica.eventos.dlq` quedó con 76 mensajes. La carga de escritura nunca llega a la BD, así que la medición ASR4 sería optimista y engañosa.
- Causa: la publicación por la API de management (`k6/carga-cientifica-simultanea.js`, ~línea 104) no pone `user_id` en `properties` y `compose-carga.yml` no define `BIOHUB_EVENTOS_PUBLICADOR_CIENTIFICO`. Arreglo: poner `user_id: RMQ_USER` en las propiedades (RabbitMQ solo lo acepta si coincide con el usuario autenticado) y pasar `BIOHUB_EVENTOS_PUBLICADOR_CIENTIFICO: ${RABBITMQ_USER}` al servicio en el compose. Además el script debería comprobar `cola_dlq == 0` y fichas resultantes, no solo `routed`.
- Efecto secundario de mi prueba: dejé 76 mensajes en la DLQ del RabbitMQ de desarrollo; intenté purgarla y el permiso fue denegado, así que **queda pendiente purgar `consulta-publica.eventos.dlq`** (borrar contenido desde la UI de management o `rabbitmqctl purge_queue`).

**BUG-J3 – Bajo – responsable: backend / Tech Lead.** `pruebas-carga/README.md` sigue diciendo "Carpeta reservada... Hoy está vacía: no hay scripts implementados" y no explica construir imagen, compose efímero, siembra, `correr-k6.sh` ni los parámetros. Los scripts no son reproducibles "según su README" porque el README no los describe (sí lo son leyendo las cabeceras de los scripts y del compose). `docs/equipo/trazabilidad.md` (líneas 9 y 111) también afirma que no hay scripts de carga ni colección Bruno.

Observaciones (no bugs):
- O-11: con el `.env` de desarrollo (`RABBITMQ_USER=biohub`), si se arranca consulta-publica sin `BIOHUB_EVENTOS_PUBLICADOR_CIENTIFICO=biohub` (el default es `cientifico_mq`), todo evento de cientifico se rechaza. `.env` no define esa variable ni `.env.example` explica el caso local. Documentarlo en el README de consulta-publica / 09-despliegue.
- O-12: no hay bugs de severidad Crítica o Alta. Sin cobertura medida (JaCoCo).

## 6. Casos del plan 06 (parte de Juanita, P3–P9) sin test automatizado propio
Los tests no citan los IDs del plan (`CF-01`, etc.), así que no existe trazabilidad mecánica; el mapeo se hizo por área y contenido de las clases de test (229 + 190 + 32 + 15 E2E + 44 requests Bruno). Cobertura por área: cifrado/llaves, `UbicacionService`, cifrado en reposo, búsqueda interna, mapa, recifrado, JWT, auditoría, inyección/límites, consumo de eventos, DLQ, búsqueda pública, caché, Redis caído y degradación (automática y forzada) tienen tests. Sin cobertura automatizada o con cobertura solo parcial:
- **CF-06** (UUID en mayúsculas en el AAD): no se encontró test explícito.
- **LG-01/LG-02** (fuga por logs a nivel TRACE/DEBUG con captura de log): cobertura solo parcial (auditoría y "sin filtraciones", no barrido TRACE de Hibernate).
- **CG-01..CG-11** (carga): solo hay k6 para pico x60 (≈CG-03/05), carga científica simultánea (CG-04, afectado por BUG-J2) y registro; sin script para CG-02, CG-06 (Redis caído en pico), CG-07 (degradado en pico), CG-08 (`-Dgroups=rendimiento`: no existe ese grupo), CG-09, CG-10; CG-11 bloqueado por rate limit.
- **WP-15** (axe-core), **WP-16** (responsive), **WP-18** (humo real con compose), **WP-11** (matriz de navegadores incl. móvil real y Edge) y **UX-01..UX-05** (usabilidad, manuales): sin automatizar; WP-11 en WebKit además no ejecutable aquí. WP-10 (consola limpia) y WP-12/WP-13 solo parcialmente (Vitest y `erroresCsp`, no E2E completo).
- Ahora **cubiertos por QA 15h**: WP-14, WP-08.
- Bloqueados por dependencias de otros equipos (sin cambios): IE-01..IE-04, SG-17..SG-19, CG-11, WI-13, DX-01..DX-04 (requieren autorización y despliegue; Tailscale/VMs ya retirado, estos casos están obsoletos).
- Casos de `web-interna` (WI-*, WC-*): 9 tests pasan, 2 archivos no ejecutables por dependencia no instalada (ver §2); no verificados.

## Veredicto 15h: APROBADO CON OBSERVACIONES
Backend 229 + 190 tests verdes (0 fallos, 1 omitido opt-in), web-publica 32 unitarios + 30 E2E (Chromium/Firefox) + build + audit verdes, Bruno 44/44 (77/77 tests) con la verificación del publicador activa, y el humo de la carga de pico funciona. No hay bugs Críticos ni Altos. Se aprueba con reservas porque: BUG-J2 (Medio) invalida la prueba de carga científica simultánea tal como está (y dejó 76 mensajes en la DLQ de desarrollo por purgar), BUG-J3 (documentación de carga), WebKit no verificado localmente, y los casos sin automatizar de §6. BUG-J2 debe corregirse antes de usar `carga-cientifica-simultanea.js` como evidencia del ASR4.

---

## Re-verificación ciclo 1 (2026-11-04)

Todo lo de abajo se ejecutó en esta sesión (no se tomó del reporte de backend). Sin commits; solo se añadió un test opt-in de QA.

### Resultados

| Verificación | Resultado |
|---|---|
| `mvn -o test` servicio-cientifico | **239 tests, 0 fallos, 0 errores, 1 omitido** (el opt-in de broker desplegado). BUILD SUCCESS. Coincide con el reporte. |
| `mvn -o test` servicio-consulta-publica | **190 tests, 0 fallos, 0 errores, 0 omitidos**. BUILD SUCCESS. Coincide. |
| Humo `carga-cientifica-simultanea.js` (`CIENTIFICA=1 R=10 EV=5 DUR=15s`, 2 réplicas, siguiendo solo el README) | Públicas 151 (p95 19,7 ms, 0 % no-200, 0 % 5xx). **Eventos 76 publicados, 76 procesados, cola pendiente 0, DLQ de carga 0.** Logs de las 2 réplicas: **0** apariciones de "publicador no permitido" y 0 de "descart". |
| Aislamiento de la cola de desarrollo | `consulta-publica.eventos` = 0 y `consulta-publica.eventos.dlq` = 76 (los viejos), **sin cambios** antes y después. Las réplicas de carga consumían `consulta-publica.carga.eventos` (2 consumidores). |
| Limpieza | `down -v` y `datos/limpiar.sh`: 0 fichas de carga; el JSON de humo se borró; la cola/DLQ/exchange de carga ya no existen en RabbitMQ. |
| E2E real científico -> consulta-publica (ARQ-26) | **OK**, ver abajo. |
| Bruno `--env local` (CLI 4.3.0, MySQL efímero 3399, ambos servicios con los jar actuales y `BIOHUB_EVENTOS_PUBLICADOR_CIENTIFICO=biohub`, verificación activa) | **44/44 requests, 77/77 tests, PASS** (9,4 s). Servicios detenidos y contenedor eliminado. |

### E2E ARQ-26 (sin inyectar `user_id` a mano)
No existe flujo real que publique `hallazgo.*` desde servicio-cientifico: el único endpoint que usa `PublicadorEventos` es `POST /api/cientifico/eventos/prueba` (`prueba.ping`, solo administrador), y el módulo de registro (HU-01) es de otro equipo. Se usó entonces el `PublicadorEventos` REAL (el mismo bean, con su `user_id` tomado de la conexión) desde un test opt-in nuevo: `servicio-cientifico/src/test/java/co/edu/unbosque/biohub/cientifico/eventos/PublicadorHallazgoE2ETest.java` (se activa con `QA_HALLAZGO_ID` + `QA_RMQ_PORT/USER/PASS`; sin esas variables se omite, por lo que no altera los 239 tests).
- Se arrancó consulta-publica (jar actual, perfil `local`, MySQL de desarrollo, `BIOHUB_EVENTOS_VERIFICAR_PUBLICADOR=true`, `BIOHUB_EVENTOS_PUBLICADOR_CIENTIFICO=biohub`, valores de `.env.example`) y se publicó un `hallazgo.registrado` con UUID único.
- Resultado: `GET /api/publica/fichas/{id}` devolvió 200 con la ficha completa (`ubicacionProtegida: true`, sin coordenadas); `consulta-publica.eventos` quedó en 0 y la DLQ de desarrollo siguió en 76 (no la usé para medir). Log del servicio sin WARN/ERROR de publicador. Con esto la cadena `user_id` AMQP del publicador -> verificación del consumidor funciona sin Bruno. Se eliminó la ficha de prueba de la BD de desarrollo.
- Límite: no se ejercitó el endpoint HTTP de servicio-cientifico con un JWT de administrador (publica `prueba.ping`, que no es `hallazgo.*`); la cobertura de ese camino es `EventosPruebaControllerTest` y `PublicadorEventosIntegracionTest`, que pasan.

### Estado de los hallazgos
- **BUG-J2: CORREGIDO.** El humo reproduce lo que describía el bug (antes 228 rechazos y 76 mensajes en DLQ) y ahora da 76/76 procesados, DLQ de carga 0, 0 rechazos por publicador, sin tocar las colas de desarrollo. El script además comprueba la DLQ de carga y reporta procesados/pendientes.
- **BUG-J3: CORREGIDO (parcial en otro documento).** Seguí `pruebas-carga/README.md` tal cual (construir jar e imagen, compose con `--scale`, siembra, k6, apagar y limpiar) sin conocimiento previo y funcionó a la primera; el orden `down -v` antes de `limpiar.sh` está explicado. Pendiente menor (no bloquea): `docs/equipo/trazabilidad.md` (líneas 9 y 176) sigue diciendo que `pruebas-carga/` solo tiene README y que no existen `.github/` ni la colección Bruno; es un documento ajeno al README.
- **O-11: CERRADA.** `.env.example` raíz trae `BIOHUB_EVENTOS_VERIFICAR_PUBLICADOR=true` y `BIOHUB_EVENTOS_PUBLICADOR_CIENTIFICO=biohub` (igual a `RABBITMQ_USER`); con esos valores el E2E y Bruno pasan con verificación activa.
- **Pendiente de usuario (heredado):** purgar `consulta-publica.eventos.dlq` (76 mensajes de mi prueba anterior).

### Bugs nuevos
Ninguno (sin BUG-J4). Observación de entorno: los jar de `target/` se reescribieron al empaquetar (no afecta a nada en ejecución).

### No ejecutado
WebKit local (limitación de librerías del sistema, igual que antes), web-publica/web-interna (fuera del alcance del ciclo, sin cambios), pico x60 y par `CIENTIFICA=0/1` completo (solo humo, por instrucción), y el endpoint HTTP de publicación con JWT de administrador sobre el servicio científico en ejecución.

### Veredicto ciclo 1: APROBADO
Los tres hallazgos (BUG-J2, BUG-J3, O-11) quedan cerrados con evidencia ejecutada, 239 + 190 tests en verde, Bruno 44/44, humo de carga limpio y ARQ-26 validado de punta a punta con el publicador real. Sin bugs nuevos.
