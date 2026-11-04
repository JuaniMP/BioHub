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
| 11 | Decisión de despliegue: webs en Vercel; backend on-premise en 2 VMs (D11) | Equipo | Reemplazada en la iteración 15 (MySQL en Aiven) |
| 12 | Reparto de ramas por persona en el README (Juanita: ubicación/consulta pública; Santiago: seguridad/acceso + infra compartida; Hernando: registro; Nicolás: reportes/equipos) | Tech Lead | Hecho |
| 13 | Preparar despliegue (Dockerfiles, compose prod, Keycloak prod) | devops | Reemplazada por la iteración 15 |

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
# Iteraciones 13 y 14 (retiradas)

Despliegue de bases, Redis y RabbitMQ en VMs locales con una VPN de malla, y su endurecimiento. **Retirado en la iteración 15**: la VPN se eliminó del proyecto y MySQL pasa a Aiven. Se conserva de esas iteraciones:
- PR #2 fusionado a `develop` (5a8d206): eventos RabbitMQ de punta a punta, usuarios RabbitMQ por servicio; `mvn -B verify` 75 tests, 0 fallos.
- Contrato del evento `hallazgo.registrado` (registro/Hernando publica, Juanita y Nicolás consumen) en `04-contrato-api.md`.
- Cada integrante conserva sus responsabilidades: no se toca código de registro, equipos/reportes ni seguridad/acceso.

---
# Iteración 15: retiro de Tailscale, MySQL en Aiven, CI y cierre de la parte de Juanita

**Fecha:** 2026-11-03 · **Rama:** `feature/ubicacion-consulta-publica` (en `~/biohub`; se retiró el worktree `~/biohub-ubicacion`) · **Modo de presupuesto:** equilibrado, sin tope.

**Objetivo:** quitar Tailscale de todo el repo; conectar los servicios de Juanita a MySQL en Aiven (SSL) con perfil `local` (Docker) y perfil `aiven`; Flyway trazable; GitHub Actions (build web-publica, JUnit, Bruno); completar y verificar la parte de Juanita contra SRS/SAD.

**Alcance:** solo la parte de Juanita: `servicio-cientifico` módulo `ubicacion`, `servicio-consulta-publica`, `web-publica`. Otros servicios solo se tocan lo mínimo para que el de Juanita funcione, y se marca en la matriz. Tailscale se retira de todo el repo (autorizado por Juanita).

**Fuentes de verdad:** `docs/entregas/` (SRS, SAD y C4 en PDF + texto extraído; no versionado, excluido en `.git/info/exclude`).

**Decisiones**
- D-I15-1: se mantiene la convención de Flyway por fecha (`V1__base.sql`, `VAAAAMMDD_HHMM__...`, `out-of-order`) porque el esquema `cientifico` lo comparten dos integrantes. Bases de Aiven nuevas: sin baseline.
- D-I15-2: Aiven con un servicio MySQL y una base + usuario por servicio; URL configurable para poder separar escritura y lectura en servicios distintos (riesgo ASR2/ASR4, lo documenta arquitectura).
- D-I15-3: RabbitMQ, Redis y Keycloak siguen en Docker local; en CI, como contenedores.
- D-I15-4: commits solo a nombre de Juanita, sin Co-Authored-By; sin push/merge.

| # | Tarea | Responsable | Modelo | Estado |
|---|---|---|---|---|
| 15a | Reconocimiento, documentos a `docs/entregas/`, línea base de tests | Tech Lead | opus/medium | Hecho |
| 15b | Matriz de trazabilidad `trazabilidad.md` | product-owner | sonnet/medium | Hecho (13 de Juanita: 4 Cumple, 9 Parcial; 14 ambigüedades A1–A14) |
| 15c | Verificación del código contra el SAD; impacto de Aiven/Flyway en ASR; quitar Tailscale de `03-arquitectura.md` | arquitecto-software | opus/high | Hecho (504a36f; 25 hallazgos: de Juanita 2 Altos ARQ-05, ARQ-06 y 11 Medios/Bajos; ARQ-12 y ARQ-15 corregidos en 8bdac84) |
| 15d | Quitar Tailscale (resto del repo); perfiles local/aiven con SSL; `.env.example` | devops | sonnet/medium | Hecho (fe1bb43, 52ac256; perfil aiven probado con SSL contra MySQL local, falta Aiven real) |
| 15e | Migraciones compatibles con Aiven; convención en README de cada servicio | dba | sonnet/high | Hecho (6d64b9e, 62f304c; 6 migraciones compatibles, sin baseline) |
| 15f | GitHub Actions + colecciones Bruno | devops + qa-tester | sonnet/medium | Hecho (Bruno 44/44 requests, 77/77 tests en local; actionlint OK; job en GitHub sin ejecutar aún) |
| 15g | Completar la parte de Juanita (SEC-J-01..03 y faltantes) | backend-senior / frontend-senior | sonnet/high · sonnet/medium | Hecho (d92c920, 31fd86b, 98e0021, 0e959d2; 2 ciclos de corrección: ARQ-26 Alto + SEC-J-10/11/14/15/16, BUG-J2/J3) |
| 15h | Verificación final: build, tests, k6, auditoría, ASR en código | qa-tester · ciberseguridad · arquitecto | sonnet/medium · opus/high · opus/high | Hecho (QA APROBADO; seguridad APROBADO CON OBSERVACIONES, 0 Crít/Alt; ASR1 y ASR4 Parciales por D42; cientifico 241/0/2 omitidos opt-in, consulta-publica 190/0) |

## Modelos y consumo
| Fase | USD acumulado | Agente que más consumió | Cambios de modelo (motivo) |
|---|---:|---|---|
| 15a Reconocimiento | 0.80 | sesión principal (opus) | — |
| 15b–15e Diseño, Tailscale, Aiven, Flyway | 9.36 | arquitecto-software (opus, 3.45) | — (todos según matriz equilibrado) |
| 15f–15g (sesión 2026-11-03, sin medir aparte) | — | — | — |
| 15h verificación + 2 ciclos (sesión 2026-11-04) | 10.89 en esta sesión (≥ 20.25 acumulado; 15f–15g no se midió) | ciberseguridad (opus, 4.08 en 2 tareas) | backend ciclo 2 sonnet/**medium** (matriz: high): solo reglas ArchUnit ya diseñadas por seguridad |
| 15h ciclo 1 re-verificación | (incluido en la fila anterior) | — | ciberseguridad opus/**medium** (matriz: high): solo re-verifica un diff acotado de su propia auditoría |

| 15h CI en GitHub + arreglo WebKit/Bruno (sesión 2026-11-04) | 13.80 en esta sesión (≥ 23.16 acumulado) | ciberseguridad (opus, 4.08) | — |

## Pendientes tras la 15h
- Corridas k6 que exige D42: degradación activa, 1 vs 2 réplicas en saturación (ARQ-30, ARQ-31) y par ASR4 `CIENTIFICA=0/1` (ARQ-32).
- SEC-J-12, SEC-J-13 (guarda de coordenadas en el publicador, sube de prioridad), SEC-J-04/05/07/08(3)/09, SEC-04/SEC-D-03 (un usuario AMQP por servicio, Santiago).
- ARQ-05 (segundo servicio Aiven sin evidencia), ARQ-07 (Hernando), ARQ-28/29/33, Aiven real sin probar. CI en GitHub: verde en run 41891195794 (740bb37), los 4 jobs.
- Condición D44: nada de registro (Hernando) entra sin pasar las reglas ArchUnit actuales.
- Usuario: purgar `consulta-publica.eventos.dlq` (76 mensajes de prueba de QA) y copiar las 2 líneas `BIOHUB_EVENTOS_*` de `.env.example` al `.env`.
