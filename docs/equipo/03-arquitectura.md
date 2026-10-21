# 03 – Arquitectura

## Estilo (decidido por el equipo, no se cambia)
**Arquitectura basada en servicios + API Gateway + eventos asíncronos en topología broker.**

```
 web-interna (5173) ──┐  JWT (Keycloak, PKCE)
                      ├──► api-gateway (8080) ──► servicio-cientifico (8081) ──► mysql-transaccional:cientifico
 web-publica (5174) ──┘  sin login (API Key      ├► servicio-equipos (8082) ────► mysql-transaccional:equipos
                         + rate limit: pendiente)├► servicio-consulta-publica ──► mysql-lectura + redis (caché)
                                                 │   (8083)
                                                 └► servicio-reportes (8084) ───► mysql-analitica
          api-gateway ──► mysql-gateway (API Keys / límites)

 cientifico, consulta-publica, reportes ◄──► RabbitMQ (exchange topic "biohub.eventos")
 Keycloak (8180): realm "biohub" – emite JWT de 5 min con aud=biohub-api
```

## Componentes
| Componente | Responsabilidad | BD | Mensajería |
|---|---|---|---|
| api-gateway | Enrutar por prefijo `/api/<prefijo>/**`, validar JWT, CORS, (pendiente) API Keys y rate limiting | mysql-gateway | – |
| servicio-cientifico | Módulo **registro** (hallazgos, fichas, historial) y módulo **ubicacion** (cifrado, llaves, mapa, búsqueda) | mysql-transaccional / `cientifico` | publica eventos |
| servicio-equipos | Equipos de trabajo | mysql-transaccional / `equipos` | – |
| servicio-consulta-publica | Lectura pública (modelo de lectura alimentado por eventos) | mysql-lectura | consume eventos; caché Redis |
| servicio-reportes | Reportes y analítica | mysql-analitica | consume eventos |

## Estructura de paquetes
`co.edu.unbosque.biohub.<servicio>.{controller, service, domain, infrastructure, config}`.
En servicio-cientifico: `cientifico.registro.{controller,service,domain,infrastructure}` y
`cientifico.ubicacion.{...}`; `cientifico.config` y `cientifico.controller` son compartidos.
**Regla:** un módulo no importa clases internas del otro; se comunican por una clase pública de `service` o por eventos.

## Seguridad
- Keycloak realm `biohub`. Roles de realm: investigador_campo, auditor_cientifico, cientifico_alto_rango, analista_ambiental, administrador.
- Clientes: `web-interna` (público, Authorization Code + PKCE S256, sin password grant);
  `biohub-pruebas` (público, SOLO password grant, **solo desarrollo** para obtener tokens por consola).
- Ambos clientes agregan `aud=biohub-api`. Todos los servicios validan firma, `iss`, `exp` y `aud`.
- El gateway valida el JWT y **cada servicio lo vuelve a validar** (defensa en profundidad).
- Roles → authorities `ROLE_<rol>` (conversor `KeycloakJwtAuthenticationConverter` en cada servicio).
- `/api/publica/**` (GET) no exige JWT: se protegerá con API Key + rate limiting en `ApiKeyRateLimitFilter` (pendiente).
- Puertos de infraestructura publicados solo en 127.0.0.1. Contraseñas en `.env` (no versionado); las de los usuarios de prueba se inyectan al realm con el placeholder `${KEYCLOAK_TEST_USERS_PASSWORD}`.

## Persistencia
- Flyway en cada servicio, `V1__base.sql` vacía. Cada historia agrega `V<n>__descripcion.sql`. Hibernate `ddl-auto=validate`.
- mysql-transaccional: un usuario por esquema con permisos solo sobre su esquema.

## Decisiones (ADR resumidos)
| # | Decisión | Motivo |
|---|---|---|
| D1 | Gateway con Spring MVC + RestClient (sin Spring Cloud Gateway) | Requisito del curso; control explícito del reenvío |
| D2 | Servicios se ejecutan en el host; Docker Compose solo para infraestructura | Ciclo de desarrollo más rápido; dockerizar servicios queda para devops |
| D3 | Los servicios leen `../.env` con `spring.config.import` | Un solo archivo de configuración local para Compose y Spring |
| D4 | Sin módulo Maven común: el conversor de JWT se duplica por servicio | Independencia entre servicios (estilo basado en servicios) |
| D5 | Spring Boot 3.5.16 (última 3.x estable), Java 21 | Requisito: Spring Boot 3.x |
| D6 | Cliente `biohub-pruebas` con password grant solo en local | Permite pruebas automatizadas por consola sin navegador |
| D7 | Servicios escuchan en 127.0.0.1 por defecto (`SERVER_ADDRESS`) | Obliga a pasar por el gateway (SEC-01) |
| D8 | El gateway lee el cuerpo con límite (10MB) y no lee cuerpo en GET/HEAD/OPTIONS/DELETE | Evita DoS por memoria (SEC-03) |
| D9 | BUG-01 de QA vs SEC-06 de seguridad (contradicción sobre path traversal): **decide Tech Lead** → era un falso positivo de curl; se añade igualmente validación de rutas en el proxy (400) | Defensa en profundidad |
| D10 | Pendientes de seguridad asignados a historias: usuarios RabbitMQ por servicio (SEC-04), API Key como ROLE_API_CLIENT (SEC-05), usuario Flyway vs aplicación (SEC-11), reglas por rol + `denyAll` por defecto (SEC-02) | Requieren modelo/endpoints que aún no existen |
| D11 | **Despliegue:** webs (web-interna y web-publica) en **Vercel** (plan Hobby, gratis). Backend on-premise en **DOS máquinas virtuales**: **VM de backend** (gateway, servicios, Keycloak, RabbitMQ, Redis) y **VM de datos** (los 4 MySQL + BD de Keycloak), cada componente como contenedor Docker. **Tailscale** conecta las VMs entre sí y al equipo por una red privada cifrada; **Tailscale Funnel** publica solo el gateway y el login de Keycloak | Costo cero; separar los datos en otra VM limita el daño si se compromete el backend (la VM de datos solo acepta conexiones de la VM de backend); Tailscale evita abrir puertos en el router y cifra el tráfico entre VMs |

### Notas de despliegue (D11) – para devops

**Topología**
```
                         Internet
                            │ HTTPS
          ┌─────────────────┴──────────────────┐
   Vercel (gratis)                  Tailscale Funnel (https://biohub-backend.<tailnet>.ts.net)
   web-interna, web-publica                    │ solo /api/** y /realms/**, /resources/**
                                               ▼
 ┌──────────── VM 1: biohub-backend (Ubuntu Server) ─────────────┐
 │  Caddy/Nginx (reverse proxy, único punto de entrada)          │
 │    ├► api-gateway ─► cientifico / equipos / consulta-publica / reportes
 │    └► keycloak                                                │
 │  rabbitmq, redis   (red Docker interna, sin puertos públicos) │
 └───────────────────────────────┬───────────────────────────────┘
                                 │ Tailscale (red privada cifrada)
                                 │ solo puertos MySQL, solo desde biohub-backend
 ┌───────────────────────────────▼───────────────────────────────┐
 │ VM 2: biohub-datos (Ubuntu Server)                            │
 │  mysql-transaccional, mysql-lectura, mysql-analitica,         │
 │  mysql-gateway, BD de Keycloak (producción)                   │
 │  Puertos publicados SOLO en la IP de Tailscale (100.x.y.z)    │
 └───────────────────────────────────────────────────────────────┘
        ▲ Tailscale: los 4 integrantes (SSH, consola Keycloak, panel RabbitMQ, clientes MySQL)
```

**Tailscale: tres usos**
1. **VM de backend → VM de datos.** Los servicios se conectan a MySQL por el nombre de Tailscale de la VM de datos (MagicDNS), p. ej. `MYSQL_TRANSACCIONAL_HOST=biohub-datos`. No hay cambios de código: los hosts ya son variables de entorno.
2. **Acceso privado del equipo.** Los 4 integrantes entran a las dos VMs (Tailscale SSH, consola de Keycloak, panel de RabbitMQ, MySQL) sin que nada de eso esté en internet ni haya puertos abiertos en el router.
3. **Publicación (Funnel).** Solo el reverse proxy de la VM de backend, con HTTPS automático y URL fija. Funnel solo admite los puertos 443/8443/10000; por eso todo pasa por un único proxy que enruta por ruta.

**Reglas de seguridad**
- **ACLs de Tailscale con etiquetas:** `tag:backend` (VM 1), `tag:datos` (VM 2) y el grupo `group:equipo` (4 integrantes). Reglas: `tag:backend` → `tag:datos` solo puertos MySQL; `group:equipo` → ambas VMs (SSH y paneles); **nadie más** llega a `tag:datos`. Verificación en dos pasos en las cuentas.
- **VM de datos:** MySQL publica sus puertos **solo en la IP de Tailscale** (`100.x.y.z:3307:3306`, etc.), nunca en `0.0.0.0`; `ufw` deniega todo lo entrante salvo la interfaz `tailscale0`. Sin Funnel.
- **VM de backend:** ningún `ports:` salvo el reverse proxy en `127.0.0.1` (Funnel se conecta localmente); servicios con `SERVER_ADDRESS=0.0.0.0` solo dentro de la red Docker; `ufw` igual que arriba.
- **Usuarios de MySQL:** cada usuario de aplicación limitado al host de la VM de backend (IP de Tailscale) en vez de `'%'`; root solo local.
- Keycloak en modo producción (`start`, BD en la VM de datos, `KC_HOSTNAME` = URL de Funnel, `KC_PROXY_HEADERS=xforwarded`); consola admin solo por la tailnet. Eliminar el cliente `biohub-pruebas` y los usuarios de prueba.
- Agregar los dominios de Vercel a `CORS_ORIGENES` del gateway y a `redirectUris`/`webOrigins` del cliente `web-interna`. En Vercel: `VITE_API_URL` y `VITE_KEYCLOAK_URL` = URL de Funnel (HTTPS obligatorio).
- Copias de seguridad programadas de los volúmenes MySQL de la VM de datos.

**Recursos sugeridos**
| VM | vCPU | RAM | Disco |
|---|---|---|---|
| biohub-backend | 4 | 6 GB | 30 GB |
| biohub-datos | 2 | 4 GB | 40 GB |

Pueden estar en el mismo equipo físico (VirtualBox/Proxmox/Hyper-V) o en equipos distintos: Tailscale las conecta igual. Limitación aceptada: la app solo está en línea cuando ambas VMs están encendidas.
