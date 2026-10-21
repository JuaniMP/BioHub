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
| 502 | Servicio destino caído (`application/problem+json`, sin detalles internos) |
