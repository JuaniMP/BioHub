# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

## [Unreleased]

### Removed
- Retirado Tailscale; MySQL pasa a Aiven. Se eliminan `deploy/` (VMs KVM, scripts de despliegue, ACL), `docs/guia-tailscale.md` y las VMs `biohub-backend`/`biohub-datos`.

### Added
- Evento de prueba de RabbitMQ: `POST /api/cientifico/eventos/prueba`, `GET /api/publica/eventos/recibidos` y `GET /api/reportes/eventos/recibidos`.

### Added (iteración 15)
- Perfiles Spring `local` (MySQL del docker-compose) y `aiven` (MySQL administrado con SSL y verificación del CA) en `servicio-cientifico` y `servicio-consulta-publica`; `.env.example` por servicio.

### Changed
- README: la sección "10b. Despliegue" describe la nueva topología (MySQL en Aiven; RabbitMQ, Redis y Keycloak en Docker local).
