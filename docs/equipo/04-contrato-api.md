# 04 – Contrato de API

Base: `http://localhost:8080` (API Gateway). Autenticación: `Authorization: Bearer <JWT de Keycloak>`.

## Enrutamiento del gateway
| Prefijo | Servicio destino | ¿JWT? |
|---|---|---|
| `/api/gateway/**` | el propio gateway | Sí |
| `/api/cientifico/**` | servicio-cientifico (8081) | Sí |
| `/api/equipos/**` | servicio-equipos (8082) | Sí |
| `/api/publica/**` | servicio-consulta-publica (8083) | No (solo GET). Futuro: `X-API-Key` |
| `/api/reportes/**` | servicio-reportes (8084) | Sí |

La ruta se reenvía completa (`/api/cientifico/x` → `http://localhost:8081/api/cientifico/x`).

## GET /api/{gateway|cientifico|equipos|publica|reportes}/ping
Prueba de punta a punta.

**200**
```json
{ "servicio": "servicio-cientifico", "usuario": "investigador1",
  "roles": ["investigador_campo"], "fecha": "2026-10-21T23:14:39.749Z" }
```
- `roles`: solo roles de negocio (sin `offline_access`, `uma_authorization`, `default-roles-*`).
- En `/api/publica/ping` sin token: `"usuario": "anonimo", "roles": []`.

## Errores
| Código | Cuándo |
|---|---|
| 401 | Falta el token, o es inválido, vencido, de otro emisor o sin `aud=biohub-api` |
| 403 | CORS desde un origen no permitido |
| 404 | Prefijo sin servicio asociado |
| 400 | Cuerpo inválido o con validación fallida (`application/problem+json`) |
| 403 | Rol insuficiente (JWT válido sin el rol requerido) |
| 503 | RabbitMQ no disponible o sin confirmar el evento (`application/problem+json`, sin detalles internos) |
| 502 | Servicio destino caído (`application/problem+json`, sin detalles internos) |

## Eventos (RabbitMQ)

Mensajería asíncrona entre servicios. Los endpoints siguientes verifican el flujo de punta a punta con un evento de prueba; sirven de base para el evento real `hallazgo.registrado`.

### Topología
| Elemento | Nombre | Declara |
|---|---|---|
| Exchange topic durable | `biohub.eventos` | cientifico (publicador) y los consumidores |
| Dead-letter exchange topic durable | `biohub.eventos.dlx` | consumidores |
| Cola durable (`x-dead-letter-exchange=biohub.eventos.dlx`, `x-dead-letter-routing-key=<cola>.dlq`) | `consulta-publica.eventos` / `reportes.eventos` | consulta-publica / reportes |
| DLQ durable | `consulta-publica.eventos.dlq` / `reportes.eventos.dlq` | consulta-publica / reportes |
| Bindings cola → `biohub.eventos` | `prueba.#`, `hallazgo.#` | cada consumidor |
| Binding DLQ → `biohub.eventos.dlx` | clave = nombre de la DLQ | cada consumidor |

### Sobre del evento
Mensaje JSON (`application/json`), persistente (`delivery_mode=2`), `message_id` = `id`. Routing key = `tipo`. No se envía la cabecera `__TypeId__`.
```json
{ "id": "89dae52d-f25d-4282-bcfa-adcc26a3b423", "tipo": "prueba.ping", "origen": "servicio-cientifico",
  "fecha": "2026-10-22T04:33:23.726914555Z", "usuario": "admin1", "datos": { "mensaje": "hola" } }
```
- `usuario`: `preferred_username` del JWT que originó el evento. `fecha`: ISO-8601 UTC.
- Tipos conocidos por los consumidores: `prueba.ping` (`datos.mensaje`: string, máx 200) y `hallazgo.registrado` (datos libres hasta definir el modelo). Para agregar un tipo: añadirlo a `TIPOS_CONOCIDOS` en `ConsumidorEventos` de cada consumidor.
- Un mensaje con JSON roto, sobre incompleto, tipo desconocido, routing key distinta del `tipo` o de más de 64 KB se rechaza sin reencolar y va a la DLQ. Otros fallos del consumidor se reintentan 3 veces (1 s, 2 s) y luego van a la DLQ.

### POST /api/cientifico/eventos/prueba
Publica un evento `prueba.ping`. Requiere JWT con rol `administrador`.

Cuerpo opcional: `{ "mensaje": "texto" }` (máx 200 caracteres; por defecto `"ping"`).

**202** – el sobre publicado (ver arriba), tras la confirmación del broker (publisher confirm).
**400** – mensaje de más de 200 caracteres o JSON inválido. **401** sin token. **403** sin rol `administrador`. **503** RabbitMQ no disponible.

### GET /api/publica/eventos/recibidos  ·  GET /api/reportes/eventos/recibidos
Últimos 20 eventos recibidos por servicio-consulta-publica / servicio-reportes (en memoria, del más reciente al más antiguo). Requiere JWT con rol `administrador`, validado en el propio servicio (aunque `/api/publica/**` GET sea público en el gateway, que reenvía `Authorization`).

**200**
```json
{ "servicio": "servicio-reportes", "total": 1,
  "eventos": [ { "id": "89dae52d-...", "tipo": "prueba.ping", "origen": "servicio-cientifico",
                 "fecha": "2026-10-22T04:33:23.726914555Z", "usuario": "admin1", "datos": { "mensaje": "hola" } } ] }
```
**401** sin token (también en `/api/publica/eventos/**`). **403** sin rol `administrador`.
