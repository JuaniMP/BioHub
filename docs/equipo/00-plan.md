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
| 11 | Decisión de despliegue: webs en Vercel; backend on-premise en 2 VMs (backend y datos) con contenedores Docker; Tailscale entre VMs, para el equipo y Funnel para publicar (D11) | Equipo | Decidido |
| 12 | Reparto de ramas por persona en el README (Juanita: ubicación/consulta pública; Santiago: seguridad/acceso + infra compartida; Hernando: registro; Nicolás: reportes/equipos) | Tech Lead | Hecho |
| 13 | Preparar despliegue (2 VMs, Tailscale + ACLs por etiquetas, Dockerfiles, compose prod, reverse proxy, Funnel, Keycloak prod, Vercel) | devops | Pendiente (antes de la primera entrega en línea) |

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
# Iteración: despliegue en 2 VMs + Tailscale + RabbitMQ (tarea 13, alcance 4a)

**Alcance acordado con Juanita (2026-10-21):** VMs nuevas en KVM local (1a); Tailscale con `tailscale up` y aprobación por enlace (2b); RabbitMQ desplegado con usuarios por servicio y evento de prueba de punta a punta (3a); sin Funnel, sin Keycloak en modo producción, sin Vercel (4a). Rama `feature/despliegue` → PR a `develop`.

**Decisiones del Tech Lead (D12)**
- VMs: `biohub-backend` (4 vCPU, 6 GB, 30 GB) y `biohub-datos` (2 vCPU, 4 GB, 40 GB), Ubuntu 24.04 cloud image, usuario `biohub`, red NAT `default` de libvirt.
- Acceso del equipo solo por la tailnet (IP 100.x de cada VM). VM datos publica MySQL en 3307-3310 sobre su IP de Tailscale. VM backend publica gateway 8080, Keycloak 8180 y panel RabbitMQ 15672 sobre su IP de Tailscale; el resto queda en la red Docker interna.
- Keycloak sigue en `start-dev` (H2) con `KC_HOSTNAME=http://biohub-backend:8180`; los contenedores resuelven `biohub-backend` con `extra_hosts`.
- RabbitMQ: exchange topic `biohub.eventos`, DLX `biohub.eventos.dlx`, colas `consulta-publica.eventos` y `reportes.eventos` (con `.dlq`) enlazadas a `prueba.#` y `hallazgo.#`. La topología la declara el código (Spring). Usuarios: `cientifico_mq` (solo publica), `consulta_mq` y `reportes_mq` (consumen su cola), `biohub` (administrador del panel). En local se sigue usando un único usuario.

| # | Tarea | Responsable | Estado |
|---|---|---|---|
| 13a | Dockerfiles, compose de backend y de datos, scripts de creación de VMs, VMs creadas | devops | Hecho |
| 13b | RabbitMQ: topología, evento de prueba publicado por científico y consumido por consulta-publica y reportes, tests | backend-senior | Hecho (71 tests; E2E local OK; nota: consumidores necesitan READ sobre biohub.eventos y biohub.eventos.dlx para los bindings) |
| 13c | Unir VMs a Tailscale (aprobación por enlace) + ACLs | Tech Lead + Juanita | Hecho (backend 100.76.214.94 tag:backend, datos 100.94.121.117 tag:datos) |
| 13d | Despliegue en las VMs y verificación de punta a punta | Tech Lead | Desplegado: 4 MySQL + 9 contenedores healthy; Hecho. Verificación final tras D13 (corrida por Juanita, 2026-10-22): token OK; 5 pings 200; sin token 401; evento con investigador1 403; evento con admin1 202 y recibido en consulta-publica y reportes; consola Keycloak y panel RabbitMQ 200 |
| 13e | QA del despliegue y del flujo de eventos | qa-tester | Hecho – Aprobado con observaciones (BUG-04 Bajo corregido) |
| 13f | Auditoría de seguridad del despliegue | ciberseguridad | Hecho – Aprobado con observaciones (0 Críticos/Altos; 4 Medios, 7 Bajos). SEC-D-05 y SEC-D-10 corregidos; SEC-D-11 aceptado (definitions.json debe ser 644 para el uid de rabbitmq; protegido por el home 750) |

**Verificación final del Tech Lead (2026-10-22):** `mvn -B verify` OK – 75 tests (73 ejecutados, 2 opt-in omitidos), 0 fallos; web-interna 2/2 y web-publica 2/2; E2E por Tailscale OK (ver 13d).

**Pendientes de seguridad (antes de datos reales, nuevos integrantes o Funnel; pasan a Alto si cambia alguna de esas condiciones)**
- SEC-D-01 (Medio): cualquier miembro con SSH es root en las VMs y puede leer los `.env` → usuario `equipo` sin sudo/docker, Tailscale SSH en modo `check`, usuario MySQL de solo lectura.
- SEC-D-02 (Medio): D13 + realm `master` sin protección de fuerza bruta → `bruteForceProtected=true`; volver a `sslRequired=external` con HTTPS.
- SEC-D-03 (Medio): consumidores con `write` sobre `biohub.eventos` (pueden falsificar eventos) → limitar `write` y validar `user_id` del mensaje. Hacer antes de `hallazgo.registrado`.
- SEC-D-04 (Medio): ACL amplia (`autogroup:member:*`, MySQL abierto a miembros) → grupos con nombre y política versionada en `deploy/tailscale/policy.hujson`.
- Bajos: SEC-D-06 backups sin cifrar ni copia externa (instalar timer), SEC-D-07 DLQ sin límite, SEC-D-08 endurecimiento de contenedores, SEC-D-09 llave SSH sin passphrase. Detalle en `08-reporte-seguridad.md`.
| 13g | Guía de uso de Tailscale y del despliegue para el equipo | documentador-tecnico | Hecho (`docs/guia-tailscale.md`, README 10b, CHANGELOG) |

**Ajustes del Tech Lead durante el despliegue**
- `desplegar.sh`: corregido `trap RETURN` que rompía `subir_env`.
- VM datos: `userland-proxy: false` para conservar la IP de origen en MySQL (parametrizado por VM en `user-data.tpl`/`crear-vms.sh`).
- VM backend: alias de red `biohub-backend` en Keycloak (`KC_HTTP_PORT=8180`) en lugar de `extra_hosts`.
- D13 (aprobada por Juanita): `sslRequired=NONE` en Keycloak, solo accesible por la tailnet. Registrada en `03-arquitectura.md`.
