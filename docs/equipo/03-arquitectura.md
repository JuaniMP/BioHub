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
| D11 | **Despliegue:** webs (web-interna y web-publica) en **Vercel** (plan Hobby, gratis); backend completo (gateway, servicios, MySQL, Redis, RabbitMQ, Keycloak) **on-premise** con Docker Compose en una sola máquina, expuesto a internet con un **túnel** (sin abrir puertos del router) | Costo cero; mismo compose que en desarrollo; la separación entre servicios es lógica (esquema + usuario por servicio), no requiere varias máquinas |

### Notas de despliegue (D11) – para devops
- Solo se expone por el túnel un **reverse proxy** (Caddy/Nginx) que enruta `/api/**` → api-gateway y `/realms/**`, `/resources/**` → Keycloak. MySQL, Redis, RabbitMQ y los servicios **nunca** se exponen.
- Túnel sugerido: **Tailscale Funnel** (gratis, hostname estable `https://<maquina>.<tailnet>.ts.net`). Alternativa: Cloudflare Tunnel *con nombre* (requiere un dominio propio en Cloudflare, ~10 USD/año). **No** usar los "quick tunnels" de Cloudflare: la URL cambia en cada reinicio y rompe Keycloak y la config de Vercel.
- Keycloak en modo producción (`start`, BD propia, `KC_HOSTNAME` = URL pública, `KC_PROXY_HEADERS=xforwarded`); eliminar el cliente `biohub-pruebas` y los usuarios de prueba.
- Agregar los dominios de Vercel a `CORS_ORIGENES` del gateway y a `redirectUris`/`webOrigins` del cliente `web-interna`.
- En Vercel: variables `VITE_API_URL` y `VITE_KEYCLOAK_URL` con la URL pública del túnel (HTTPS obligatorio: el navegador bloquea llamadas http desde una página https).
- Limitación aceptada: la app solo está en línea cuando la máquina on-premise está encendida.
