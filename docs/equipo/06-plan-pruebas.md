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

## 8. Casos de artefactos de despliegue (verificación estática)
| ID | Descripción | Comando | Esperado |
|---|---|---|---|
| DP-01 | Sintaxis de scripts | `for f in $(find deploy -name '*.sh'); do bash -n $f; done` | sin errores |
| DP-02 | Compose backend válido | `docker compose --env-file <env ficticio> -f deploy/backend/docker-compose.yml config` | rc 0 |
| DP-03 | Compose datos válido | idem datos | rc 0 |
| DP-04 | Ningún `ports:` a 0.0.0.0 | revisar `host_ip` en `config` | todos con IP Tailscale |
| DP-05 | Servicios sin puertos publicados | `config` | solo api-gateway, keycloak, rabbitmq(15672) |
| DP-06 | Variables por servicio (SEC-12) | comparar `environment` de cada servicio; sin `env_file` | cada uno solo con las suyas |
| DP-07 | Variables obligatorias | quitar `TS_IP_BACKEND` o `RABBITMQ_REPORTES_PASSWORD` del env | `config` falla con mensaje claro |
| DP-08 | Imágenes | `mvn -DskipTests package` y `docker build -f <svc>/Dockerfile .` (contexto = raíz) | 5 imágenes, usuario uid 10001, `curl` presente |
| DP-09 | `generar-env.sh` (en copia fuera del repo) | ejecutar y analizar | permisos 600, 16 contraseñas únicas, MySQL idéntico en ambos, sin comentarios/espacios, no sobrescribe sin `--forzar` |
| DP-10 | `generar-definitions.sh` + RabbitMQ real | cargar `definitions.json` generado en `rabbitmq:4.1-management` | 4 usuarios, sin `guest`, login AMQP de cada usuario, permisos EV-23/24 |

## 9. Procedimiento E2E contra las VMs (para repetir)
Requisitos: equipo en la tailnet, `biohub-backend` resolviendo a la IP Tailscale (MagicDNS o `/etc/hosts`). La contraseña se lee del `.env` sin imprimirla.

```bash
set -a; . deploy/backend/.env; set +a      # carga KEYCLOAK_TEST_USERS_PASSWORD (no hacer echo)
KC=http://biohub-backend:8180/realms/biohub/protocol/openid-connect/token
GW=http://biohub-backend:8080
tok() { curl -s -X POST "$KC" -d grant_type=password -d client_id=biohub-pruebas -d "username=$1" \
          --data-urlencode "password=$KEYCLOAK_TEST_USERS_PASSWORD" | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])'; }
ADMIN=$(tok admin1); INV=$(tok investigador1)

# E2E-V1 Sin token -> 401           (esperado 401)
curl -s -o /dev/null -w '%{http_code}\n' -X POST $GW/api/cientifico/eventos/prueba
# E2E-V2 investigador1 -> 403       (esperado 403)
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H "Authorization: Bearer $INV" $GW/api/cientifico/eventos/prueba
# E2E-V3 mensaje > 200 -> 400       (esperado 400)
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
     -d "{\"mensaje\":\"$(printf 'a%.0s' $(seq 201))\"}" $GW/api/cientifico/eventos/prueba
# E2E-V4 admin1 publica -> 202 con sobre (guardar el id)
curl -s -w '\n%{http_code}\n' -X POST -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
     -d '{"mensaje":"e2e-vm"}' $GW/api/cientifico/eventos/prueba
# E2E-V5 recibido en ambos consumidores (esperado 200, el id de V4 es el primero de la lista)
for r in publica reportes; do curl -s -H "Authorization: Bearer $ADMIN" $GW/api/$r/eventos/recibidos | python3 -m json.tool | head -20; done
# E2E-V6 consumidores sin token / con investigador1 (esperado 401 y 403, incluido /api/publica)
for r in publica reportes; do
  curl -s -o /dev/null -w "$r sin token %{http_code}\n" $GW/api/$r/eventos/recibidos
  curl -s -o /dev/null -w "$r investigador %{http_code}\n" -H "Authorization: Bearer $INV" $GW/api/$r/eventos/recibidos
done
# E2E-V7 DLQ vacías y colas vacías (panel http://<IP TS backend>:15672, usuario biohub): reportes.eventos.dlq y consulta-publica.eventos.dlq con 0 mensajes
```
Opcional (503): detener `biohub-rabbitmq` en la VM y repetir V4 (esperado 503 problem+json); volver a levantarlo. Requiere autorización de la dueña.
