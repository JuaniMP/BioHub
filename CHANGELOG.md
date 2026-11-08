# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

## [Unreleased]

### Removed
- Retirada la VPN de malla anterior; MySQL pasa a Aiven. Se eliminan `deploy/` (VMs KVM, scripts de despliegue, ACL), la guía de la VPN y las VMs `biohub-backend`/`biohub-datos`.

### Added
- Evento de prueba de RabbitMQ: `POST /api/cientifico/eventos/prueba`, `GET /api/publica/eventos/recibidos` y `GET /api/reportes/eventos/recibidos`.

### Added (acceso y gateway)
- API externa `/api/v1/externa/fichas/**` con API Key y límite por consumidor; límite por IP en el portal; administración de consumidores, keys y usuarios en `/api/gateway/**`; denegación por defecto y control por rol en el gateway; revocación inmediata de tokens tras un cambio de rol; usuarios RabbitMQ por servicio y memoria acotada en Redis.

### Changed (acceso y gateway)
- Realm: bloqueo a los 3 intentos, cliente `biohub-gateway` y usuario `sinrol1`. Requiere `KEYCLOAK_GATEWAY_CLIENT_SECRET` en `.env` y reimportar el realm.

### Added (acceso y gateway, 2)
- Circuit breaker por servicio destino en el gateway: 503 inmediato con `Retry-After` cuando un servicio falla varias veces seguidas.

### Changed (acceso y gateway, 2)
- CI: acciones de GitHub en versiones que corren con Node 24.

### Fixed (acceso y gateway)
- El proxy dejaba vacíos los adjuntos multipart.

### Added (iteración 15)
- Perfiles Spring `local` (MySQL del docker-compose) y `aiven` (MySQL administrado con SSL y verificación del CA) en `servicio-cientifico` y `servicio-consulta-publica`; `.env.example` por servicio.

### Changed
- README: la sección "10b. Despliegue" describe la nueva topología (MySQL en Aiven; RabbitMQ, Redis y Keycloak en Docker local).
