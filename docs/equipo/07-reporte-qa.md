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

Sección retirada en la iteración 15 (VPN de malla eliminada; ver 09-despliegue.md).

## Bugs
No se encontraron bugs Críticos, Altos ni Medios. Hallazgos menores:

BUG-04 (`.env.example` de `deploy/`): sección retirada en la iteración 15 (VPN de malla eliminada; ver 09-despliegue.md).

### Observaciones (para ciberseguridad / Tech Lead)
- O-9: Los tests opt-in dependen de variables de entorno; no hay prueba automática en CI de las definitions reales. Sugerencia: añadir un IT que cargue `definitions.json` generado en Testcontainers.
- O-10: Sin cobertura medida (JaCoCo) ni E2E automatizado del flujo de eventos por el gateway; queda el procedimiento manual (plan, sección 9).

## Veredicto: APROBADO CON OBSERVACIONES
El código de RabbitMQ cumple el contrato (73/73 tests con broker real, 0 omitidos) y los permisos por usuario funcionan contra un RabbitMQ real con las definitions generadas. La verificación de los artefactos de despliegue en VMs fue retirada en la iteración 15 (VPN de malla eliminada; ver 09-despliegue.md).

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
- Bloqueados por dependencias de otros equipos (sin cambios): IE-01..IE-04, SG-17..SG-19, CG-11, WI-13, DX-01..DX-04 (requieren autorización y despliegue; VPN y VMs ya retiradas, estos casos están obsoletos).
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

### CI en GitHub, primera ejecución (2026-11-04, run 41876849186)
- backend cientifico y consulta-publica: OK. Bruno: 44/44 requests, 77/77 tests, pero el job salía con código 2 porque Bruno CLI no crea `resultados/` → `mkdir -p resultados` en `ci.yml`.
- web-publica: 2 E2E fallaban solo en WebKit por `style-src-attr` "inline" en `<select>` nativos: artefacto de WebKit (se reproduce con un `<select>` estático en HTML puro con `style-src 'self'`), no de la app. El fixture `erroresCsp` ahora usa el evento `securitypolicyviolation` e ignora solo esa combinación exacta; 3 tests de guarda demuestran que `<style>`, `style=` y `<select style=...>` siguen detectándose. WebKit verificado en Docker (`mcr.microsoft.com/playwright:v1.64.1-noble`): 18 pasan, 1 omitido previo. CSP sin cambios (sin `'unsafe-inline'`).

## Iteración 16 (2026-11-04): verificación de 81f4e28..HEAD

Commits revisados: ARQ-33 (81f4e28), SEC-J-04 web-interna (47b5cc9), SEC-J-13 (786f4e1), SEC-J-12 (57ab7ad), SEC-J-08.3 (1e449ac), ARQ-29 (25d2175), ARQ-28 (aeb67d8).

### Bug encontrado y corregido

**BUG-J4 (Medio, responsable: QA, solo tests): `mvn verify` a secas falla con un `.env` local del perfil Aiven.**
- Pasos: con un `.env` que define `CIENTIFICO_MIGR_USER/PASSWORD` (o `CONSULTA_MIGR_*`) en la raíz o en el servicio, ejecutar `mvn -B verify` en `servicio-cientifico` o `servicio-consulta-publica`.
- Esperado: las pruebas son herméticas y no dependen del entorno del desarrollador. Obtenido: `spring.config.import` carga el `.env`, Flyway usa `cientifico_migr` contra el MySQL de Testcontainers y falla con `Access denied for user 'cientifico_migr'` (científico: decenas de errores de contexto, p. ej. MigracionesUbicacionTest 6/6 y QaSeguridadJwtTest 30/30; consulta-publica: igual). En CI no se ve porque no hay `.env`.
- Primer intento descartado: sobrescribir `spring.config.import` en un `application.properties` de pruebas no sirve, porque Spring procesa los imports de cada archivo de configuración aunque otro tenga precedencia.
- Corrección: `src/test/resources/application.properties` en ambos servicios con `spring.flyway.user=${spring.datasource.username}` y `spring.flyway.password=${spring.datasource.password}`. Solo afecta al classpath de pruebas; producción intacta; no se leyó ni copió ningún valor de los `.env`.
- Evidencia tras la corrección, con el `.env` real presente y sin variables sobrescritas: científico 264 ejecutadas, 0 fallos, 0 errores, 2 omitidas, BUILD SUCCESS; consulta-publica 194, 0, 0, 0 omitidas, BUILD SUCCESS.

### Resultados

| Proyecto | Comando | Resultado |
|---|---|---|
| servicio-cientifico | `mvn -B verify` | 264 / 0 fallos / 0 errores / 2 omitidas |
| servicio-consulta-publica | `mvn -B verify` | 194 / 0 / 0 / 0 omitidas |
| web-interna | `npm test` y `npm run build` | 46 de 46 (4 archivos); build OK |
| web-publica | `npm test` y `npm run build` | 32 de 32 (3 archivos); build OK |

Las 2 omitidas de científico son las pruebas opt-in contra el broker desplegado (requieren `QA_RMQ_*`).

### Revisión de que las pruebas nuevas prueban lo que dicen

- SEC-J-13: `HallazgoRegistradoSerializacionTest` comprueba JSON y `convertValue` sin claves ni valores de coordenadas, y que siguen en memoria. `PublicadorEventosTest` cubre claves (6 variantes, sin distinguir mayúsculas), anidadas (mapa, lista, mapa), texto (decimal, grados, "lat:/lon:"), que el mensaje no filtra el valor, que no se llama al broker, un caso sin coordenadas con números con coma que se publica (sin falso positivo) y que `prueba.*` no se revisa. Sin hueco relevante.
- SEC-J-12: `ProveedorDeLlavesKeyStoreTest` cubre 600 aceptado y 644/640/604 rechazados sin revelar la ruta.
- SEC-J-08.3: `AuditoriaLogbackTest` carga el `logback-spring.xml` real con Joran: archivo propio, solo esas líneas, anexado entre reinicios, nivel INFO fijo aunque la raíz sea ERROR.
- ARQ-29: regla ArchUnit sobre bytecode (con `allowEmptyShould`, hoy `registro` solo tiene `package-info`, así que la regla es preventiva) más comprobación sobre el código fuente que sí atrapa un import. Aceptable.
- ARQ-28: `layeredArchitecture` en ambos servicios más reglas explícitas; en consulta-publica la deuda `service.vista`/`CriterioBusquedaPublica` queda congelada y documentada. Nota: en ubicacion la capa Infrastructure solo se restringe con `mayNotBeAccessedByAnyLayer`, y el acceso Service→Infrastructure queda cubierto por `infrastructureNoDependeDeService` y el resto de reglas D43.
- SEC-J-04: `PaginaMapa.test.jsx` verifica `maxZoom` 8 con un punto, 10 con varios (`padding` incluido) y `referrerPolicy="no-referrer"` en la capa de teselas.

No añadí casos nuevos: no vi huecos relevantes. Observación (Bajo, no bloquea): las comprobaciones de keystore 600/644 dependen de POSIX; en un sistema de archivos sin POSIX la prueba de 644 no es significativa.

### No ejecutado

E2E contra servicios desplegados y k6 (D42, pospuesto por la usuaria): fuera del alcance de esta iteración.

### Veredicto Iteración 16: APROBADO

Los siete commits cumplen lo declarado y las pruebas nuevas son significativas. El único bug (BUG-J4, aislamiento de pruebas) estaba en los tests y quedó corregido; sin bugs abiertos de producción.


---

# Iteración 17 (2026-11-08)

Rama `feature/ubicacion-consulta-publica`. Alcance: `denyAll` y `@PreAuthorize` (D55), CI rojo (zona horaria y recifrado), integración del PR #7 (API Keys y límites) y E2E por el gateway con perfil `local`.

## Comandos usados

```bash
# Suites como en CI (ci.yml tiene 3 servicios: cientifico, consulta-publica y api-gateway), con TZ=UTC y luego TZ=America/Bogota
TZ=<zona> JAVA_TOOL_OPTIONS=-Duser.timezone=<zona> sg docker -c "mvn -B -ntp -pl <servicio> -am verify"
# Repetición x10
sg docker -c "mvn -B -ntp -q -pl servicio-cientifico -am test -Dtest='RecifradoServiceTest,BusquedaHallazgosApiTest' -Dsurefire.failIfNoSpecifiedTests=false"
mvn -B -ntp -q -pl servicio-consulta-publica -am test -Dtest=ValidadorDatosHallazgoTest -Dsurefire.failIfNoSpecifiedTests=false   # (esta clase vive en consulta-publica)
# E2E: MySQL locales del compose (docker compose up -d mysql-transaccional mysql-lectura mysql-gateway) y los 3 servicios con
SPRING_PROFILES_ACTIVE=local java -jar <modulo>/target/<modulo>-0.0.1-SNAPSHOT.jar   # cwd fuera del repo, variables del .env raíz exportadas
```

Detalles del E2E: los jars se ejecutaron con el directorio de trabajo fuera del repo para que no leyeran `servicio-cientifico/.env` (tiene configuración y llave de Aiven). `servicio-cientifico` usó una `UBICACION_CLAVES` aleatoria generada para la prueba. En los logs el perfil activo fue `local` en los tres y las URL JDBC fueron `localhost:3307` (científico), `localhost:3308` (consulta pública) y `localhost:3310` (gateway). Ningún log contiene "aivencloud". Los tokens vienen del client `biohub-pruebas` (password grant); el script lee la contraseña de `.env` sin imprimirla.

## Resultados de suites

| Servicio | TZ | Ejecutadas | Fallos | Errores | Omitidas | SeguridadEndpointsTest | ArquitecturaSeguridadTest | Resultado |
|---|---|---:|---:|---:|---:|---|---|---|
| servicio-cientifico | UTC | 322 | 0 | 0 | 2 | 56/56 | 2/2 | BUILD SUCCESS |
| servicio-cientifico | America/Bogota | 322 | 0 | 0 | 2 | 56/56 | 2/2 | BUILD SUCCESS |
| servicio-consulta-publica | UTC | 239 | 0 | 0 | 0 | 40/40 | 2/2 | BUILD SUCCESS |
| servicio-consulta-publica | America/Bogota | 239 | 0 | 0 | 0 | 40/40 | 2/2 | BUILD SUCCESS |
| api-gateway | UTC | 168 | 0 | 0 | 0 | n/a | n/a | BUILD SUCCESS |
| api-gateway | America/Bogota | 168 | 0 | 0 | 0 | n/a | n/a | BUILD SUCCESS |

Las 2 omitidas de científico son `PublicadorHallazgoE2ETest` y `PublicadorBrokerDesplegadoTest` (opt-in con `QA_RMQ_*`, igual que en la iteración 16).

Repeticiones (10 ejecuciones consecutivas cada una, todas con exit 0):

| Prueba | Casos por ejecución | Zona | Fallos en 10 ejecuciones |
|---|---:|---|---:|
| `RecifradoServiceTest` | 6 | UTC | 0 |
| `BusquedaHallazgosApiTest` | 21 | UTC | 0 |
| `ValidadorDatosHallazgoTest` | 20 | UTC | 0 |
| `ValidadorDatosHallazgoTest` | 20 | America/Bogota | 0 |

## E2E por el gateway (puerto 8080, perfil `local`)

Quién respondió se dedujo del cuerpo y las cabeceras: el 403 del gateway es `application/problem+json;charset=UTF-8` (solo ocurre con `sinrol1`, que no tiene ninguno de los 5 roles); el 403 del servicio es `application/problem+json` sin `charset`. El 401 sin token lo da el gateway (sin cuerpo).

| Método | Ruta | Usuario | Esperado | Obtenido | |
|---|---|---|---|---|---|
| GET | /api/cientifico/ubicacion/hallazgos | cientifico1, investigador1, auditor1, analista1, admin1 | 200 | 200 (x5) | ✅ |
| GET | /api/cientifico/ubicacion/hallazgos | sinrol1 | 403 | 403 problem+json `ROL_INSUFICIENTE` (gateway) | ✅ |
| GET | /api/cientifico/ubicacion/hallazgos | sin token | 401 | 401 | ✅ |
| GET | /api/cientifico/ubicacion/mapa | cientifico1 | 200 | 200 | ✅ |
| GET | /api/cientifico/ubicacion/mapa | admin1, investigador1, auditor1, analista1 | 403 | 403 problem+json `ROL_INSUFICIENTE` (servicio) x4 | ✅ |
| GET | /api/cientifico/ubicacion/mapa | sinrol1 | 403 | 403 problem+json `ROL_INSUFICIENTE` (gateway) | ✅ |
| GET | /api/cientifico/ubicacion/mapa | sin token | 401 | 401 | ✅ |
| POST | /api/cientifico/ubicacion/recifrado | admin1 | 2xx | 200 `{claveActiva:v1, revisados:0, recifrados:0, fallidos:0}` | ✅ |
| POST | /api/cientifico/ubicacion/recifrado | cientifico1, investigador1 | 403 | 403 problem+json (servicio) | ✅ |
| POST | /api/cientifico/ubicacion/recifrado | sinrol1 | 403 | 403 problem+json (gateway) | ✅ |
| POST | /api/cientifico/ubicacion/recifrado | sin token | 401 | 401 | ✅ |
| GET | /api/cientifico/ping | los 5 roles | 200 | 200 (x5) | ✅ |
| GET | /api/cientifico/ping | sinrol1 | 403 | 403 `ROL_INSUFICIENTE` (gateway) | ✅ |
| GET | /api/cientifico/ping | sin token | 401 | 401 | ✅ |
| GET | /api/publica/fichas | sin token | 200 | 200 | ✅ |
| GET | /api/publica/fichas/00000000-0000-0000-0000-000000000000 | sin token | 404 | 404 problem+json "La ficha solicitada no existe." | ✅ |
| GET | /api/publica/ping | sin token | 200 | 200 | ✅ |
| GET | /api/publica/eventos/recibidos | admin1 | 200 | 200 | ✅ |
| GET | /api/publica/eventos/recibidos | cientifico1, investigador1, sinrol1 | 403 | 403 (servicio, cuerpo vacío; ver OBS-J17-1) | ✅ |
| GET | /api/publica/eventos/recibidos | sin token | 401 | 401 (servicio, cuerpo vacío) | ✅ |
| POST | /api/publica/fichas | sin token / admin1 | rechazada | 401 / 403 `ROL_INSUFICIENTE` (gateway) | ✅ |
| GET | /api/cualquiercosa | admin1 / sin token | rechazada | 403 `ROL_INSUFICIENTE` / 401 | ✅ |
| GET | /api/gateway/consumidores | cientifico1 / sin token | 403 / 401 | 403 / 401 | ✅ |
| POST | /api/gateway/consumidores | admin1 | 201 | 201 | ✅ |
| POST | /api/gateway/consumidores/{id}/keys | admin1 | 201 | 201 (apiKey mostrada solo aquí) | ✅ |
| GET | /api/v1/externa/fichas | X-API-Key válida | 200 | 200 con `X-RateLimit-Limit: 60` y `X-RateLimit-Remaining: 58` | ✅ |
| GET | /api/v1/externa/fichas | sin key | 401 | 401 `API Key inválida` | ✅ |
| GET | /api/v1/externa/fichas | key inválida | 401 | 401 | ✅ |
| GET | /api/v1/externa/fichas | JWT de admin1 sin key | 401 | 401 | ✅ |
| POST | /api/v1/externa/fichas | con key válida | rechazada | 401 (la ruta externa es solo GET) | ✅ |
| DELETE | /api/gateway/keys/{id} | admin1 | 204 | 204 | ✅ |
| GET | /api/v1/externa/fichas | key revocada | 401 | 401 (revocación inmediata) | ✅ |
| GET | /api/publica/ping (x112) | sin token, misma IP | 429 + Retry-After | 429 en la petición 112 (había ~8 previas: 120 por minuto), `Retry-After: 58`, problem+json `LIMITE_EXCEDIDO` (gateway) | ✅ |

Total: 50 comprobaciones del script principal, 50 aprobadas, 0 fallidas. Las llamadas internas con JWT no pidieron API Key (PR #7 no rompe lo anterior). La key de prueba se revocó y el consumidor `qa-j17-*` quedó creado en la BD local (no hay endpoint para borrarlo).

Comprobaciones adicionales: mientras el cupo por IP estaba agotado, `/api/publica/eventos/recibidos` con y sin JWT también devolvió 429 (el límite se aplica antes de la autenticación; es coherente con el diseño). Llamadas directas a los puertos de los servicios confirman que el 403 de `eventos/recibidos` lo emite `servicio-consulta-publica` con cuerpo vacío.

## Bugs

No se encontraron bugs bloqueantes. Observaciones:

### OBS-J17-1 (Bajo, no bloquea) — responsable sugerido: backend (consulta-publica)
- **Qué:** el 403 y el 401 de `GET /api/publica/eventos/recibidos` salen del propio servicio con cuerpo vacío y `Content-Type: application/octet-stream` (solo la cabecera `WWW-Authenticate`). El 403 de `ubicacion` en científico sí es problem+json `ROL_INSUFICIENTE` (D55).
- **Reproducir:** `GET /api/publica/eventos/recibidos` con token de `cientifico1` por el gateway, o directo a `localhost:8083`.
- **Esperado vs obtenido:** el contrato (§ eventos/recibidos) solo exige 401/403, así que no incumple; pero es inconsistente con el formato de error del resto (§5.1 y D55). Obtenido: 403 con cuerpo vacío.
- **Archivo probable:** `servicio-consulta-publica/.../config/SecurityConfig` (falta un `AccessDeniedHandler`/`AuthenticationEntryPoint` que emita problem+json).

### OBS-J17-2 (Informativo)
`/api/publica/**` no devuelve cabeceras `X-RateLimit-*` en respuestas 2xx (solo `Retry-After` en el 429). Es el comportamiento del código (`ApiKeyRateLimitFilter.atenderPortal`) y del contrato (`X-RateLimit-*` solo en la API externa), así que no es defecto; se anota porque el encargo esperaba cabeceras de límite en la ruta pública.

### OBS-J17-3 (Informativo)
Las pruebas de integración de `servicio-cientifico` leen `servicio-cientifico/.env` (cwd del módulo), que hoy contiene credenciales y llave reales de Aiven. No hubo conexión a Aiven (0 coincidencias de "aivencloud" en los logs de las suites), pero conviene que 17g lo revise.

## No ejecutado

- `web-publica` (npm test, build y Playwright del job `frontend` de CI): fuera del alcance de la tarea 17f.
- Pruebas opt-in contra el broker desplegado (`QA_RMQ_*`): omitidas por diseño (2).
- 429 de la API externa por consumidor (60/min) y por 20 keys inválidas/min: no probados en E2E; los cubre la suite del gateway.
- `servicio-equipos` y `servicio-reportes` no se arrancaron (fuera de alcance).

## Veredicto Iteración 17: APROBADO

Las tres suites de CI pasan en UTC y en America/Bogota (322, 239 y 168 pruebas, 0 fallos), las pruebas antes inestables (`RecifradoServiceTest`, `BusquedaHallazgosApiTest`, `ValidadorDatosHallazgoTest`) pasaron 10 de 10 veces, y los 50 casos E2E por el gateway coinciden con la matriz de `11-seguridad-y-acceso.md`, el 403 de `sinrol1` es problem+json `ROL_INSUFICIENTE` (D55) y las API Keys y el límite por IP funcionan sin romper las rutas internas con JWT. Solo hay una observación de formato de error (Bajo).

---

# Iteración 18: verificación de SEC-J-19 y SEC-J-20

Fecha: 2026-11-09. Rama `feature/ubicacion-consulta-publica`. Commits verificados: SEC-J-19 (`ArquitecturaSeguridadTest` sin vías de elusión) y SEC-J-20 (`ProblemaSeguridadHandler`: 403 problem+json `ROL_INSUFICIENTE` y 401 del contrato en todo el servicio). Durante la sesión el historial se reescribió: los hashes actuales son `fc769a9` (SEC-J-19) y `efed795` (SEC-J-20); el contenido es el mismo que los `d718be5` y `e49373a` del encargo.

## Resumen de ejecución (`mvn -B -pl servicio-cientifico,servicio-consulta-publica -am verify`)

| Ejecución | servicio-cientifico | servicio-consulta-publica | Resultado |
|---|---|---|---|
| TZ=UTC | 434 pruebas, 0 fallos, 2 omitidas | 339, 0 fallos, 0 omitidas | BUILD SUCCESS |
| TZ=America/Bogota (1.ª) | 434, 0, 2 omitidas | 339, **1 fallo**, 0 | BUILD FAILURE (ver BUG-J18-1) |
| TZ=America/Bogota (repetición completa) | 434, 0, 2 omitidas | 339, 0, 0 | BUILD SUCCESS |

- La suma de los XML de `surefire-reports` de científico da 437 (2 omitidas), como midió el Tech Lead; el resumen de Maven da 434. Es una diferencia de conteo del informe (probablemente pruebas dinámicas/anidadas), no un fallo; las 2 omitidas coinciden (broker desplegado opt-in).
- Repetí 5 veces `PublicadorNoPermitidoIntegracionTest` aislada en Bogota: 5/5 en verde. El fallo es intermitente (1 de 7 ejecuciones en total).
- Cobertura (JaCoCo): no disponible en este build, no medida.

## Cobertura de las pruebas nuevas (revisadas leyendo el código y ejecutadas)

| Requisito | Dónde se prueba | Estado |
|---|---|---|
| 403 problem+json `ROL_INSUFICIENTE`, `Cache-Control: no-store`, cuerpo fijo (`type`, `title`, `status`, `detail`, `instance`, `codigo`) fuera de `ubicacion` | científico: `SeguridadEndpointsTest` (todo endpoint con JWT sin roles y con rol no permitido, `/api/cientifico/ping`, `/api/cientifico/eventos/prueba`, denyAll en `/`, `/actuator/*`, `/api/publica/*`...). consulta-pública: `/api/publica/eventos/recibidos` con 6 roles y sin roles, y rutas denyAll | Cubierto |
| 401 sin cuerpo, `WWW-Authenticate: Bearer`, `no-store` | ambos `SeguridadEndpointsTest` (sin token en todos los endpoints; ruta pública con token malo) | Cubierto |
| 401 sin la descripción interna del decodificador JWT | `unJwtInvalidoDa401SinCuerpoSinFiltrarElMotivoInterno` en ambos: cabecera exacta `Bearer error="invalid_token"`, sin "firma rota" ni "interno-7" | Cubierto |
| `UbicacionExceptionHandler` sigue respondiendo y auditando primero en `ubicacion` | Sin prueba nueva en SEC-J-20; lo cubren las existentes `MapaApiTest.losDemasRolesRecibenElMapaDenegado` (problem+json, no-store, `ROL_INSUFICIENTE`) y `AuditoriaYSinFiltracionesTest` / `BusquedaHallazgosApiTest` (auditoría del 403); todas en verde | Cubierto (regresión) |
| `ConsultaPublicaExceptionHandler` ya no convierte AccessDenied en 500 | `unAccessDeniedExceptionDeUnEndpointDeFichasNoSeConvierteEn500` (mock lanza `AccessDeniedException`, esperado 403 `ROL_INSUFICIENTE` sin filtrar el mensaje) | Cubierto |
| El 403 no filtra trazas ni detalles internos | `elCuerpoDel403NoFiltraTrazasNiDetallesInternos` en ambos | Cubierto |

Huecos menores (no bloqueantes): no hay prueba que combine "denyAll de la cadena de filtros" con auditoría (no se audita fuera de `ubicacion`, lo que es el diseño); el 403 de `/actuator/env` se probó en vivo (abajo) y en la suite, pero la suite no comprueba `Cache-Control` en esa ruta concreta salvo mediante `esRolInsuficiente`, que sí lo hace.

## Prueba de mutación manual

| Mutación (temporal) | Resultado |
|---|---|
| Quitar `@PreAuthorize` de `PingController` (científico, línea 21) | `ArquitecturaSeguridadTest` falla (69 pruebas, 1 fallo): "methods ... annotated with @RequestMapping should tener @PreAuthorize con un rol..." |
| Cambiar `permitAll()` por `isAuthenticated()` en `FichasPublicasController` línea 44 (consulta-pública) | `ArquitecturaSeguridadTest` falla (66 pruebas, 1 fallo) |

Ambos archivos restaurados con `git checkout -- <archivo>`; `git status` limpio tras cada mutación y al final.

## E2E por el gateway (perfil `local`, nunca `aiven`)

Infraestructura local arriba (keycloak, mysql, rabbitmq, redis). Levanté consulta-pública (8083), científico (8081) y api-gateway (8080) con perfil `local`, cwd fuera del repo y llave de ubicación aleatoria. Usuario `sinrol1` (sin ninguno de los 5 roles).

| Petición | Resultado obtenido | Estado |
|---|---|---|
| GET `/api/publica/eventos/recibidos` por gateway, `sinrol1` | 403, `application/problem+json`, `no-store`, `{"codigo":"ROL_INSUFICIENTE", "instance":"/api/publica/eventos/recibidos", ...}` | OK |
| Ídem sin token (gateway) | 401, `WWW-Authenticate: Bearer`, `no-store`, sin cuerpo | OK |
| Ídem directo al servicio 8083, `sinrol1` | 403 problem+json `ROL_INSUFICIENTE`, `no-store` (antes de SEC-J-20: cuerpo vacío) | OK, es la causa raíz de OBS-J17-1 corregida |
| Ídem directo 8083, sin token | 401 `WWW-Authenticate: Bearer`, `no-store`, sin cuerpo | OK |
| Ídem directo 8083, token basura | 401 `WWW-Authenticate: Bearer error="invalid_token"`, sin descripción interna | OK |
| Directo 8081 `/api/cientifico/ping`, `sinrol1` | 403 problem+json `ROL_INSUFICIENTE`, `no-store` | OK |
| Directo 8081 `/api/cientifico/ubicacion/mapa`, `sinrol1` | 403 problem+json (handler de `ubicacion`) | OK |
| Directo 8081 `/api/cientifico/ping` sin token | 401 `Bearer`, `no-store`, sin cuerpo | OK |
| Directo 8081 `/actuator/env`, `sinrol1` | 403 problem+json `ROL_INSUFICIENTE` | OK |

OBS-J17-1: **CERRADO**. Servicios y gateway detenidos al terminar; sin cambios en el árbol.

## Bugs

### BUG-J18-1 (Bajo) Prueba intermitente: `PublicadorNoPermitidoIntegracionTest.elBrokerRechazaUnUserIdFalsificado`
- Responsable sugerido: backend (dueño de la prueba, no es código de producción).
- Archivo: `servicio-consulta-publica/src/test/java/co/edu/unbosque/biohub/consultapublica/eventos/PublicadorNoPermitidoIntegracionTest.java:86`.
- Reproducir: `TZ=America/Bogota mvn -B -pl servicio-cientifico,servicio-consulta-publica -am verify` repetidas veces; falló 1 vez en 7 (no falló en UTC ni en 5 ejecuciones aisladas).
- Esperado: `assertThatThrownBy` recibe una excepción porque RabbitMQ rechaza el `user_id` falsificado.
- Obtenido: `Expecting code to raise a throwable` (línea 86). El log de esa misma ejecución muestra que el broker sí rechazó: `PRECONDITION_FAILED - user_id property set to 'guest' but authenticated user was 'intruso_mq2'`, pero el cierre de canal llega de forma asíncrona y el `queueDeclarePassive` posterior se ejecutó antes de que se propagara (condición de carrera). No es una regresión de SEC-J-19/20 (ningún archivo tocado por esos commits interviene); no depende de la zona horaria, solo coincidió.
- Sugerencia: esperar con `Awaitility` hasta ver el error de canal (o comprobar la ausencia de la ficha y que el mensaje no se entregó) en vez de una sola operación posterior.

## No ejecutado

- Frontends (`web-interna`, `web-publica`), servicio-equipos y servicio-reportes: fuera de alcance.
- Cobertura JaCoCo: no configurada.

## Veredicto Iteración 18: APROBADO

SEC-J-19 y SEC-J-20 cumplen lo declarado: las mutaciones sobre `@PreAuthorize` son detectadas, el 403 problem+json `ROL_INSUFICIENTE` con `no-store` aplica a todo el servicio (comprobado en pruebas y en vivo), el 401 conserva el contrato sin filtrar la descripción del decodificador, el handler de `ubicacion` sigue auditando y `ConsultaPublicaExceptionHandler` ya no produce 500. Las suites pasan en UTC y Bogota (434 y 339, 0 fallos tras repetir). Queda un único hallazgo Bajo (BUG-J18-1, prueba intermitente), que no bloquea el push pero conviene estabilizar porque puede hacer fallar el CI al azar.
