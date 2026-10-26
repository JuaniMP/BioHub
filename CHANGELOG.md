# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

## [Unreleased]

### Added
- Guía de uso de Tailscale y del despliegue en las 2 VMs: `docs/guia-tailscale.md`.
- Despliegue on-premise en dos VMs (`biohub-backend`, `biohub-datos`) conectadas por Tailscale, con scripts en `deploy/` (`desplegar.sh`, `generar-env.sh`, compose de backend y datos).
- Evento de prueba de RabbitMQ: `POST /api/cientifico/eventos/prueba`, `GET /api/publica/eventos/recibidos` y `GET /api/reportes/eventos/recibidos`.

### Changed
- README: la sección "10b. Despliegue" pasa de "pendiente" a "implementado".
- Keycloak se despliega con `sslRequired=NONE` (decisión D13), solo accesible por la tailnet.
