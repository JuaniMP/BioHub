# 08 – Reporte de seguridad: esqueleto BioHub

**Revisor:** ciberseguridad · **Fecha:** 2026-10-21 · **Alcance:** esqueleto completo (sin lógica de negocio), tarea 8c del plan.

## Resumen

| Severidad | Cantidad |
|---|---|
| Crítico | 0 |
| Alto | 0 |
| Medio | 4 |
| Bajo | 10 |
| Informativo | 6 |

**Veredicto: APROBADO CON OBSERVACIONES** (no hay hallazgos Críticos ni Altos). Los 4 hallazgos **Medio** no son explotables hoy porque no existen endpoints de negocio, pero son decisiones que las próximas historias van a heredar. Hay que corregirlos **antes** de la primera historia que exponga datos (en especial las de ubicaciones y API pública).

### Cómo se revisó
- **Análisis estático:** realm de Keycloak, `SecurityConfig` del gateway y de los 4 servicios, `KeycloakJwtAuthenticationConverter` (idéntico en los 5 módulos, comprobado con `diff`), `ProxyService`, `ProxyController`, `GatewayExceptionHandler`, `RestClientConfig`, `ApiKeyRateLimitFilter`, todos los `application.yml`, `docker-compose.yml`, `.env.example`, `.gitignore`, script de init de MySQL, ambas webs (`keycloak-js`, clientes HTTP, componentes) y los `pom.xml`.
- **Pruebas dinámicas (solo lectura, contra el entorno local):** path traversal y rutas no normalizadas en el gateway, CORS (origen permitido y ajeno, preflight), cabeceras de respuesta, validación de `aud` con un token de otro cliente (`admin-cli`), password grant en `web-interna`, `redirect_uri` ajeno y petición sin PKCE en Keycloak, cuerpo de 20 MB sin autenticación, interfaces de escucha (`ss -ltnp`), usuarios y permisos de RabbitMQ, usuarios y grants de MySQL.
- **Secretos:** `git status --porcelain --ignored` y `git check-ignore`. Se buscaron los valores de las claves sensibles del `.env` en todos los archivos que se versionarían, sin imprimirlos. **No hay coincidencias.** `.env` está ignorado (`.gitignore:14`) y tiene permisos `600`. Las claves de `.env` y `.env.example` coinciden.
- **Dependencias:** `npm audit` en `web-interna` y `web-publica` da **0 vulnerabilidades**. Para Maven se revisaron las versiones empaquetadas en los JAR (no hay escáner de CVE instalado y no se instaló ninguno).

---

## Hallazgos

### SEC-01 · Medio · A01 Broken Access Control / A05 Security Misconfiguration
**Los servicios y el gateway escuchan en todas las interfaces (0.0.0.0), así que se puede saltar el gateway.**

- **Archivos:** `servicio-cientifico/src/main/resources/application.yml:31`, `servicio-equipos/.../application.yml:26`, `servicio-consulta-publica/.../application.yml:40`, `servicio-reportes/.../application.yml:31`, `api-gateway/.../application.yml:26` (solo `server.port`, sin `server.address`).
- **Problema:** `ss -ltnp` muestra que los puertos 8080 a 8084 escuchan en `*`. La infraestructura de Docker y las webs sí están en `127.0.0.1`. `curl http://127.0.0.1:8083/api/publica/ping` responde 200 sin pasar por el gateway. La documentación (03-arquitectura) dice que la infraestructura solo se publica en 127.0.0.1, pero no aplica la misma regla a los procesos Java.
- **Por qué importa:** la API Key y el rate limiting de `/api/publica/**` se van a aplicar **solo en el gateway**. Cualquiera que llegue al puerto 8083 (otro equipo en la misma red Wi‑Fi del laboratorio, si el firewall del host lo permite) se los salta. Lo mismo pasará con cualquier control que en el futuro solo exista en el gateway (cabeceras, auditoría, límites de tamaño). Además todo se expone por HTTP en la LAN.
- **Corrección:** en cada servicio, escuchar solo en loopback. En un despliegue real, aislar la red (los servicios no se publican; solo el gateway).
  ```yaml
  server:
    address: ${SERVER_ADDRESS:127.0.0.1}
    port: ${SERVICIO_CONSULTA_PUBLICA_PORT:8083}
  ```
  Haz lo mismo en el gateway si no se necesita acceder desde otro equipo. Agrega `SERVER_ADDRESS` a `.env.example` y documéntalo en 03-arquitectura.
- **Esfuerzo:** S · **Responsable sugerido:** Tech Lead / devops

### SEC-02 · Medio · A01 Broken Access Control
**No está activada la seguridad por método (`@EnableMethodSecurity`) y la autorización por defecto es "cualquier usuario autenticado".**

- **Archivos:** `servicio-*/src/main/java/.../config/SecurityConfig.java` (cientifico, equipos y reportes en las líneas 24-26; consulta-publica en las 24-28) y `api-gateway/.../config/SecurityConfig.java:42`. `grep` no encuentra `EnableMethodSecurity` ni `PreAuthorize` en el repositorio.
- **Problema:** todas las cadenas terminan en `.anyRequest().authenticated()`. Si un desarrollador protege un endpoint con `@PreAuthorize("hasRole('cientifico_alto_rango')")`, **la anotación se ignora sin ningún aviso** mientras no exista `@EnableMethodSecurity`. Cualquier rol (por ejemplo `investigador_campo`) podría entonces leer ubicaciones protegidas. El gateway tampoco filtra por rol: cualquier token válido llega a cualquier servicio.
- **Por qué importa:** es un fallo que deja pasar todo justo en el activo más sensible (ubicaciones de especies protegidas). Las pruebas con cualquier token "pasarían".
- **Corrección:**
  1. Agregar `@EnableMethodSecurity` en el `SecurityConfig` de cada servicio.
  2. Denegar por defecto las rutas sensibles en la propia cadena, para que también estén protegidas si alguien olvida la anotación:
     ```java
     @Configuration
     @EnableWebSecurity
     @EnableMethodSecurity
     public class SecurityConfig {
         ...
         .authorizeHttpRequests(auth -> auth
             .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
             .requestMatchers("/api/cientifico/ubicaciones/**").hasRole("cientifico_alto_rango")
             .requestMatchers("/api/cientifico/**").hasAnyRole("investigador_campo", "auditor_cientifico",
                                                              "cientifico_alto_rango", "administrador")
             .anyRequest().denyAll())
     ```
  3. Agregar una prueba por servicio que demuestre que un rol sin permiso recibe 403. Por ejemplo, un `@PreAuthorize` de prueba con `jwt().authorities(...)` de otro rol.
- **Esfuerzo:** S · **Responsable sugerido:** Tech Lead (plantilla), cada desarrollador por historia

### SEC-03 · Medio · A04 Insecure Design (DoS) / A05
**El gateway lee el cuerpo completo en memoria (`byte[]`) sin límite, también en la ruta pública anónima.**

- **Archivos:** `api-gateway/.../controller/ProxyController.java:24-27` (`@RequestBody(required = false) byte[] cuerpo`), `ProxyService.java:66-68` y `ProxyService.java:79` (`readAllBytes()` de la respuesta).
- **Problema:** se confirmó que un `GET /api/publica/ping` **sin token** con un cuerpo de 20 MB es aceptado y leído por el gateway (respuesta 502 tras ~1 s). Ni Spring MVC ni Tomcat limitan el tamaño de un cuerpo que no es formulario. Las rutas públicas son solo GET, pero el `permitAll` por método no impide que un GET traiga cuerpo.
- **Por qué importa:** unas pocas peticiones concurrentes con cuerpos grandes agotan el heap del gateway y tumban **todos** los servicios, porque el gateway es el punto único de entrada. Lo puede hacer cualquier persona anónima.
- **Corrección:**
  - Rechazar en el filtro del gateway, antes de leer el cuerpo, cuando el tamaño excede un límite, y no permitir cuerpo en las rutas públicas:
    ```java
    long max = props.tamanoMaximoCuerpo().toBytes();               // p. ej. 1 MB, configurable
    long declarado = request.getContentLengthLong();
    boolean esPublica = rutasPublicas.matches(request);
    if ((esPublica && (declarado > 0 || request.getHeader("Transfer-Encoding") != null))
            || declarado > max) {
        response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value());
        return;
    }
    ```
  - Para los cuerpos con `Transfer-Encoding: chunked` (sin `Content-Length`), reenviar en streaming (`InputStream` con un `LimitedInputStream`) en lugar de `byte[]`. Ajustar `server.tomcat.max-swallow-size` y `max-http-form-post-size`.
  - Para las futuras subidas de fotos o fichas: usar `spring.servlet.multipart.max-file-size` / `max-request-size` en el servicio y en el gateway.
- **Esfuerzo:** M · **Responsable sugerido:** Tech Lead (gateway)

### SEC-04 · Medio · A05 Security Misconfiguration / A01 (mínimo privilegio)
**Todos los servicios comparten un único usuario de RabbitMQ con rol `administrator` y permisos `.* .* .*`.**

- **Archivos:** `docker-compose.yml:105-106` (`RABBITMQ_DEFAULT_USER/PASS`), `servicio-cientifico/.../application.yml:18-22`, `servicio-consulta-publica/.../application.yml:27-31`, `servicio-reportes/.../application.yml` (`spring.rabbitmq`), `.env.example:49-50`.
- **Problema:** `rabbitmqctl list_users` muestra `biohub [administrator]`, y `list_permissions` muestra `biohub .* .* .*`. El servicio de consulta pública (el más expuesto a Internet) tiene credenciales de administrador del broker.
- **Por qué importa:** con la topología broker, `consulta-publica` alimenta su modelo de lectura con los eventos de `cientifico`. Si se compromete cualquier servicio, se pueden **publicar eventos falsos** (por ejemplo, un hallazgo con ubicación exacta que acabe en el portal público), leer colas con eventos de ubicaciones, borrar colas o crear usuarios desde el panel de administración.
- **Corrección:** un usuario por servicio con permisos mínimos, y el administrador solo para el panel. Por ejemplo, con un `definitions.json` montado en `/etc/rabbitmq/definitions.json` y `load_definitions`:
  ```json
  "users": [
    {"name":"cientifico_amqp","password_hash":"...","tags":[]},
    {"name":"consulta_amqp","password_hash":"...","tags":[]},
    {"name":"reportes_amqp","password_hash":"...","tags":[]}
  ],
  "permissions": [
    {"user":"cientifico_amqp","vhost":"/","configure":"^$","write":"^biohub\\.eventos$","read":"^$"},
    {"user":"consulta_amqp","vhost":"/","configure":"^consulta\\..*","write":"^consulta\\..*","read":"^(biohub\\.eventos|consulta\\..*)$"},
    {"user":"reportes_amqp","vhost":"/","configure":"^reportes\\..*","write":"^reportes\\..*","read":"^(biohub\\.eventos|reportes\\..*)$"}
  ]
  ```
  Con `configure: ^$`, el exchange debe declararse con el admin (definitions) y no desde `RabbitConfig`. También se puede dar a cientifico `configure` solo sobre `^biohub\.eventos$`. Añadir `RABBITMQ_CIENTIFICO_USER/PASSWORD`, etc. a `.env.example`. Además, los consumidores deben validar el esquema del evento (ver Requisitos).
- **Esfuerzo:** M · **Responsable sugerido:** Tech Lead / devops

---

### SEC-05 · Bajo · A04 Insecure Design
**`ApiKeyRateLimitFilter`: la ubicación sirve, pero el diseño actual deja pasar las peticiones cuando el filtro falla (fail-open).**

- **Archivos:** `api-gateway/.../config/SecurityConfig.java:41` (`permitAll()` para `/api/publica/**`) y `:43` (`addFilterBefore(..., BearerTokenAuthenticationFilter.class)`). `ApiKeyRateLimitFilter.java:30-33`.
- **Evaluación de la ubicación:**
  - Bien: el filtro está dentro de la cadena de seguridad, después de `CorsFilter` (los preflight no llegan a él y los 401/429 llevarán cabeceras CORS) y **antes** de leer el cuerpo y de validar el JWT, lo que es barato frente a DoS. El doble registro está bien desactivado con `FilterRegistrationBean.setEnabled(false)`.
  - Problema 1: las rutas públicas son `permitAll()`. Toda la protección dependerá de que el filtro **rechace** activamente las peticiones. Si hay un bug, una excepción atrapada o una ruta no contemplada, la petición pasa sin control.
  - Problema 2: si el filtro decide qué es público con `request.getRequestURI().startsWith(...)`, puede no coincidir con el `RequestMatcher` de Spring Security (URI cruda contra ruta normalizada).
  - Problema 3: al ir antes de `BearerTokenAuthenticationFilter`, no conoce al usuario. Un rate limit **por usuario** para rutas con JWT necesitaría un segundo filtro después de la autenticación (`addFilterAfter(..., AuthorizationFilter.class)` o después de `BearerTokenAuthenticationFilter`).
- **Corrección (fail-closed):** que el filtro **autentique** la API Key y que la regla exija esa autenticación:
  ```java
  // SecurityConfig
  RequestMatcher publicas = new OrRequestMatcher(props.rutasPublicas().stream()
          .map(p -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/" + p + "/**"))
          .toList());
  ...
  .requestMatchers(publicas).hasRole("API_CLIENT")   // ya no permitAll
  .addFilterBefore(new ApiKeyRateLimitFilter(publicas, servicioApiKeys, limitador), BearerTokenAuthenticationFilter.class)

  // ApiKeyRateLimitFilter
  if (!publicas.matches(request)) { chain.doFilter(request, response); return; }
  String key = request.getHeader("X-API-Key");
  Optional<ClienteApi> cliente = servicioApiKeys.validar(key);      // compara hash con MessageDigest.isEqual
  if (cliente.isEmpty()) { response.sendError(401); return; }
  if (!limitador.permitir(cliente.get().id(), request.getRemoteAddr())) {
      response.setHeader("Retry-After", "60"); response.sendError(429); return;
  }
  SecurityContextHolder.getContext().setAuthentication(
      new ApiKeyAuthenticationToken(cliente.get(), List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"))));
  chain.doFilter(request, response);
  ```
  Así, si el filtro no autentica, Spring Security responde 401.
- **Esfuerzo:** S (ahora, como contrato del punto de extensión) · **Responsable sugerido:** Tech Lead / responsable de la historia de API Keys

### SEC-06 · Bajo · A03 Injection / A10 SSRF (defensa en profundidad)
**`ProxyService` arma la URL destino con la URI cruda (`getRequestURI()`) y depende solo del `StrictHttpFirewall`.**

- **Archivos:** `api-gateway/.../service/ProxyService.java:40-48` (`resolverDestino`), `:51` (`request.getRequestURI()`), `:56-60` (`UriComponentsBuilder ... build(true)`).
- **Problema:** Spring Security autoriza con la ruta **normalizada**, pero el proxy enruta con la URI **cruda**. Hoy no se puede explotar: se probaron `/api/cientifico/../publica/ping`, `/api/publica/%2e%2e/cientifico/ping`, `..;/`, `//`, `;x=1`, y con y sin token todas fueron rechazadas por el firewall (401/400) sin llegar al reenvío. El host destino sale de la configuración (no hay SSRF) y las redirecciones no se siguen (`RestClientConfig.java:18`). Aun así, la seguridad del enrutamiento depende de una configuración por defecto que nadie debe relajar (por ejemplo `setAllowUrlEncodedSlash(true)` o `setAllowSemicolon(true)`).
- **Corrección:** validar en el propio `ProxyService` y fallar si la URI no está normalizada:
  ```java
  String ruta = request.getRequestURI();
  String normalizada = URI.create(ruta).normalize().getRawPath();
  if (!ruta.equals(normalizada) || ruta.contains("%2e") || ruta.contains("%2E")
          || ruta.contains("%2f") || ruta.contains("%2F") || ruta.contains(";") || ruta.contains("\\")) {
      return ResponseEntity.badRequest().build();
  }
  ```
  Agregar pruebas en `ProxyServiceTest` con estas rutas, y un comentario en `SecurityConfig` que prohíba relajar el `HttpFirewall`.
- **Esfuerzo:** S · **Responsable sugerido:** Tech Lead

### SEC-07 · Bajo · A05 Security Misconfiguration
**El proxy reenvía al servicio casi todas las cabeceras del cliente (lista de exclusión, no lista blanca), incluida `X-API-Key`, y deriva `X-Forwarded-Host` del `Host` del cliente.**

- **Archivos:** `api-gateway/.../service/ProxyService.java:25-29` (`CABECERAS_EXCLUIDAS`), `:83-92` (`copiarCabeceras`; `X-Forwarded-Host` en la línea 91).
- **Problema:** pasan hacia los servicios cabeceras como `X-Real-IP`, `X-Forwarded-Port`, `X-Forwarded-Prefix`, `X-Original-URL` y `X-API-Key`, y cualquier cabecera "de identidad" que alguien invente después (`X-Usuario`, `X-Roles`). `X-Forwarded-Host` = `getServerName()` lo controla el cliente.
- **Por qué importa:** hoy los servicios no confían en esas cabeceras (no hay `server.forward-headers-strategy`). Pero si una historia habilita `framework`/`native`, o usa `X-Real-IP` para auditoría o rate limiting, el cliente podrá suplantar la IP, el prefijo o el host. Esto influye en URLs generadas, redirecciones y logs. Reenviar la API Key también la expone innecesariamente a más componentes y logs.
- **Corrección:** excluir por prefijo y quitar la API Key, o mejor, usar lista blanca:
  ```java
  private static boolean excluida(String n) {
      return CABECERAS_EXCLUIDAS.contains(n) || n.startsWith("x-forwarded-") || n.equals("x-real-ip")
          || n.equals("x-api-key") || n.startsWith("x-original-") || n.startsWith("x-biohub-");
  }
  ...
  destino.set("X-Forwarded-Host", props.hostPublico());   // valor de configuración, no del cliente
  ```
- **Esfuerzo:** S · **Responsable sugerido:** Tech Lead

### SEC-08 · Bajo · A05 Security Misconfiguration
**Las excepciones no controladas del gateway terminan en 500, y los errores se enmascaran como 401.**

- **Archivos:** `api-gateway/.../controller/GatewayExceptionHandler.java:17` (solo maneja `ResourceAccessException`), `ProxyService.java:56-60`, y `SecurityConfig.java:38-42` (no permite `/error`).
- **Problema:** `GET /api/cientifico/ping?a=%zz` con token da **500**, porque `build(true)` lanza `IllegalArgumentException`. El cuerpo es genérico gracias a `include-message: never`. Sin token, el mismo error y los rechazos del firewall llegan a `/error`, que exige autenticación, y responden **401** en vez de 400/500. No filtra información, pero confunde el diagnóstico y las métricas, y oculta posibles ataques en los logs.
- **Corrección:**
  ```java
  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail peticionInvalida(IllegalArgumentException ex) {
      return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Petición mal formada.");
  }
  ```
  Y en la cadena: `.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`. Es seguro porque `include-stacktrace` e `include-message` están en `never`. Aplicarlo también en los servicios.
- **Esfuerzo:** S · **Responsable sugerido:** Tech Lead

### SEC-09 · Bajo · A07 Identification and Authentication Failures
**Realm: `admin-cli` del realm `biohub` permite password grant, no hay eventos de auditoría y la política de contraseñas es mínima.**

- **Archivos:** `infra/keycloak/realm-biohub.json:56` (lista de clientes; `admin-cli` no se declara y queda con sus valores por defecto), `:17` (`passwordPolicy`), y la ausencia de `eventsEnabled`/`adminEventsEnabled`.
- **Problema:**
  - Se comprobó que `client_id=admin-cli` emite tokens por password grant en el realm `biohub`. Esos tokens **no** sirven contra la API (sin `aud=biohub-api`: el gateway y el servicio responden 401, lo que confirma que la validación de audiencia funciona). Aun así, es un segundo canal de prueba de contraseñas sin navegador que seguirá existiendo cuando se elimine `biohub-pruebas`.
  - No se registran eventos de login ni de administración, así que no hay trazabilidad de accesos de `cientifico_alto_rango` ni de `administrador`.
  - `length(12) and notUsername`: falta `notEmail`, `maxLength(64)` y `passwordHistory`.
- **Corrección** (añadir al JSON):
  ```json
  "passwordPolicy": "length(12) and maxLength(64) and notUsername and notEmail and passwordHistory(3)",
  "eventsEnabled": true,
  "eventsExpiration": 2592000,
  "enabledEventTypes": ["LOGIN","LOGIN_ERROR","LOGOUT","CODE_TO_TOKEN_ERROR","REFRESH_TOKEN_ERROR","UPDATE_PASSWORD","UPDATE_EMAIL"],
  "adminEventsEnabled": true,
  "adminEventsDetailsEnabled": false,
  ```
  y declarar `admin-cli` con `"directAccessGrantsEnabled": false` (o desactivarlo desde la consola o el script de despliegue). Recordar que el realm solo se importa la primera vez (README).
- **Esfuerzo:** S · **Responsable sugerido:** Tech Lead

### SEC-10 · Bajo · A02 / A05 (exposición de secretos)
**Contraseñas en la línea de comandos (health checks e init de MySQL) e interpolación de contraseñas dentro de SQL.**

- **Archivos:** `docker-compose.yml:9` (`mysqladmin ... -p"$$MYSQL_ROOT_PASSWORD"`), `docker-compose.yml:94` (`redis-cli -a "$$REDIS_PASSWORD"`), `infra/mysql/transaccional/01-esquemas-y-usuarios.sh:7` (`-p"${MYSQL_ROOT_PASSWORD}"`) y `:11-12` (`IDENTIFIED BY '${...PASSWORD}'`).
- **Problema:** los health checks corren cada 10 s con `docker exec`. La contraseña de **root** de MySQL y la de Redis aparecen en los argumentos del proceso, visibles con `ps` en el host para cualquier usuario local. En el script de init, una contraseña con `'` o `\` rompe el SQL (o inyecta sentencias con privilegios de root). El origen es el `.env`, así que el riesgo es bajo, pero el error es silencioso.
- **Corrección:**
  ```yaml
  test: ["CMD-SHELL", "MYSQL_PWD=\"$$MYSQL_ROOT_PASSWORD\" mysqladmin ping -h 127.0.0.1 -uroot --silent"]
  ...
  test: ["CMD-SHELL", "REDISCLI_AUTH=\"$$REDIS_PASSWORD\" redis-cli ping | grep -q PONG"]
  ```
  ```bash
  export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"
  for v in MYSQL_CIENTIFICO_PASSWORD MYSQL_EQUIPOS_PASSWORD; do
    [[ "${!v}" =~ ^[A-Za-z0-9_.@%+=-]+$ ]] || { echo "Caracteres no permitidos en $v" >&2; exit 1; }
  done
  mysql --protocol=socket -uroot <<SQL
  ...
  ```
  Mejor aún, un usuario de health check sin privilegios (`GRANT USAGE`) en lugar de root.
- **Esfuerzo:** S · **Responsable sugerido:** devops / Tech Lead

### SEC-11 · Bajo · A01 (mínimo privilegio en BD)
**El usuario de aplicación tiene `ALL PRIVILEGES` sobre su esquema, y existe `root@'%'`.**

- **Archivos:** `infra/mysql/transaccional/01-esquemas-y-usuarios.sh:14-15`. Los demás MySQL usan `MYSQL_USER` de la imagen, que también recibe `ALL` sobre su base. `root@'%'` lo crea la imagen por defecto (confirmado en `mysql.user`).
- **Problema:** el mismo usuario ejecuta Flyway (DDL) y la aplicación (DML). Una inyección SQL futura podría hacer `DROP TABLE`, `ALTER` o `CREATE ROUTINE`. Root acepta conexiones desde cualquier host del contenedor (hoy el puerto solo está publicado en 127.0.0.1).
- **Corrección:** separar el usuario de migración del de ejecución:
  ```sql
  CREATE USER 'cientifico_migr'@'%' IDENTIFIED BY '...';
  GRANT ALL PRIVILEGES ON cientifico.* TO 'cientifico_migr'@'%';
  GRANT SELECT, INSERT, UPDATE, DELETE ON cientifico.* TO 'cientifico_app'@'%';
  ```
  ```yaml
  spring.flyway.user: ${MYSQL_CIENTIFICO_MIGR_USER}
  spring.flyway.password: ${MYSQL_CIENTIFICO_MIGR_PASSWORD}
  ```
  En compose, `MYSQL_ROOT_HOST: localhost` para que root solo funcione dentro del contenedor.
- **Esfuerzo:** M · **Responsable sugerido:** dba / Tech Lead

### SEC-12 · Bajo · A05 / A02
**Cada servicio carga el `.env` completo y recibe las credenciales de los demás servicios.**

- **Archivos:** `*/src/main/resources/application.yml:6` (`spring.config.import: optional:file:../.env[.properties]`).
- **Problema:** el `Environment` de, por ejemplo, `servicio-consulta-publica` contiene las contraseñas de MySQL de cientifico, equipos, analítica y gateway, las de root de cada MySQL y la de administración de Keycloak. Esto contradice el aislamiento "un usuario por esquema" de 03-arquitectura. Si mañana alguien expone `/actuator/env` o `/actuator/configprops`, o un error vuelca propiedades, se filtran **todas**.
- **Corrección:** está bien como comodidad local (D3), pero:
  1. Documentar que en cualquier despliegue cada servicio recibe solo sus variables (secretos de Docker o del orquestador).
  2. Mantener `management.endpoints.web.exposure.include` en `health,info` y añadir una prueba que verifique que `/actuator/env` responde 404.
  3. Mover los `*_ROOT_PASSWORD` y `KEYCLOAK_ADMIN_PASSWORD` a un `.env.infra` que solo lea Compose (`env_file` / `--env-file`).
- **Esfuerzo:** S · **Responsable sugerido:** devops

### SEC-13 · Bajo · A07
**web-interna: cuando falla el refresco del token, no se vuelve a iniciar sesión.**

- **Archivos:** `web-interna/src/api/cliente.js:9` (`await keycloak.updateToken(30)`), `web-interna/src/auth/keycloak.js:21-25` (`checkLoginIframe: false`, sin `onTokenExpired` ni `onAuthRefreshError`).
- **Problema:** cuando la sesión de Keycloak expira (30 min de inactividad o 10 h de máximo) o se cierra en otra pestaña, `updateToken` lanza un error. El usuario ve "No se pudo contactar el servicio" y la app sigue mostrando su nombre y roles del token viejo. No hay riesgo de acceso indebido (la API rechaza el token), pero la sesión "fantasma" en la UI es mala práctica, y en las historias con formularios se perderán datos.
- **Corrección:**
  ```js
  export async function getAutenticado(ruta) {
    try {
      await keycloak.updateToken(30);
    } catch {
      await keycloak.login();          // sesión vencida: volver a autenticar
      return;
    }
    ...
  }
  keycloak.onAuthRefreshError = () => keycloak.login();
  ```
  El almacenamiento está bien: `keycloak-js` mantiene el token y el refresh token solo en memoria, y no hay `localStorage`/`sessionStorage` en el código.
- **Esfuerzo:** S · **Responsable sugerido:** frontend

### SEC-14 · Bajo · A02 / A05 (secretos versionados en el futuro)
**`.gitignore` no cubre archivos de llaves ni certificados.**

- **Archivos:** `.gitignore:13-17`.
- **Problema:** está bien para `.env` (verificado con `git check-ignore`). Pero el módulo `cientifico.ubicacion` va a manejar **cifrado y llaves**, y no se ignoran `*.pem`, `*.key`, `*.p12`, `*.jks`, `*.keystore` ni `secrets/`. El primer commit de una llave de cifrado de ubicaciones sería un incidente difícil de revertir (queda en el historial).
- **Corrección:**
  ```gitignore
  # Llaves y certificados
  *.pem
  *.key
  *.p12
  *.pfx
  *.jks
  *.keystore
  secrets/
  infra/**/llaves/
  ```
  Y un hook o paso de CI con `gitleaks` (`gitleaks protect --staged`) antes del primer push (tarea 9 del plan).
- **Esfuerzo:** S · **Responsable sugerido:** Tech Lead

---

### SEC-15 · Informativo · A06 Vulnerable and Outdated Components
**Dependencias.**
- **npm:** `npm audit` da 0 vulnerabilidades en las dos webs (keycloak-js 26.2.4, react 19.3.1, vite 8.3.5).
- **Maven (versiones empaquetadas):** spring-boot 3.5.16, spring-framework 6.2.19, spring-security 6.5.11, tomcat-embed 10.1.55, jackson-databind 2.21.4, nimbus-jose-jwt 9.37.4, mysql-connector-j 9.7.0, flyway 11.7.2, spring-amqp 3.2.12, lettuce 6.6.0, netty 4.1.135, logback 1.5.34, snakeyaml 2.4. No hay escáner instalado (OWASP dependency-check, trivy, grype u osv-scanner) y no se instaló ninguno, así que **no se pudo confirmar la ausencia de CVE**. Son las versiones gestionadas por el BOM de Boot 3.5.16, sin versiones fijadas a mano.
- **Acción:**
  1. Añadir `org.owasp:dependency-check-maven` (o `osv-scanner`) y Dependabot/Renovate en CI.
  2. Verificar en spring.io la **fecha de fin de soporte OSS de Spring Boot 3.5.x**. Si ya terminó, no habrá parches públicos de CVE para la línea 3.x, y conviene dejarlo registrado como riesgo aceptado por requisito del curso.
  3. Fijar las imágenes de Docker por versión de parche o digest (`mysql:8.4.x`, `redis:7.4.x-alpine`, `rabbitmq:4.1.x-management`).

### SEC-16 · Informativo · A05
**Cabeceras de seguridad.** El gateway responde con `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-store` y `X-XSS-Protection: 0` (correcto). Las cabeceras que vienen de los servicios no se duplican. No hay HSTS porque es HTTP (esperado en local). Las webs de Vite no envían CSP. **Acción para despliegue:** servir las webs con CSP (`default-src 'self'; connect-src 'self' <gateway> <keycloak>; frame-ancestors 'none'; object-src 'none'; base-uri 'self'`), HSTS y `Referrer-Policy: no-referrer`. Para la API pública cacheable habrá que relajar `Cache-Control` de forma explícita, solo en esas rutas.

### SEC-17 · Informativo · A07
**Los usuarios pueden editar su nombre y su email desde la consola de cuenta de Keycloak** (`/realms/biohub/account/` responde 200; es el comportamiento por defecto del perfil de usuario). Los claims `name`, `given_name`, `family_name` y `email` del token los controla el usuario. **Acción:** identificar a los usuarios siempre por `sub` (nunca por `email` ni `preferred_username` para asociar datos o permisos). Tratar esos claims como entrada no confiable (React ya escapa; nunca usar `dangerouslySetInnerHTML` con ellos). Opcional: en el perfil de usuario del realm, dejar `email` editable solo por admin.

### SEC-18 · Informativo · A05
**Panel de RabbitMQ y consola de Keycloak con usuario administrador predecible.** Ambos están en 127.0.0.1 (bien). `.env.example` propone `KEYCLOAK_ADMIN_USER=admin`. En local es aceptable; en cualquier otro entorno, usar un nombre no trivial, eliminar el admin de bootstrap tras crear uno definitivo y no publicar el puerto 15672.

### SEC-19 · Informativo · A08 Software and Data Integrity
**Serialización.** `RabbitConfig` usa `Jackson2JsonMessageConverter` con los paquetes de confianza por defecto (`java.util`, `java.lang`), lo cual es seguro. **No** cambiarlo a `setTrustedPackages("*")`. La caché de Redis (`CacheConfig`) usará por defecto `JdkSerializationRedisSerializer` (deserialización Java nativa). Ver los requisitos.

### SEC-20 · Informativo
**Contexto aceptado, verificado sin riesgo adicional:**
- Keycloak `start-dev` por HTTP: solo en `127.0.0.1:8180`, `KC_HOSTNAME` fijo (el `iss` no depende del `Host`).
- `biohub-pruebas`: solo password grant (`standardFlowEnabled: false`), sujeto a la protección contra fuerza bruta, documentado en el README.
- `/api/publica/**` sin JWT: solo GET. HEAD, POST y los demás métodos exigen token.

---

## Bien hecho

- **Validación de JWT completa en el gateway y en cada servicio** (firma, `iss` vía `issuer-uri`, `exp` y `aud=biohub-api`). Se comprobó con un token real de `admin-cli` (sin `aud`): 401 en el gateway **y** en el servicio directo. Los ID tokens y refresh tokens no sirven como access token (su `aud` es distinta).
- **Realm:** PKCE S256 obligatorio en `web-interna` (sin `code_challenge` Keycloak rechaza la petición), password grant desactivado en `web-interna` (comprobado), `redirect_uri` ajeno rechazado (400), implicit flow desactivado, registro y reset desactivados, `rememberMe: false`, protección contra fuerza bruta (5 intentos, espera creciente hasta 15 min), access token de 5 min, código de autorización de 60 s, **rotación de refresh token sin reutilización** (`revokeRefreshToken: true`, `refreshTokenMaxReuse: 0`), sesiones de 30 min de inactividad y 10 h de máximo, `sslRequired: external`. Los tokens no traen `offline_access`.
- **Contraseñas de prueba fuera del repo:** el placeholder `${KEYCLOAK_TEST_USERS_PASSWORD}` se resuelve en la importación (el login con el valor del `.env` funciona). No hay ningún secreto real en los archivos versionables. `.env` ignorado y con permisos `600`. `.env.example` solo con valores `cambiar_*` y las mismas claves que `.env`.
- **CORS** estricto: lista explícita de orígenes, `allowCredentials(false)`, cabeceras permitidas acotadas. Un origen ajeno recibe 403, también en el preflight. El proxy elimina las `Access-Control-*` de los servicios para que manden las del gateway.
- **Proxy:** sin SSRF (destino solo desde configuración, no sigue redirecciones), timeouts de conexión y lectura, `Cookie` y `Origin` no se reenvían, `X-Forwarded-For` lo **sobrescribe** el gateway con `getRemoteAddr()` (no se confía en el del cliente), y no se filtran detalles internos en el 502 (`ProblemDetail` genérico, el detalle solo en el log).
- **Path traversal bloqueado** (`..`, `%2e`, `%2f`, `;`, `//`), comprobado empíricamente.
- **Configuración de errores y actuator:** `include-stacktrace: never`, `include-message: never`, solo `health,info` expuestos, `show-details: never`, `/actuator/info` y `/actuator/env` exigen token. `open-in-view: false`. `ddl-auto: validate` con Flyway.
- **API stateless** (`STATELESS`, CSRF desactivado con justificación correcta: Bearer, sin cookies).
- **Infraestructura** publicada solo en `127.0.0.1`. Un usuario de BD por esquema (cientifico no puede tocar `equipos`). Redis con contraseña. Variables obligatorias en compose (`:?Falta en .env`).
- **web-interna:** token solo en memoria (sin `localStorage`), Authorization Code + PKCE, logout con `redirectUri` del propio origen, filtrado de roles técnicos. React escapa toda la salida y no se usa `dangerouslySetInnerHTML`.
- **Conversor de roles** duplicado pero **idéntico** en los 5 módulos (comprobado con `diff`). Descarta roles técnicos y usa `preferred_username` con `sub` como respaldo.
- **Pruebas de seguridad** en el gateway (`GatewaySecurityTest`): 401 sin token, sin reenvío cuando no hay autenticación.

---

## Requisitos de seguridad para las próximas historias

### API Keys (`/api/publica/**`)
1. Generar las keys con `SecureRandom` (≥ 32 bytes, Base64URL) y un prefijo identificable (`bh_pub_...`). **Guardar solo el hash** (SHA-256 basta para keys aleatorias de alta entropía; HMAC-SHA256 con un *pepper* del `.env` es mejor). Mostrar la key una sola vez al crearla.
2. Comparar en tiempo constante (`MessageDigest.isEqual`) y buscar por un identificador o prefijo no secreto, no por el hash completo con `LIKE`.
3. Estados: activa, revocada, expirada, con fecha de expiración y `ultimo_uso`. Revocación inmediata (invalidar la caché).
4. Las keys **nunca** van en la URL (query string): solo en la cabecera `X-API-Key`. Nunca se registran en logs (enmascarar: `bh_pub_ab12…`). No se reenvían a los servicios (SEC-07).
5. Implementar como autenticación fail-closed (SEC-05). La gestión de keys (`/api/gateway/keys/**`) debe exigir `hasRole('administrador')`, no solo `authenticated()`.
6. La web pública no puede llevar una key secreta en su bundle de JS (todo lo que se envía al navegador es público). Hay dos opciones: una key "pública" identificada por origen y con límites bajos, o que la web pública quede exenta por `Origin` con límite por IP. Documentar la decisión.

### Rate limiting
1. Contador compartido (Redis, ventana deslizante o token bucket; por ejemplo Bucket4j con `lettuce`) para que funcione con varias instancias del gateway. **Fallar cerrado o degradado** de forma definida si Redis no está disponible.
2. Clave de límite: API Key, y además IP (`request.getRemoteAddr()`; **nunca** `X-Forwarded-For` del cliente, salvo que haya un proxy de confianza configurado con `server.forward-headers-strategy=native` y `RemoteIpValve` con `internalProxies`).
3. Responder `429` con `Retry-After`, y opcionalmente las cabeceras `RateLimit-*`. Ponerlo **antes** de leer el cuerpo (ya lo está). Para rutas con JWT, un segundo limitador por `sub` después de la autenticación.
4. Los servicios no deben ser accesibles sin pasar por el gateway (SEC-01). Si no, el límite no sirve.
5. Límites también en Keycloak: ya existe la protección contra fuerza bruta. No desactivarla.

### Ubicaciones protegidas, cifrado y llaves (`cientifico.ubicacion`)
1. Cifrar las coordenadas exactas **en la aplicación** con AES-256-GCM (IV aleatorio de 12 bytes por registro, guardado junto al texto cifrado, con el `id` del registro como AAD). Nada de ECB/CBC sin MAC, IV fijo o cifrado "casero".
2. Cifrado de sobre (*envelope encryption*): una DEK por registro o lote, envuelta con una KEK que **no** está en la BD ni en el repo (en local, archivo fuera del repo y en `.gitignore`, o variable de entorno; en despliegue, KMS o Vault). Incluir un identificador de versión de llave para poder rotarla.
3. Solo `cientifico_alto_rango` puede descifrar, comprobado en el servicio (SEC-02) y **auditando cada descifrado** (quién, cuándo, qué registro, motivo), en una tabla de auditoría de solo inserción.
4. Los eventos hacia `consulta-publica` y `reportes` **nunca** llevan coordenadas exactas, solo generalizadas (por ejemplo, cuadrícula de 10 km o municipio). La generalización ocurre en `cientifico` antes de publicar, no en el consumidor.
5. Búsqueda y mapa: no permitir consultas que permitan triangular (por ejemplo, búsquedas por radio muy pequeño sobre datos protegidos); aplicar una granularidad mínima.
6. Las coordenadas exactas no aparecen en logs, mensajes de error, trazas ni en la caché de Redis.

### Autorización y datos
1. `@EnableMethodSecurity`, denegación por defecto y una prueba de 403 por rol en cada endpoint nuevo (SEC-02).
2. Sin IDOR: cada consulta por `id` comprueba la pertenencia (equipo o autor) con el `sub` del token, no con un parámetro del cliente.
3. Identificar a los usuarios por `sub` (SEC-17).
4. Validar la entrada con Bean Validation (`@Valid`, tamaños máximos) y usar solo consultas parametrizadas (JPA/`@Query` con parámetros, nunca concatenar en JPQL o SQL nativo).
5. MFA (OTP) obligatorio para `cientifico_alto_rango` y `administrador` (flujo condicional por rol en Keycloak).

### Eventos (RabbitMQ)
1. Usuarios por servicio con permisos mínimos (SEC-04).
2. Esquema de evento versionado (`tipo`, `version`, `id`, `ocurridoEn`), validado en el consumidor. Consumidores idempotentes (por `id` de evento) y una DLQ para los mensajes inválidos.
3. No usar `setTrustedPackages("*")` ni el `__TypeId__` del productor para instanciar clases arbitrarias: mapear a DTO fijos.

### Caché (Redis)
1. Configurar `RedisCacheConfiguration` con `GenericJackson2JsonRedisSerializer` (o un serializador por tipo) en lugar de la serialización Java por defecto, y **sin** `activateDefaultTyping` amplio.
2. Cachear solo datos públicos ya generalizados, y nunca respuestas que dependan del usuario.

### Archivos subidos (fichas, fotos)
1. Límite de tamaño en el gateway y en el servicio (SEC-03). Validar el tipo por *magic bytes*, no por extensión ni `Content-Type`.
2. Guardar fuera del *webroot*, con un nombre generado (UUID) y nunca la ruta que manda el cliente. Quitar los metadatos EXIF de las fotos, **en especial el GPS**: si no, se filtran las ubicaciones protegidas por la foto pública.
3. Servir con `Content-Disposition: attachment` o con el `Content-Type` correcto y `nosniff`.

### Logs y despliegue
1. Nunca registrar `Authorization`, `X-API-Key`, tokens, contraseñas, coordenadas exactas ni PII innecesaria. Usar un `MDC` con `sub` y un id de petición para la trazabilidad.
2. Fuera de local: Keycloak en modo `start` con HTTPS y BD externa, sin `biohub-pruebas`, sin `admin-cli` con password grant, `sslRequired: all`, `redirectUris` sin comodín, HSTS y CSP en las webs (SEC-16).
3. `gitleaks` y escaneo de dependencias en CI (SEC-14, SEC-15).

---

## Veredicto

**APROBADO CON OBSERVACIONES.** No hay hallazgos Críticos ni Altos. Antes de empezar las historias que expongan datos se deben resolver **SEC-01** (servicios en 0.0.0.0), **SEC-02** (`@EnableMethodSecurity` y denegación por defecto) y **SEC-03** (límite de cuerpo en el gateway), y **SEC-04** (usuarios de RabbitMQ) antes de la primera historia con eventos. Los hallazgos Bajos se pueden resolver junto con la historia que toque cada componente.

## Seguimiento del Tech Lead (corrección y re-verificación)
Verificado tras recrear la infraestructura desde cero (`docker compose down -v` + `up -d`) y recompilar.

| ID | Estado | Verificación |
|---|---|---|
| SEC-01 | **Corregido**: `server.address: ${SERVER_ADDRESS:127.0.0.1}` en los 5 servicios | `ss -ltn`: 8080-8084 solo en 127.0.0.1 |
| SEC-02 | **Corregido parcialmente**: `@EnableMethodSecurity` en los 5 servicios. Las reglas por rol y el cambio a `denyAll` por defecto quedan para cada historia (aún no hay endpoints de negocio) | compila; 23+8 tests |
| SEC-03 | **Corregido**: el gateway ya no usa `@RequestBody byte[]`; lee como máximo `tamano-maximo-cuerpo` (10MB) → 413; en GET/HEAD/OPTIONS/DELETE/TRACE el cuerpo no se lee | POST 11 MB → 413; tests `rechazaCuerpoMayorAlLimiteSinReenviar`, `ignoraElCuerpoEnGet` |
| SEC-04 | **Pendiente** → primera historia con eventos (usuarios RabbitMQ por servicio vía definitions.json) | – |
| SEC-05 | **Pendiente** → historia de API Keys (feature/seguridad-acceso) | – |
| SEC-06 | **Corregido**: el proxy rechaza con 400 rutas no normalizadas (`..`, `%2e`, `%2f`, `%5c`, `;`, `//`, `%25`, `%00`) y el firewall responde 400 (`HttpStatusRequestRejectedHandler`) | 9 casos parametrizados; `/api/publica/%2e%2e/...` → 400 |
| SEC-07 | **Corregido**: se excluyen `x-forwarded-*`, `x-original-*`, `x-real-ip`, `x-api-key`, `forwarded`; el gateway fija `X-Forwarded-For/Proto`; ya no envía `X-Forwarded-Host` | test `noReenviaCabecerasFalsificablesNiLaApiKey` |
| SEC-08 | **Corregido**: `IllegalArgumentException` → 400 ProblemDetail; `dispatcherTypeMatchers(ERROR).permitAll()` en los 5 servicios | `?a=%zz` → 400 |
| SEC-09 | **Corregido**: `admin-cli` sin password grant, eventos de login y admin activos, política `length(12) and maxLength(64) and notUsername and notEmail and passwordHistory(3)` | password grant con admin-cli → `unauthorized_client` |
| SEC-10 | **Corregido**: healthchecks con `MYSQL_PWD`/`REDISCLI_AUTH`; Redis lee la contraseña de un .conf interno; el script de init valida caracteres y usa `MYSQL_PWD` | `ps` del host: 0 procesos con contraseñas |
| SEC-11 | **Corregido parcialmente**: `MYSQL_ROOT_HOST=127.0.0.1` (ya no existe `root@'%'`). Usuario de migración separado del de aplicación → pendiente (cuando haya tablas) | `mysql.user`: root solo en localhost/127.0.0.1 |
| SEC-12 | **Aceptado en local / pendiente para despliegue** (devops: cada servicio recibe solo sus variables) | – |
| SEC-13 | **Corregido**: `onAuthRefreshError` y `catch` de `updateToken` → `keycloak.login()` | tests web-interna 2/2 |
| SEC-14 | **Corregido**: `.gitignore` con llaves/certificados/`secrets/` y `.claude/` | – |
| SEC-15..18 | Informativos; se tendrán en cuenta en CI (devops) y despliegue | – |
| SEC-19 | **Corregido**: caché Redis serializa en JSON con TTL y prefijo `biohub:publica:` | compila |

**Estado final: APROBADO CON OBSERVACIONES** (sin Críticos/Altos; pendientes asignados a historias).

---
# Iteración despliegue (D11-D13)

**Revisor:** ciberseguridad · **Fecha:** 2026-10-22 · **Rama:** `feature/despliegue` (sin commit; revisado el árbol de trabajo) · **Alcance:** despliegue en 2 VMs KVM + Tailscale (tarea 13f), código nuevo de RabbitMQ (13b) y decisión D13. Alcance acordado 4a (sin Funnel, sin Keycloak de producción, datos de prueba).

## Resumen

| Severidad | Cantidad |
|---|---|
| Crítico | 0 |
| Alto | 0 |
| Medio | 4 |
| Bajo | 7 |
| Informativo | 4 |

**Veredicto: APROBADO CON OBSERVACIONES.** No hay hallazgos Críticos ni Altos dentro del alcance 4a. Los 4 Medios (SEC-D-01 a SEC-D-04) son aceptables solo mientras: (a) la tailnet tenga únicamente a los 4 integrantes, (b) no haya datos reales y (c) no se active Funnel/serve. Si cambia cualquiera de esas tres cosas, **SEC-D-01, SEC-D-02 y SEC-D-04 pasan a Alto** y deben corregirse antes. SEC-D-03 se debe corregir antes de implementar el evento real `hallazgo.registrado`.

### Cómo se revisó
- **Documentación:** `00-plan.md` (iteración tarea 13), `03-arquitectura.md` (D11), `04-contrato-api.md` (Eventos), `09-despliegue.md`, `docs/guia-tailscale.md` y los pendientes SEC-* de este reporte.
- **Infraestructura (estático):** todo `deploy/**` (`crear-vms.sh`, `user-data.tpl`, `generar-env.sh`, `desplegar.sh`, ambos `docker-compose.yml`, `.env.example`, scripts de init MySQL, `generar-definitions.sh`, `20-definitions.conf`, backups y unidades systemd), los 5 `Dockerfile`, `.dockerignore`, `.gitignore`.
- **Compose resuelto:** `docker compose config` de ambos stacks con las IP reales inyectadas por variable (sin imprimir secretos): puertos publicados, variables por servicio, usuario, `cap_drop`, `security_opt`.
- **Código:** `eventos/**` de científico, consulta-pública y reportes; `RabbitConfig` y `SecurityConfig` de los servicios; `application.yml` (diff); tests de autorización (`isForbidden` presente en los 3 controladores de eventos).
- **Secretos:** `git status --porcelain --ignored`, `git check-ignore -v` y búsqueda de los **valores** de todas las claves `*PASSWORD*/*SECRET*/*KEY*` de `.env`, `deploy/backend/.env` y `deploy/datos/.env` en los 199 archivos versionables (sin imprimirlos): **0 coincidencias de secretos** (solo `KEYCLOAK_ISSUER_URI` y `KEYCLOAK_AUDIENCE`, que no son secretos). Búsqueda de `tskey-` (auth keys de Tailscale): 0.
- **Anfitrión (solo lectura):** `ss -ltn` (solo loopback, dnsmasq de libvirt en 192.168.122.1:53 y puertos efímeros de tailscaled), `tailscale status`, permisos de `.env` y de la llave SSH.
- **ACL y estado de las VMs:** según lo verificado y reportado por el Tech Lead (no se accedió a las VMs).
- **Dependencias:** no hay escáner de CVE instalado (`dependency-check`, `trivy`, `grype`: no disponibles) y no se instaló ninguno. Spring Boot 3.5.16. Sigue abierto SEC-15.

---

## Hallazgos

### SEC-D-01 · Medio · A01 / A05 (mínimo privilegio en la operación)
**Cualquier miembro de la tailnet es root en las dos VMs y puede leer todos los secretos.**

- **Archivos:** `deploy/vms/user-data.tpl:13-15` (`sudo: ALL=(ALL) NOPASSWD:ALL`), `deploy/vms/user-data.tpl:53` (`usermod -aG docker biohub`), regla `ssh` de la ACL (`autogroup:member -> tag:backend, tag:datos, users [biohub]`), `docs/guia-tailscale.md:61` y `:166-168`.
- **Problema:** Tailscale SSH deja entrar a todos los miembros como `biohub`. Ese usuario tiene sudo sin contraseña **y** pertenece al grupo `docker` (equivalente a root por sí solo). Además, los `.env` (600) son de `biohub`, así que cualquiera que entre los lee directamente (`cat ~/biohub-backend/.env`), igual que con `docker inspect`/`docker exec ... env`. La guía dice "pide las contraseñas por un canal privado", pero en la práctica todos los miembros ya tienen acceso a todas: contraseñas root de MySQL, admin de Keycloak y RabbitMQ, contraseñas de los usuarios de prueba y volúmenes de datos.
- **Por qué importa:** si se compromete el portátil de **un** integrante (o se invita a alguien más a la tailnet), el atacante obtiene root y todos los datos de ambas VMs. Así se pierde la separación backend/datos que justifica D11. Hoy los datos son de prueba, por eso queda en Medio.
- **Cómo corregirlo:**
  1. Separar los roles: `biohub` (administración, solo la dueña, con la llave por la red NAT o Tailscale SSH en modo `check`) y un usuario `equipo` para los compañeros, **sin** sudo y **sin** grupo `docker`, con permisos sudo solo para comandos de lectura:
     ```
     # /etc/sudoers.d/biohub-equipo  (visudo -f)
     equipo ALL=(root) NOPASSWD: /usr/bin/docker compose --project-directory /home/biohub/biohub-backend ps, \
                                 /usr/bin/docker compose --project-directory /home/biohub/biohub-backend logs *
     ```
     (Ojo: no permitir `docker exec`, `run` ni `compose up`: todos dan root.)
  2. ACL de SSH con grupos explícitos y reautenticación:
     ```json
     "groups": { "group:admins": ["juanita@..."], "group:equipo": ["a@...", "b@...", "c@..."] },
     "ssh": [
       { "action": "check", "checkPeriod": "12h", "src": ["group:admins"], "dst": ["tag:backend","tag:datos"], "users": ["biohub"] },
       { "action": "check", "checkPeriod": "12h", "src": ["group:equipo"], "dst": ["tag:backend","tag:datos"], "users": ["equipo"] }
     ]
     ```
  3. Para consultas de BD, en vez de compartir la contraseña root, crear un usuario MySQL de **solo lectura** (`SELECT` sobre los esquemas, `@'localhost'`) y documentar ese.
  4. Quitar el NOPASSWD de `biohub` cuando ya no haga falta cloud-init (o dejarlo solo para la llave local), sabiendo que mientras `biohub` esté en el grupo `docker` quitar el NOPASSWD no reduce el riesgo.
- **Esfuerzo:** M · **Responsable:** devops + dueña (ACL).

### SEC-D-02 · Medio · A02 / A05 / A07 (D13: Keycloak sin exigir TLS)
**`sslRequired=NONE` en los realms `master` y `biohub`, con la consola de administración accesible desde toda la tailnet.**

- **Archivos:** `deploy/desplegar.sh:96-100`, `deploy/backend/docker-compose.yml:192-202` (`start-dev`, `KC_HOSTNAME=http://...`, puerto 8180 en la IP de Tailscale), ACL `autogroup:member -> tag:backend:8180`.
- **Riesgo residual evaluado (D13 aceptada por la dueña):**
  - **Confidencialidad en tránsito: aceptable.** Todo el tráfico cliente→Keycloak viaja por WireGuard, y el puerto está ligado solo a `100.76.214.94` (verificado en el compose resuelto; Docker no lo publica en otra interfaz). Nadie fuera de la tailnet lo ve.
  - **Lo que D13 no cubre:** (1) en `master` quedan la consola admin y el password grant de `admin-cli`, ambos alcanzables por HTTP desde cualquier dispositivo de la tailnet. El realm `master` **no** tiene `bruteForceProtected` (solo `biohub` lo tiene; el import no toca `master`), así que se puede intentar adivinar la contraseña del admin sin límite. (2) El cifrado depende de que **nadie** publique 8180 por otro medio (Funnel, `tailscale serve` HTTP, reenvío de puertos, `0.0.0.0`). Con `NONE`, Keycloak entregaría tokens y cookies por HTTP en claro sin quejarse. (3) Las cookies de sesión de la consola no llevan `Secure`.
  - **Condiciones para mantener D13** (todas a la vez):
    1. 8180 publicado solo en la IP de Tailscale. No usar Funnel ni `tailscale serve` sobre 8180 mientras siga en `NONE`.
    2. ACL: 8180 solo para los miembros que lo necesitan (ver SEC-D-04). Ningún dispositivo compartido ni nodo externo.
    3. Solo usuarios de prueba y datos de prueba (`start-dev`, H2).
    4. **Volver a `external` en `biohub` y en `master` (o mejor `all`) en cuanto haya HTTPS** (`tailscale serve --https=443 http://127.0.0.1:8180` o Funnel solo para `/realms/biohub` y `/resources`). Cambiar entonces `KC_HOSTNAME` a `https://biohub-backend.<tailnet>.ts.net` y quitar el paso D13 de `desplegar.sh`. Si se reimporta el realm, revisar que no se haya aplicado `NONE`.
- **Cómo reducir el riesgo ya (S):**
  ```bash
  # en desplegar.sh, junto al paso D13: protección contra fuerza bruta en master
  $K update realms/master --config $C -s bruteForceProtected=true -s failureFactor=5 \
     -s permanentLockout=false -s maxFailureWaitSeconds=900
  ```
  Opcional: dejar `master` en `external` y administrar con `kcadm` vía `docker exec` (como ya hace `desplegar.sh`). Así solo `biohub` queda en `NONE` y la consola admin no se usa por la tailnet.
- **Esfuerzo:** S (fuerza bruta en master) / M (HTTPS con `tailscale serve`) · **Responsable:** devops; la reversión a `external` queda como requisito de la tarea de Funnel/Vercel.

### SEC-D-03 · Medio · A01 / A08 (integridad de los eventos)
**Los usuarios consumidores pueden publicar en `biohub.eventos` y los consumidores creen el campo `origen` del cuerpo: se pueden falsificar eventos.**

- **Archivos:** `deploy/backend/rabbitmq/generar-definitions.sh:38-41` (`write` incluye `biohub.eventos` y `biohub.eventos.dlx` para `consulta_mq` y `reportes_mq`), `servicio-*/.../eventos/service/ConsumidorEventos.java:57-67` (consulta-pública y reportes; valida `origen` no vacío pero no quién publicó), `servicio-cientifico/.../eventos/service/PublicadorEventos.java:50-55`.
- **Problema:** para declarar bindings, un consumidor necesita `write` sobre **su cola** y `read` sobre el exchange; no necesita `write` sobre los exchanges. Con el permiso actual, `reportes_mq` (o cualquiera que robe su contraseña desde el contenedor de reportes) puede publicar `hallazgo.registrado` con `"origen":"servicio-cientifico"`. Consulta-pública lo aceptaría y lo mostraría como dato público. El sobre no tiene nada que identifique al publicador de forma verificable.
- **Por qué importa:** hoy solo existe `prueba.ping`, por eso queda en Medio. Con el evento real, comprometer el servicio de reportes (el menos crítico) permitiría inyectar hallazgos en la consulta pública. Así se rompe el aislamiento por servicio que buscaba SEC-04.
- **Cómo corregirlo:**
  1. Mínimo privilegio en `generar-definitions.sh`:
     ```python
     ("consulta_mq", "RABBITMQ_CONSULTA_PASSWORD", "",
      (r"^(biohub\.eventos|biohub\.eventos\.dlx|consulta-publica\.eventos(\.dlq)?)$",   # configure
       r"^consulta-publica\.eventos(\.dlq)?$",                                         # write: solo sus colas (bindings)
       r"^(biohub\.eventos|biohub\.eventos\.dlx|consulta-publica\.eventos(\.dlq)?)$")), # read
     # igual para reportes_mq con reportes\.eventos
     ```
     (El dead-lettering lo hace el broker y no necesita `write` del usuario sobre el DLX.) Volver a correr el E2E de 13b para confirmar.
  2. Autenticar al publicador con `user_id`, que RabbitMQ **valida** contra el usuario de la conexión:
     ```java
     // PublicadorEventos, dentro del MessagePostProcessor
     mensaje.getMessageProperties().setUserId(usuarioRabbit); // ${spring.rabbitmq.username}

     // ConsumidorEventos.interpretar(), después de validar el sobre
     static final Map<String, String> PUBLICADORES = Map.of("servicio-cientifico", "cientifico_mq");
     String userId = mensaje.getMessageProperties().getReceivedUserId();
     if (userId == null || !userId.equals(PUBLICADORES.get(evento.origen()))) {
         throw new EventoInvalidoException("Publicador no autorizado para el origen declarado");
     }
     ```
  3. Al implementar `hallazgo.registrado`: tipar `datos` con un DTO validado (no `Map<String,Object>` libre) y lista blanca de tipos **por publicador**.
- **Esfuerzo:** S · **Responsable:** devops (permisos) + backend-senior (user_id).

### SEC-D-04 · Medio · A01 / A05 (ACL de Tailscale más amplia de lo necesario)
**La ACL da a todos los miembros más acceso del que usan, y mantiene la regla "todos con todos".**

- **Archivo:** política de la tailnet (consola de Tailscale; no está versionada en el repositorio).
- **Problema:**
  1. `autogroup:member -> autogroup:member:*`: cada integrante llega a **todos los puertos** de los dispositivos de los demás, incluido el portátil de la dueña, que es el anfitrión KVM de las VMs. Hoy el anfitrión solo escucha en loopback (comprobado con `ss -ltn`), pero cualquier servicio que se levante en `0.0.0.0` (por ejemplo, un `docker run -p 8080:8080` de desarrollo) queda expuesto a todo el equipo.
  2. `autogroup:member -> tag:datos:3307-3310`: según la guía (sección 6), los miembros **no** pueden ni deben usar MySQL directo (los usuarios de aplicación solo aceptan `@100.76.214.94` y root es local). El acceso solo agrega superficie previa a la autenticación de MySQL.
  3. `autogroup:member` en lugar de grupos con nombre: cualquier invitado futuro hereda todo el acceso (8180 consola admin, 15672 panel admin, SSH como root, ver SEC-D-01).
  4. Lo que está bien: `tagOwners` solo `autogroup:admin`; `tag:backend -> tag:datos` limitado a 3307-3310; `tag:datos` no tiene salida hacia nadie; los nodos etiquetados no entran en `autogroup:member`.
- **Cómo corregirlo:**
  ```json
  "groups": { "group:admins": ["juanita@..."], "group:equipo": ["juanita@...", "a@...", "b@...", "c@..."] },
  "acls": [
    { "action": "accept", "src": ["tag:backend"],  "dst": ["tag:datos:3307-3310"] },
    { "action": "accept", "src": ["group:equipo"], "dst": ["tag:backend:8080"] },
    { "action": "accept", "src": ["group:admins"], "dst": ["tag:backend:22,8180,15672", "tag:datos:22"] },
    { "action": "accept", "src": ["group:equipo"], "dst": ["tag:backend:22,8180", "tag:datos:22"] }
    // sin autogroup:member -> autogroup:member:*  (o limitarlo a puertos concretos si se necesita)
  ],
  "tests": [
    { "src": "a@...", "accept": ["tag:backend:8080"], "deny": ["tag:datos:3307", "tag:backend:15672"] }
  ]
  ```
  Ajustar 8180 (necesario para pedir tokens) y 15672 según quién lo use de verdad. Versionar la política, sin secretos, en `deploy/tailscale/policy.hujson` como referencia, con la sección `tests` para que la consola rechace cambios que la rompan.
- **Esfuerzo:** S · **Responsable:** dueña de la tailnet + Tech Lead.

### SEC-D-05 · Bajo · A02 (secretos en argumentos de proceso; regresión parcial de SEC-10)
- **Archivo:** `deploy/desplegar.sh:100`.
- **Problema:** `kcadm.sh config credentials ... --password "$KC_BOOTSTRAP_ADMIN_PASSWORD"` pone la contraseña del admin de Keycloak en la línea de comandos de un proceso. Mientras se ejecuta, cualquier usuario de la VM la ve con `ps` (los procesos del contenedor se ven desde el host). SEC-10 eliminó justo este patrón en los healthchecks.
- **Cómo corregirlo:** que `kcadm` la lea de la variable de entorno en vez del argumento (Keycloak 26 lee `KC_CLI_PASSWORD` si no se pasa `--password`; verificarlo en 26.8.1). Si no, pasarla por stdin:
  ```bash
  ... sh -c 'export KC_CLI_PASSWORD="$KC_BOOTSTRAP_ADMIN_PASSWORD"; $K config credentials --config $C --server http://localhost:8180 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" >/dev/null && ...'
  ```
- **Esfuerzo:** S · **Responsable:** devops.

### SEC-D-06 · Bajo · A02 / A09 (backups)
- **Archivos:** `deploy/datos/backup/backup.sh:16-27`, `deploy/datos/backup/README.txt:3,9`.
- **Bien:** `umask 077` (archivos 600, carpetas por instancia 700), contraseña root leída dentro del contenedor, archivo `.tmp` + `mv` atómico, rotación de 14 días, un fallo no aborta las demás instancias.
- **Problema:** (1) Los dumps se guardan **sin cifrar** y en el mismo disco de la misma VM, que a su vez está en el portátil de la dueña: no protegen contra pérdida o robo del equipo, y quien obtenga root (SEC-D-01) se lleva todo el histórico. (2) `--all-databases` incluye `mysql.user` (hashes de las contraseñas de aplicación y root). (3) No se verificó que el timer esté instalado (`systemctl list-timers biohub-backup.timer`). (4) El volumen `keycloak-data` (H2: usuarios y configuración del realm) no tiene backup (aceptable en dev).
- **Cómo corregirlo:** cifrar antes de escribir y copiar fuera:
  ```bash
  ... | gzip -9 | age -r "$BACKUP_AGE_PUBKEY" > "$destino.tmp"     # clave privada fuera de la VM
  ```
  Volcar solo los esquemas de aplicación (`--databases cientifico equipos`) en lugar de `--all-databases`, o separarlos. Copia periódica a otro equipo. Verificar el timer y hacer una prueba de restauración documentada.
- **Esfuerzo:** M · **Responsable:** devops. Pasa a **Medio** cuando haya datos reales (ubicaciones protegidas).

### SEC-D-07 · Bajo · A04 / A09 (DLQ sin límites; retención de datos)
- **Archivos:** `servicio-consulta-publica/.../config/RabbitConfig.java:56-59` y `servicio-reportes/.../config/RabbitConfig.java:56-59`.
- **Problema:** las colas `.dlq` no tienen `x-max-length` ni `x-message-ttl`. Un publicador con errores (o malicioso, ver SEC-D-03) puede llenar el disco de RabbitMQ con mensajes de hasta 64 KB. Además, los cuerpos rechazados (que con `hallazgo.registrado` pueden llevar coordenadas) se guardan indefinidamente y se ven en el panel.
- **Cómo corregirlo:**
  ```java
  return QueueBuilder.durable(cola + ".dlq").maxLength(10_000).overflow(QueueBuilder.Overflow.dropHead)
          .ttl((int) Duration.ofDays(7).toMillis()).build();
  ```
  (Cambiar argumentos de una cola existente exige borrarla y declararla de nuevo. Como alternativa, usar una *policy* en `definitions.json`, que no exige recrear la cola.) Además, alarma de disco o de longitud de la DLQ.
- **Esfuerzo:** S · **Responsable:** backend-senior.

### SEC-D-08 · Bajo · A05 (endurecimiento de contenedores e imágenes)
- **Archivos:** `deploy/backend/docker-compose.yml` (todos los servicios), `deploy/datos/docker-compose.yml:5`, `*/Dockerfile:6`.
- **Problema:** ningún servicio tiene `security_opt: [no-new-privileges:true]`, `cap_drop: [ALL]` ni `read_only` (verificado en el compose resuelto). Las imágenes base se referencian por etiqueta mutable (`eclipse-temurin:21-jre`, `mysql:8.4`, `rabbitmq:4.1-management`, `redis:7.4-alpine`) y no por digest. Keycloak sí fija `26.8.1`.
- **Cómo corregirlo:**
  ```yaml
  x-servicio: &servicio
    security_opt: ["no-new-privileges:true"]
    cap_drop: ["ALL"]
    read_only: true
    tmpfs: ["/tmp"]
  ```
  (En los servicios Java. En MySQL/RabbitMQ/Redis, solo `no-new-privileges`.) Fijar `FROM eclipse-temurin:21-jre@sha256:...` y renovarlo con Dependabot/Renovate. Escanear las imágenes con `trivy image` en CI (SEC-15).
- **Esfuerzo:** S · **Responsable:** devops.

### SEC-D-09 · Bajo · A07 (llave de administración sin passphrase)
- **Archivo:** `deploy/vms/crear-vms.sh:20-21` (`ssh-keygen ... -N ""`).
- **Problema:** `~/.ssh/biohub_vms` (600, correcto) da root en las dos VMs y no tiene passphrase: cualquier proceso del usuario del anfitrión que la lea obtiene ese acceso.
- **Cómo corregirlo:** `ssh-keygen -p -f ~/.ssh/biohub_vms` y usar `ssh-agent` (`desplegar.sh` funciona igual con el agente: `BatchMode=yes` acepta llaves del agente).
- **Esfuerzo:** S · **Responsable:** dueña.

### SEC-D-10 · Bajo · A09 / A04 (IP de origen perdida en el backend)
- **Archivo:** `deploy/vms/crear-vms.sh:33` y `deploy/vms/user-data.tpl:37-42` (`userland-proxy: true` en backend).
- **Problema:** con `docker-proxy`, el gateway ve todas las conexiones como si vinieran de la IP del bridge (172.x). El registro de auditoría por IP y cualquier rate limiting por IP (SEC-05) no sirven: todos los clientes comparten IP. El comentario de `user-data.tpl:38-39` ("Keycloak (sslRequired=external)...") quedó obsoleto con D13.
- **Cómo corregirlo:** con D13 aplicada ya no hace falta `docker-proxy` en backend. Poner `"userland-proxy": false` también ahí (como en datos) y actualizar el comentario. Si se mantiene, limitar por API key o `sub`, nunca por IP.
- **Esfuerzo:** S · **Responsable:** devops.

### SEC-D-11 · Bajo · A02 (definitions.json legible por todos)
- **Archivo:** `deploy/backend/rabbitmq/generar-definitions.sh:54` (modo 644). Se copia a la VM con `rsync -a`.
- **Problema:** contiene los hashes de los 4 usuarios RabbitMQ en `rabbit_password_hashing_sha256`: una sola iteración, sal de 4 bytes. Con contraseñas aleatorias de 24 caracteres (~143 bits) el riesgo real es despreciable, pero el archivo no debería ser legible por todos.
- **Cómo corregirlo:** `0o640` y, en la VM, `chgrp 999` (gid de `rabbitmq` en la imagen oficial) o montarlo como `configs:`/`secrets:` de compose con `uid: "999"`, `mode: 0400`.
- **Esfuerzo:** S · **Responsable:** devops.

### Informativos
- **SEC-D-I1 · JDBC sin verificación de TLS.** Las URL `jdbc:mysql://...` no fijan `sslMode`, así que Connector/J usa `PREFERRED`: cifra, pero no verifica el certificado. Es aceptable porque el tráfico backend→datos va por WireGuard y la ACL solo permite `tag:backend -> tag:datos:3307-3310`. Si algún día MySQL sale de la tailnet, usar `sslMode=VERIFY_IDENTITY`.
- **SEC-D-I2 · Contraseñas de aplicación en el entorno de los contenedores MySQL.** `MYSQL_*_PASSWORD`/`APP_PASSWORD` solo se usan en el primer arranque (init), pero quedan para siempre en `docker inspect`. Mejora futura: archivos `secrets:` montados y que el init lea `*_FILE`.
- **SEC-D-I3 · Usuarios MySQL con `GRANT ALL` sobre su esquema** (`deploy/datos/init/01-transaccional.sh:19-20`, `01-usuario-unico.sh:16`): sigue pendiente SEC-11 (usuario Flyway separado y usuario de ejecución con DML solamente). En especial, `consulta_app` (lectura) no debería tener DDL una vez creadas las tablas.
- **SEC-D-I4 · Cliente `biohub-pruebas` (password grant) y contraseña única para los 5 usuarios de prueba, incluido `admin1` (`administrador`)**, compartida con todo el equipo: aceptado en 4a. Se debe eliminar antes de cualquier exposición fuera de la tailnet (ya previsto en "Logs y despliegue", punto 2).

---

## Bien hecho
- **Secretos fuera de git:** `deploy/**/.env`, `definitions.json` y la copia del realm están ignorados (`.gitignore:44-46`). Los `.env` son 600 y se generan con 24 caracteres de `/dev/urandom` (`generar-env.sh`). Se suben a la VM con `umask 077` y por stdin (no por argumentos). `rsync` excluye `.env`. El realm versionado (`infra/keycloak/realm-biohub.json`) usa el placeholder `${KEYCLOAK_TEST_USERS_PASSWORD}`, sin secretos. 0 coincidencias de valores secretos en los archivos versionables.
- **`.dockerignore` mínimo:** excluye `.env*`, `.git`, `deploy/`, `docs/`, `src/` y las webs; las imágenes solo copian el jar.
- **Dockerfiles:** usuario no root (`uid 10001`, shell `nologin`), JRE en lugar de JDK, `HEALTHCHECK`, `ExitOnOutOfMemoryError`.
- **Exposición de red:** todos los `ports:` ligados a la IP de Tailscale (y el compose falla con `:?` si falta, así nunca cae en `0.0.0.0`). AMQP 5672, Redis, puertos de clúster de RabbitMQ (4369/25672), la gestión de Keycloak (9000) y los servicios 8081-8084 no se publican. El riesgo de que Docker se salte ufw está documentado y mitigado.
- **SEC-12 corregido:** cada servicio recibe solo sus variables (sin `env_file` compartido), comprobado en el compose resuelto.
- **SEC-04 corregido (con el ajuste de SEC-D-03):** usuarios RabbitMQ por servicio, sin `guest`, hashes en vez de contraseñas en claro, `cientifico_mq` sin lectura, definiciones reproducibles con `load_definitions`.
- **MySQL:** usuarios de aplicación limitados a `@100.76.214.94` (nunca `%`), root solo local, `skip-name-resolve`, `userland-proxy=false` en datos para conservar la IP de origen, validación de caracteres en los scripts de init (evita inyección SQL en el heredoc), `MYSQL_PWD` en lugar de `-p`.
- **SEC-10 mantenido en el compose:** healthchecks con `MYSQL_PWD`/`REDISCLI_AUTH`; la contraseña de Redis va a un `.conf` interno.
- **VMs:** `ssh_pwauth: false`, `disable_root`, `lock_passwd`, ufw que deniega todo lo entrante (SSH solo desde la red NAT), actualizaciones automáticas, repos oficiales con keyring firmado, `StrictHostKeyChecking=accept-new`.
- **Código de eventos:** endpoints con `@PreAuthorize("hasRole('administrador')")` más una regla explícita en `SecurityConfig` de consulta-pública, antes del `permitAll` de `GET /api/publica/**` (el orden es correcto), con tests de 403. Validación en el publicador (`@Size(max=200)`) y en el consumidor (tamaño ≤ 64 KB, sobre completo, lista blanca de tipos, routing key = tipo). Deserialización a un `record` fijo sin `__TypeId__` ni tipado polimórfico (cumple el requisito 3 de "Eventos"). Errores `problem+json` sin detalles internos (503 genérico). Los logs y el `MessageRecoverer` registran solo id/tipo/origen, nunca el cuerpo. Publisher confirms y mensajes persistentes. `default-requeue-rejected: false` y reintentos acotados (sin bucle de reencolado). Almacén en memoria acotado a 20.
- **D13 documentada** con su justificación y la condición para revertirla, en el script y en la guía, junto con la advertencia "no publicar fuera de Tailscale".

## Estado de los pendientes anteriores
| ID | Estado tras esta iteración |
|---|---|
| SEC-04 | **Corregido** en despliegue (ajuste de permisos de escritura en SEC-D-03) |
| SEC-12 | **Corregido** en `deploy/backend/docker-compose.yml` |
| SEC-10 | Mantenido en el compose; regresión puntual en `desplegar.sh:100` (SEC-D-05) |
| SEC-11 | Sigue pendiente (SEC-D-I3) |
| SEC-15 | Sigue pendiente: no hay escaneo de CVE de Maven ni de imágenes |
| SEC-02, SEC-05 | Sin cambios (fuera de esta iteración) |

## Veredicto (iteración despliegue)
**APROBADO CON OBSERVACIONES.** No hay Críticos ni Altos dentro del alcance 4a.

Condiciones:
1. **Antes del evento real `hallazgo.registrado`:** SEC-D-03 (permisos `write` de consumidores y validación de `user_id`) y SEC-D-07 (límites de la DLQ).
2. **Antes de datos reales, de invitar a alguien más a la tailnet o de activar Funnel/serve:** SEC-D-01 (separar el acceso root), SEC-D-04 (ACL con grupos, sin `member:*` ni MySQL para miembros) y SEC-D-02 (volver `sslRequired` a `external`/`all` con HTTPS). Ya hoy, a bajo costo: activar fuerza bruta en `master`.
3. Los Bajos se pueden resolver en la próxima tarea de devops. SEC-D-05, SEC-D-09 y SEC-D-10 son de esfuerzo S.
