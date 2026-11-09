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

# Iteración despliegue (D11-D13) e iteración 14

Sección retirada en la iteración 15 (VPN de malla eliminada; ver 09-despliegue.md). Se conservan solo los hallazgos que no dependían de la VPN y que otras secciones citan (SEC-D-03 y SEC-D-07).

## Hallazgos que siguen vigentes

### SEC-D-03 · Medio · A01 / A08 (integridad de los eventos)
**Los usuarios consumidores pueden publicar en `biohub.eventos` y los consumidores creen el campo `origen` del cuerpo: se pueden falsificar eventos.**

- **Archivos:** las definitions de RabbitMQ del despliegue anterior (retiradas en la iteración 15) (`write` incluye `biohub.eventos` y `biohub.eventos.dlx` para `consulta_mq` y `reportes_mq`), `servicio-*/.../eventos/service/ConsumidorEventos.java:57-67` (consulta-pública y reportes; valida `origen` no vacío pero no quién publicó), `servicio-cientifico/.../eventos/service/PublicadorEventos.java:50-55`.
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


---

# Parte de Juanita – ubicación y consulta pública

**Revisor:** ciberseguridad · **Fecha:** 2026-10-26 · **Rama:** `feature/ubicacion-consulta-publica` (worktree `biohub-ubicacion`), commits `65c8860..e186843` sobre `0cb19df` · **Alcance:** módulo `ubicacion` de `servicio-cientifico` (cifrado, llaves, política de acceso, búsqueda interna, mapa, re-cifrado), `servicio-consulta-publica` (consumidor `hallazgo.*`, búsqueda y ficha pública, caché Redis), `web-publica` y `web-interna/src/ubicacion/`, y dependencias nuevas (`react-router`, `leaflet`, `react-leaflet`, `org.testcontainers:mysql`).

## Resumen

| Severidad | Cantidad |
|---|---|
| Crítico | 0 |
| Alto | 0 |
| Medio | 3 |
| Bajo | 6 |
| Informativo | 6 |

**Veredicto: APROBADO CON OBSERVACIONES.** No encontré ninguna ruta, log, excepción, `toString()`, caché, evento ni respuesta por la que las coordenadas lleguen a un rol distinto de `cientifico_alto_rango`. El cifrado AES-256-GCM está bien hecho. Los 3 Medios no son explotables hoy, porque todavía no hay publicador real de `hallazgo.*` ni datos reales, pero condicionan el despliegue:
- **SEC-J-01** (integridad de eventos; incluye SEC-D-03) pasa a **Alto** si el publicador de Hernando se despliega en las VMs sin corregirlo.
- **SEC-J-02** y **SEC-J-03** (coordenadas en texto libre y en la DLQ) se deben resolver antes de cargar datos reales.

### Cómo se revisó
- **Documentación:**
  - `03-arquitectura.md`: D14-D33 y §J1-J14.
  - `04-contrato-api.md`: parte de Juanita.
  - `01-requisitos.md` §9.
  - `srs-biohub.md`: RF11-RF13, RF19, RNF9, ASR5, ASR6.
  - Hallazgos SEC-*, SEC-D-* y SEC-T-* de este reporte.
- **Código (estático):** todo `servicio-cientifico/.../ubicacion/**` (dominio, infraestructura, servicios, controladores y migraciones), `SecurityConfig` y el conversor JWT de ambos servicios, todo lo nuevo o modificado de `servicio-consulta-publica`, los dos `application.yml`, `.env.example`, `deploy/**` (Redis y variables), CORS del gateway, `web-publica/src/**`, `web-interna/src/ubicacion/**` y los tests de no filtración (`AuditoriaYSinFiltracionesTest` captura los logs en DEBUG, incluido el SQL).
- **Búsquedas dirigidas:**
  - Frontend: `dangerouslySetInnerHTML`, `innerHTML`, `bindPopup`, `localStorage`/`sessionStorage`, `console.*`, `target=`, `href=`, `eval`, `window.location`, `history.*`.
  - Backend: `activateDefaultTyping`, `JsonTypeInfo`, `TrustedPackages` y `__TypeId__`.
  - Consultas: SQL/JPQL construido con valores del cliente. Solo se concatenan fragmentos fijos y todos los valores van enlazados.
- **Pruebas:**
  - `mvn -B -pl servicio-cientifico,servicio-consulta-publica -am verify`: **BUILD SUCCESS**, 157 tests en científico (1 omitido) y 128 en consulta-pública. Una primera ejecución falló en `EventosPruebaControllerTest` por un `.class` ausente, causado por otra compilación que corría en paralelo en el mismo worktree; al repetirla pasó.
  - `vitest`: web-publica 32/32 y web-interna 45/45.
  - Contenedores Testcontainers: se eliminaron solos. Los que quedaron activos pertenecen a otra sesión que estaba ejecutando pruebas; no se tocaron las VMs ni el Compose local.
- **Dependencias:**
  - `npm audit` (todas las dependencias y solo producción): **0 vulnerabilidades** en las dos webs.
  - Versiones: `leaflet` 1.9.4, `react-leaflet` 5.0.0 (`@react-leaflet/core` 3.0.0) y `react-router` 8.5.0. Todas las entradas de los `package-lock.json` tienen `integrity`.
  - `org.testcontainers:mysql` tiene `scope test`, así que no llega al JAR.
  - Maven: no hay escáner de CVE instalado (`dependency-check`, `trivy`, `grype` y `osv-scanner` no están disponibles). SEC-15 sigue abierto.
- **Secretos:** se buscaron cadenas con forma de llave (`vN:` seguido de base64 de 32 bytes) en el árbol y en el historial de la rama, sin imprimir valores. Solo aparecen los hashes `integrity` de los lockfiles. No hay `application-test.yml` con llaves: los tests generan las suyas. `.env` y `dist/` están ignorados.

---

## Hallazgos

### SEC-J-01 · Medio · A08 Integridad / A01 (SEC-D-03 sigue abierto y ahora tiene impacto real)
**Un `hallazgo.*` falsificado se acepta como dato público y además puede "congelar" una ficha para siempre.**

- **Archivos:**
  - `servicio-consulta-publica/src/main/resources/application.yml:85` (`verificar-publicador: ${BIOHUB_EVENTOS_VERIFICAR_PUBLICADOR:false}`).
  - `.env.example:72` (`=false`).
  - `.../eventos/service/ConsumidorEventos.java:64-74`.
  - `.../eventos/domain/DatosHallazgo.java:18` (`@Min(1) Integer version`, sin tope).
  - `.../infrastructure/EscrituraFichasJdbc.java:24` (`WHERE ... version < ?`).
  - Del lado de Santiago: las definitions de RabbitMQ (retiradas con `deploy/`) (write de los consumidores sobre `biohub.eventos`) y `PublicadorEventos` sin `setUserId`.
- **Problema:** el consumidor ya implementa D24, pero con la verificación del `user_id` AMQP **desactivada por defecto**, y SEC-D-03 sigue abierto: `reportes_mq` y `consulta_mq` pueden publicar en `biohub.eventos`. El campo `origen` es texto del propio cuerpo y se falsifica trivialmente. Como "gana la versión mayor", un evento con `"version": 2147483647` deja la ficha con contenido del atacante, y **ningún evento legítimo posterior puede corregirla**: todos tienen una versión menor y se ignoran en silencio con un log DEBUG.
- **Por qué importa:** quien comprometa el servicio menos crítico (reportes) o robe su contraseña de RabbitMQ puede inyectar o desfigurar fichas del portal público. Por ejemplo, puede poner en `descripcion` coordenadas inventadas o reales de otra fuente y presentarlas como dato de BioHub, o bloquear la reclasificación de una especie a `EN_PELIGRO`. El daño persiste hasta una intervención manual en `mysql-lectura`. El reporte anterior pedía corregir SEC-D-03 "antes de implementar el evento real".
- **Cómo corregirlo:**
  1. **Santiago:** aplicar el punto 1 y el punto 2 de SEC-D-03 (permisos `write` mínimos y `setUserId(usuarioRabbit)` en `PublicadorEventos`).
  2. **consulta-pública: fallar cerrado.** Cambiar el valor por defecto a `true` y poner `false` solo de forma explícita en el entorno local o de pruebas:
     ```yaml
     verificar-publicador: ${BIOHUB_EVENTOS_VERIFICAR_PUBLICADOR:true}
     ```
     Así, en las VMs la verificación queda activa sin depender de que alguien recuerde activarla. Si hoy rompe el E2E porque el publicador aún no pone `user_id`, mejor: obliga a cerrar el punto 1.
  3. **consulta-pública: tope de cordura en `version`** y alerta en lugar de DEBUG cuando el salto es anómalo:
     ```java
     @NotNull @Min(1) @Max(1_000_000) Integer version,   // DatosHallazgo
     ```
     Documentar el procedimiento de reparación (borrar la fila en `mysql-lectura` y reenviar el último snapshot legítimo).
  4. Al activar la verificación, añadir un test de integración: publicar con un usuario AMQP distinto debe acabar en la DLQ y no crear ficha.
- **Esfuerzo:** S (consulta-pública), S (Santiago).
- **Responsable:** consulta-pública (puntos 2-4) y Santiago (punto 1, recomendación).
- **Condición:** **antes** de desplegar en las VMs el publicador real de Hernando. Si no se cumple, pasa a **Alto**.

### SEC-J-02 · Medio · A04 Insecure Design / A01 (fuga por texto libre)
**El consumidor solo bloquea claves con nombre de coordenada, pero publica tal cual el texto libre (`descripcion` hasta 5.000 caracteres, `nombreComun`, `tipoEspecimen`).**

- **Archivos:** `servicio-consulta-publica/.../eventos/service/ValidadorDatosHallazgo.java:74` (solo `contieneClaveProhibida` sobre las **claves**) y `:99` (`d.descripcion()` pasa directo a `FichaPublica`). Se ve en `web-publica/src/paginas/Ficha.jsx` (sección "Descripción").
- **Problema:** las claves extra se descartan (lector tolerante), así que el nombre de una clave nunca llega a `mysql-lectura`. Lo que sí llega es el **valor** de los campos de texto. Un investigador que escribe en la descripción "colectado en 5.53214, -73.36712" o "5°31'55\"N 73°22'02\"W" (algo habitual en fichas de campo) publica la coordenada exacta en el portal, en Redis y en la API pública, aunque todo lo demás de D14 esté bien.
- **Por qué importa:** es la vía más probable de fuga de una ubicación protegida. No requiere atacante, solo un error humano. Una vez publicada, queda en cachés HTTP de terceros (`Cache-Control: public`).
- **Cómo corregirlo:**
  1. **Hernando (fuente):** validar en el formulario y en la API de registro que `descripcion`, `nombreComun` y `tipoEspecimen` no contengan coordenadas, y avisar al usuario.
  2. **consulta-pública (defensa en profundidad):** detector de patrones en todos los campos de texto. Si encuentra coordenadas, el evento se descarta según SEC-J-03 (no a la DLQ con el texto en claro):
     ```java
     private static final Pattern COORD_DECIMAL = Pattern.compile(
             "[-+]?\\d{1,2}[.,]\\d{3,}\\s*[,;/ ]\\s*[-+]?\\d{1,3}[.,]\\d{3,}");
     private static final Pattern COORD_DMS = Pattern.compile(
             "\\d{1,3}\\s*[°º]\\s*\\d{1,2}\\s*['′]\\s*(\\d{1,2}([.,]\\d+)?\\s*[\"″])?\\s*[NSEOW]",
             Pattern.CASE_INSENSITIVE);
     static boolean pareceCoordenada(String t) {
         return t != null && (COORD_DECIMAL.matcher(t).find() || COORD_DMS.matcher(t).find());
     }
     ```
     Probarlo con los ejemplos de arriba y con falsos positivos razonables (fechas, medidas como "12,5 cm").
  3. Documentar la regla en `04` §7.3: "ningún campo de texto puede contener coordenadas".
- **Esfuerzo:** S (consulta-pública), S-M (Hernando).
- **Responsable:** consulta-pública y Hernando (recomendación).

### SEC-J-03 · Medio · A04 / A09 (coordenadas en claro retenidas en la DLQ)
**D24 manda a la DLQ precisamente los eventos que traen coordenadas. Así quedan en claro, sin TTL, en disco y en el panel de RabbitMQ.**

- **Archivos:**
  - `servicio-consulta-publica/.../eventos/service/ValidadorDatosHallazgo.java:74-77` (lanza `EventoInvalidoException`, que termina en la DLQ).
  - `.../config/RabbitConfig.java:58` (`QueueBuilder.durable(cola + ".dlq").build()`, sin `x-message-ttl` ni `x-max-length`; ver SEC-D-07).
  - `04-contrato-api.md` §7.5 ("`datos` con una clave de coordenadas → DLQ").
- **Problema:** si el publicador de Hernando incluye por error `latitud`/`longitud`, el mensaje persistente (`delivery_mode=2`) queda guardado indefinidamente en `consulta-publica.eventos.dlq` con las coordenadas exactas en claro. Cualquiera con acceso al panel o al volumen de RabbitMQ en la VM backend puede leerlo. El mismo mensaje llega también a `reportes.eventos` (Nicolás), que podría guardarlo o registrarlo. El procedimiento documentado de "Move messages" lo reinyectaría.
- **Por qué importa:** RNF9/ASR6 exigen que las coordenadas no existan en claro en ningún almacén persistente. La DLQ es un almacén persistente fuera del cifrado y fuera de la auditoría (D28).
- **Cómo corregirlo:**
  1. **consulta-pública:** tratar "trae coordenadas" (claves o texto, SEC-J-02) como **descartar con ack** y registrar un incidente sin valores, nunca DLQ:
     ```java
     // ConsumidorEventos.recibir()
     try {
         procesador.procesar(evento);
     } catch (EventoConCoordenadasException e) {   // subclase nueva, lanzada por ValidadorDatosHallazgo
         log.error("INCIDENTE: evento hallazgo.* con coordenadas descartado id={} tipo={}", evento.id(), evento.tipo());
         return; // ack: el mensaje desaparece del broker
     }
     ```
     Actualizar `04` §7.5, la fila correspondiente de §J5 y los tests de rechazo.
  2. **Hernando:** validar `datos` contra el JSON Schema de `04` §7.3 **antes** de publicar (fallar cerrado). Así el mensaje con coordenadas nunca llega a crearse en el broker; es la única barrera que evita que exista.
  3. **Santiago:** SEC-D-07 (TTL y `max-length` en las DLQ) y restringir el panel de RabbitMQ a administración.
  4. **Nicolás (reportes):** la misma regla en su consumidor.
- **Esfuerzo:** S.
- **Responsable:** consulta-pública. Recomendación para Hernando, Santiago y Nicolás.

### SEC-J-04 · Bajo · A04 (privacidad: fuga del área a las teselas OSM)
**Con "Ver en el mapa" de un solo hallazgo, el mapa se centra en la coordenada exacta a zoom 12 y pide a `tile.openstreetmap.org` las teselas alrededor del punto.**

- **Archivos:** `web-interna/src/ubicacion/componentes/MapaHallazgos.jsx:12` (`URL_TESELAS`) y `:27` (`fitBounds(..., { maxZoom: ZOOM_MAXIMO })`).
- **Problema:** las teselas de zoom 12 miden unos 10 km de lado. El conjunto de teselas pedido, centrado en el punto, permite a OSMF y a su CDN deducir el área (unos pocos km) de un hallazgo protegido, junto con la IP del científico y la hora. TLS oculta la ruta a terceros en la red, pero no al proveedor. R6 lo acepta para la vista general, pero el caso de un único punto es el peor.
- **Cómo corregirlo:**
  - Rápido: en el ajuste automático usar un zoom menor cuando hay pocos puntos, por ejemplo `fitBounds(pos, { maxZoom: puntos.length === 1 ? 8 : 10, padding: [32, 32] })`. El usuario puede acercar manualmente, lo que sigue revelando el área, pero ya por decisión propia.
  - Definitivo: servir las teselas desde un servidor propio o un proxy con caché en la VM backend, y no enviar `Referer` (`referrerPolicy: 'no-referrer'` en `TileLayer`).
- **Esfuerzo:** S (rápido), M (proxy).
- **Responsable:** web-interna-ubicacion. El proxy de teselas sería de Santiago.

### SEC-J-05 · Bajo · A04 (DoS de caché; Redis compartido sin límite de memoria)
**La caché `busquedas` tiene una clave por cada `q` distinto que mande el cliente, y Redis no tiene `maxmemory`.**

- **Archivos:** `servicio-consulta-publica/.../service/CriterioBusquedaPublica.java` (`claveCache()`), `.../service/ConsultaFichasService.java` (`@Cacheable` de `busquedas`), `deploy/backend/docker-compose.yml:166` y `docker-compose.yml:94` (solo `appendonly yes` y `requirepass`).
- **Problema:** con miles de `q` únicos por segundo, cada uno con una página de hasta 50 resúmenes, Redis crece sin límite durante el TTL de 60 s. Es el mismo Redis del rate limit del gateway, así que el efecto alcanza también al limitador. El rate limit por IP (pendiente de Santiago, R5) es lo único que lo acota.
- **Cómo corregirlo:**
  - Santiago: `maxmemory 256mb` y `maxmemory-policy volatile-lru` en el `redis.conf` generado (todas las claves de caché tienen TTL).
  - Opcional en consulta-pública: cachear las búsquedas solo para `page <= 2`, o con `q` ya visto (evita guardar consultas que nadie repite).
- **Esfuerzo:** S.
- **Responsable:** Santiago (recomendación) y consulta-pública (opcional).

### SEC-J-06 · Bajo · A05 / A04 (configuración de llaves: `.env.example` incompleto y modo efímero)
**`UBICACION_CLAVES` y `UBICACION_CLAVE_ACTIVA` no están en `.env.example` (lo exige §J3.3) ni en `deploy/`. La única vía documentada para arrancar sin ellas es la llave efímera.**

- **Archivos:** `.env.example:68-74` (solo variables de consulta-pública), `servicio-cientifico/src/main/resources/application.yml:62-66`, `.../ubicacion/infrastructure/ConfiguracionUbicacion.java:24-27` y `ProveedorDeLlavesEntorno.java:39-43`.
- **Problema:**
  - El modo efímero está bien protegido: exige `UBICACION_LLAVE_EFIMERA_DEV=true` **y** el perfil `dev`, y ningún archivo de `deploy/` activa perfiles (comprobado). Aun así, quien levante el servicio tras el merge encuentra un fallo de arranque sin plantilla de variables y tiene a mano la "solución rápida". Si esa combinación llega a una VM, las coordenadas reales se cifran con una llave que desaparece al reiniciar: pérdida irreversible (R1).
  - La llave efímera usa el id `v1`, el mismo que la primera llave real, así que las filas cifradas con ella aparecen después como fallos de `INTEGRIDAD` y no como llave desconocida, lo que confunde el diagnóstico.
- **Cómo corregirlo:**
  1. Añadir a `.env.example` (vacías):
     ```
     # Llaves AES-256 de ubicación (D20). Generar cada una con: openssl rand -base64 32
     # Formato: v1:<base64>,v2:<base64>  · Sin ellas servicio-cientifico NO arranca
     UBICACION_CLAVES=
     UBICACION_CLAVE_ACTIVA=
     ```
     Santiago debe añadirlas también a `deploy/generar-env.sh` (generación idempotente) y al compose del backend (J12.6).
  2. En el modo efímero, registrar un ERROR en lugar de WARN y negarse si el datasource no es local:
     ```java
     String url = entorno.getProperty("spring.datasource.url", "");
     if (!url.contains("//localhost") && !url.contains("//127.0.0.1")) {
         throw new IllegalStateException("UBICACION_LLAVE_EFIMERA_DEV solo se permite con una BD local");
     }
     ```
     Usar un id que no pueda coincidir con una llave real, por ejemplo `v9999`.
- **Esfuerzo:** S.
- **Responsable:** ubicación (`.env.example`, modo efímero) y Santiago (`deploy/`).

### SEC-J-07 · Bajo · A05 (sin CSP ni cabeceras de seguridad en las webs; SEC-16 sigue abierto)
- **Archivos:** `web-publica/index.html` (sin CSP), `web-publica/` sin `vercel.json`, y el mismo caso en web-interna.
- **Problema:** el portal ahora muestra texto que viene de la BD (fichas). React lo escapa y no hay `dangerouslySetInnerHTML` (comprobado), pero no hay una segunda barrera ante una regresión o ante una dependencia comprometida. Tampoco hay `frame-ancestors` (clickjacking) ni `Referrer-Policy`.
- **Cómo corregirlo:** `vercel.json` en cada web. Para web-publica:
  ```json
  { "headers": [{ "source": "/(.*)", "headers": [
    { "key": "Content-Security-Policy", "value": "default-src 'self'; connect-src 'self' https://<gateway>; img-src 'self' data:; style-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'" },
    { "key": "X-Content-Type-Options", "value": "nosniff" },
    { "key": "Referrer-Policy", "value": "strict-origin-when-cross-origin" },
    { "key": "Strict-Transport-Security", "value": "max-age=31536000" } ]}],
    "rewrites": [{ "source": "/(.*)", "destination": "/index.html" }] }
  ```
  En web-interna (Leaflet), además: `img-src 'self' data: https://tile.openstreetmap.org`, `style-src 'self' 'unsafe-inline'` (Leaflet usa estilos en línea) y `Referrer-Policy: no-referrer`.
- **Esfuerzo:** S.
- **Responsable:** web-publica, y Santiago para web-interna y Vercel.

### SEC-J-08 · Bajo · A09 (huecos en la auditoría D28)
- **Archivos:** `servicio-cientifico/.../ubicacion/controller/UbicacionExceptionHandler.java:170-172` y `.../service/AuditoriaAccesoUbicacion.java:14-17`.
- **Problema:**
  1. Los 403 de `POST /recifrado` no se auditan, por la excepción explícita `!endsWith("/recifrado")`. Un intento de un no administrador sobre la operación criptográfica es justo lo que conviene ver.
  2. El campo `endpoint` (que sale de `getRequestURI()`) no pasa por `limpiar()`, a diferencia de `usuario` y `filtros`. Hoy no es explotable porque Tomcat no admite CR/LF en la línea de petición y la URI queda codificada, pero es inconsistente.
  3. La auditoría es solo un log. Se puede apagar con `logging.level.biohub.auditoria.ubicacion=OFF` y se pierde con la rotación.
- **Cómo corregirlo:** auditar también el 403 de re-cifrado con `permitido=false`, aplicar `limpiar(endpoint, 200)` y enviar el logger `biohub.auditoria.ubicacion` a un appender propio, persistente y de solo anexado. Más adelante, la tabla `ubicacion_acceso_log` (Could).
- **Esfuerzo:** S.
- **Responsable:** ubicación.

### SEC-J-09 · Bajo · A07 (identidad: revocación de rol y MFA; lado de Santiago)
- **Problema:**
  - RF19 pide que una revocación de rol se refleje "de inmediato", pero un token de `cientifico_alto_rango` revocado sigue sirviendo hasta 5 minutos (R4).
  - Sigue sin MFA para `cientifico_alto_rango`, que es el único rol que ve coordenadas.
- **Cómo corregirlo (Santiago):** bajar el `accessTokenLifespan` a 1-2 minutos, o introspección o lista de revocación en el gateway solo para `/api/cientifico/ubicacion/mapa` y `/hallazgos`. Para MFA, OTP condicional por rol en Keycloak.
- **Esfuerzo:** M.
- **Responsable:** Santiago (recomendación).

### Informativos
- **SEC-J-I1:** J3.1 dice que una colisión del `UNIQUE (clave_version, coord_nonce)` "se reintenta una vez con otro nonce", pero no está implementado: el registro fallaría y se revertiría. La probabilidad con nonces aleatorios de 96 bits es despreciable y el fallo es seguro (nunca reutiliza un nonce). Basta con ajustar el texto de J3.1.
- **SEC-J-I2:** las llaves viven en el heap (el `String` de `PropiedadesCifrado` y los bytes decodificados no se borran). No se debe activar `-XX:+HeapDumpOnOutOfMemoryError` hacia disco compartido ni exponer `heapdump`/`env`/`configprops` en actuator (hoy solo `health,info`, correcto).
- **SEC-J-I3:** la ficha pública muestra `fechaRecoleccion` exacta y el departamento también para `EN_PELIGRO`. Combinado con expediciones conocidas puede ayudar a acotar la zona. Propuesta de producto: para especies en peligro, publicar solo mes y año (práctica de datos sensibles de GBIF).
- **SEC-J-I4:** la búsqueda interna muestra `investigador` (`preferred_username`) a los 5 roles internos y permite filtrar por él. Es coherente con el contrato. Conviene confirmar con la dueña del producto que `analista_ambiental` lo necesita (minimización).
- **SEC-J-I5 (Hernando):** los campos personalizados de ASR5 (columnas JSON/EAV) no deben admitir coordenadas en claro. Si no, se salta RNF9 por fuera del módulo de ubicación. Sus DTOs de entrada con latitud/longitud deben enmascarar `toString()` (`04` §6, obligación 2), porque con `logging.level.org.springframework.web=DEBUG` Spring registra el cuerpo leído usando `toString()`.
- **SEC-J-I6:** con dos instancias, el `AtomicBoolean` del re-cifrado es por instancia. `FOR UPDATE SKIP LOCKED` evita el doble procesamiento (correcto). Las filas bloqueadas por un registro en curso se saltan y quedan en `pendientesPorVersion`, así que hay que repetir el re-cifrado hasta que llegue a 0 antes de retirar la llave vieja (ya está en J3.5, paso 3).

---

## Bien hecho
- **Cifrado:**
  - AES/GCM/NoPadding con tag de 128 bits, nonce de 12 B de `SecureRandom` nuevo en **cada** cifrado (también al re-cifrar) y AAD = UUID canónico.
  - Se valida la longitud del nonce y del texto plano. `AEADBadTagException` y cualquier `GeneralSecurityException` se convierten en `INTEGRIDAD` sin propagar la causa.
  - Se borra el texto plano (`Arrays.fill`).
  - El texto plano de `recifrar` no sale del cifrador.
  - Hay `CHECK` de longitud y `UNIQUE (clave_version, coord_nonce)` en la BD.
  - No existen columnas en claro.
- **Llaves:**
  - Se validan al arrancar (32 bytes, id `^v[1-9][0-9]{0,3}$`, sin repetidos y con la activa presente).
  - Los mensajes nombran la variable, nunca el valor (con test).
  - `toString()` enmascarado.
  - Se usan en todos los perfiles.
  - El modo efímero exige perfil y bandera a la vez (con test).
- **Autorización centralizada:**
  - `PoliticaAccesoUbicacion` excluye explícitamente `AnonymousAuthenticationToken`.
  - El mapa tiene `@PreAuthorize` más `exigirCoordenadas` en el servicio.
  - `ServicioDescifrado` vuelve a exigir el rol antes de tocar el cifrador.
  - Para roles sin permiso, la consulta **ni siquiera selecciona** `coord_*` (minimización).
  - `bbox` responde 403 o 400 según el rol.
  - El re-cifrado es solo para `administrador` y nunca devuelve coordenadas.
  - No hay forma de llegar a las coordenadas por `hallazgoId`, parámetros o re-cifrado sin el rol.
- **Sin filtraciones:**
  - `toString()` enmascarado en `Coordenadas`, `CoordenadasCifradas`, `UbicacionHallazgo`, `FilaHallazgo`, `RegistroUbicacionComando` y los DTOs de respuesta (también en el `toString` anidado de los records contenedores). Por eso el log DEBUG "Writing [...]" de Spring MVC no las muestra.
  - Un test captura los logs en DEBUG, incluido el SQL.
  - Los errores son `problem+json` sin trazas.
  - Todas las respuestas internas, incluidos los errores, llevan `Cache-Control: no-store`.
  - Actuator expone solo `health,info`.
- **Inyección:**
  - JPQL y SQL con parámetros enlazados. Solo se añaden fragmentos fijos según los filtros.
  - `LIKE` escapado (`!` en la búsqueda interna y `\` en la pública).
  - En el FULLTEXT se eliminan todos los caracteres que no son letra ni número, así que no quedan operadores.
  - Orden fijo, sin ordenamiento controlado por el cliente.
  - Las claves de caché se codifican por componente (sin colisiones por `|`/`=`).
  - Probado con `' OR '1'='1`.
- **Eventos:**
  - Límite de 64 KB y `datos` como `Map` sin tipado polimórfico.
  - `__TypeId__` ignorado.
  - Mapeo estricto (sin coerción de escalares) y Bean Validation.
  - Búsqueda recursiva de claves prohibidas, sin distinguir mayúsculas.
  - Valor de estado desconocido → `EN_PELIGRO`.
  - `usuario` del sobre nunca guardado.
  - UPSERT atómico `WHERE version < ?`.
  - Invalidación de caché después del commit.
- **Caché:** `Jackson2JsonRedisSerializer` tipado a los DTOs públicos (sin `activateDefaultTyping`), prefijo `biohub:publica:`, Redis caído tratado como miss y probado con el contenedor detenido. No se cachean 404 ni errores.
- **Disponibilidad:**
  - `size` ≤ 50 en la pública y ≤ 100 en la interna; `(page+1)*size ≤ 10.000`.
  - Mapa limitado a 1.000 elementos, con `truncado`.
  - Descifrado limitado a la página.
  - Hikari a 2 s y Redis a 300 ms.
- **Frontend:**
  - Listas blancas en los dos adaptadores: `aHallazgo` descarta latitud y longitud si el rol no es alto rango, aunque lleguen.
  - El mapa no llama a la API sin el rol.
  - Las coordenadas viven solo en el estado del componente y se descartan con 401/403.
  - `credentials: 'omit'` y `cache: 'no-store'`.
  - Sin storage, sin `console.*` y sin `dangerouslySetInnerHTML`.
  - El `title` del marcador se asigna como atributo, sin HTML.
  - `state.volver` se valida (`startsWith('?')`): no hay redirección abierta.
  - Un 404 y un 400 se muestran igual en la ficha.
- **Pruebas:** con Testcontainers efímeros. No se tocaron las VMs (D30).

## Condicionado a otros integrantes (recomendaciones)
| Quién | Qué | Antes de |
|---|---|---|
| Santiago | SEC-D-03 (permisos `write` y `setUserId`) → SEC-J-01 | Desplegar el publicador real en las VMs |
| Santiago | `UBICACION_CLAVES`/`UBICACION_CLAVE_ACTIVA` en `generar-env.sh` y en el compose, con copia de custodia fuera de la VM (R1) y nunca junto a los respaldos de la BD (SEC-D-06) → SEC-J-06 | Desplegar el módulo `ubicacion` |
| Santiago | Rate limit por IP en `/api/publica/**` y `maxmemory` en Redis → SEC-J-05; SEC-D-07 (TTL en las DLQ) → SEC-J-03 | Exponer el portal a internet |
| Santiago | CSP en web-interna, montar `rutasUbicacion` sin exponer el mapa a otros roles, MFA y vida del token → SEC-J-07, SEC-J-09 | Datos reales |
| Hernando | Validar el JSON Schema antes de publicar, sin coordenadas en texto libre ni en campos ASR5, outbox y `version` creciente → SEC-J-02, SEC-J-03, SEC-J-I5 | Su primera publicación de `hallazgo.*` |
| Nicolás | Misma regla de descarte de eventos con coordenadas en `reportes` → SEC-J-03 | Consumir `hallazgo.*` |

## Veredicto (parte de Juanita)
**APROBADO CON OBSERVACIONES.** No hay Críticos ni Altos. La confidencialidad de las coordenadas dentro del alcance revisado es sólida: el cifrado es correcto, el control de rol está centralizado y tiene doble barrera, y no hay fugas por logs, errores, caché ni eventos salientes. Condiciones:
1. **Antes de desplegar el publicador de `hallazgo.*` en las VMs:** SEC-J-01 (cambiar el valor por defecto a `true` y poner el tope de versión, más SEC-D-03 de Santiago). Si no se cumple, SEC-J-01 pasa a **Alto**.
2. **Antes de datos reales:** SEC-J-02 y SEC-J-03.
3. Los Bajos (SEC-J-04 a SEC-J-09), en la siguiente iteración de cada responsable.

---

# Iteración 15h – auditoría final (2026-11-04)

**Revisor:** ciberseguridad · **Rama:** `feature/ubicacion-consulta-publica` (HEAD `8cab133`) **más el working tree sin commit** · **Alcance:** `servicio-cientifico` paquete `co.edu.unbosque.biohub.cientifico.ubicacion` (y los eventos de dominio nuevos de `cientifico.eventos.domain` que escucha), `servicio-consulta-publica` completo, `.env.example`, `application.yml`, CI, Bruno y `pruebas-carga/`.

> **Numeración:** `SEC-J-09` ya existía (identidad, lado de Santiago). Los hallazgos nuevos empiezan en **SEC-J-10** para no pisar el anterior.

## Resumen

| Severidad | Cantidad (nuevos) |
|---|---|
| Crítico | 0 |
| Alto | 0 |
| Medio | 2 (SEC-J-10, SEC-J-11) |
| Bajo | 3 (SEC-J-12, SEC-J-13, SEC-J-14) |
| Informativo | 3 (SEC-J-I7 a SEC-J-I9) |

**Veredicto: APROBADO CON OBSERVACIONES.** La refactorización (Decorator, Strategy, Adapter de keystore, Observer y `service/vista`) **no rompió** ningún control de SEC-J-01 a SEC-J-08: las coordenadas siguen saliendo solo hacia `cientifico_alto_rango`, se cifran antes de llegar a JPA, el re-cifrado no expone texto plano, cada descifrado y cada 403 quedan auditados y la llave efímera sigue limitada a una BD local. Los dos Medios son de **diseño y configuración**, no se pueden explotar desde fuera hoy y se corrigen con poco esfuerzo (S).

### Cómo se revisó
- **Documentación:** 03-arquitectura (D34 a D41), 04-contrato-api y los hallazgos SEC-J-*, SEC-D-03 y SEC-D-07 de este reporte.
- **Código (estático):** `git status`, `git diff` y todos los archivos nuevos sin seguimiento de los dos servicios. Se leyeron completos `ConfiguracionUbicacion`, los tres decoradores y `RepositorioUbicacionesJpa`, `ProveedorDeLlavesKeyStore`/`Entorno`, `PropiedadesCifrado`, `PoliticaAccesoUbicacion`, `VisibilidadExacta`/`Generalizada`, `ServicioDescifrado`, `BusquedaHallazgosService`, `MapaService`, `RecifradoService`, `UbicacionServiceImpl`, `ObservadorUbicacion`, `HallazgoRegistrado`/`Reclasificado`, los controladores y `UbicacionExceptionHandler`, los DTO de `service/vista`, `ConsumidorEventos`, `DetectorCoordenadas`, `MonitorCarga`, `MonitorCargaFilter`, `ConfiguracionDegradacion`, `ElectorEstrategiaBusqueda`, `BusquedaCompleta`/`Degradada` y `CriterioBusquedaPublica.claveCache()`.
- **Búsquedas dirigidas:**
  - Quién inyecta `CifradorCoordenadas`, `UbicacionHallazgoRepository`, `ServicioDescifrado` y `RepositorioUbicaciones`.
  - Quién publica `HallazgoRegistrado` (hoy nadie: `registro` está vacío).
  - Cadenas con forma de llave (`vN:` + base64 de 32 bytes) en todo el árbol: sin coincidencias reales.
  - `.env` ignorado (`!! .env`, permisos 600). No hay `.p12`/`.jks` en el árbol.
- **Pruebas:** `mvn -B -pl servicio-cientifico -am verify`: **229 tests, 0 fallos, 1 omitido** (incluye `ArquitecturaUbicacionTest`, `DecoradoresRepositorioUbicacionesTest`, `DecoradorAuditoriaIntegracionTest`, `ProveedorDeLlavesKeyStoreTest`, `ObservadorUbicacion*Test` y los QA de JWT, cifrado e inyección). **No** se ejecutó la suite de consulta-pública porque otro agente estaba editando `servicio-consulta-publica/src/test/**`. Esos cambios en curso no se revisaron.
- **Dependencias:** `archunit-junit5` 1.4.1 (con `archunit` 1.4.1 y `archunit-junit5-engine` 1.4.1 como transitivas) queda **solo en `scope test`** en los dos `pom.xml` (confirmado con `dependency:tree`) y no llega al JAR. No hay escáner de CVE disponible (`osv-scanner`, `trivy`, `grype` y `dependency-check` no están instalados). SEC-15 sigue abierto.
- **Cambios concurrentes:** durante la auditoría, `ConsumidorEventos.java:43` y el comentario de `servicio-consulta-publica/src/main/resources/application.yml:91` cambiaron en el working tree (otro agente). Se reporta el estado **al cierre de la revisión**.

---

## Estado de los hallazgos previos

| ID | Estado | Evidencia |
|---|---|---|
| SEC-J-01 | **Parcial**: cerrado en código, abierto en configuración y en el publicador | Cerrado: `consulta-publica/.../application.yml:92` (`verificar-publicador: ...:true`), `ConsumidorEventos.java:43` (valor por defecto `true` al cierre), `:46` (`STRICT_DUPLICATE_DETECTION`), `:125-129` (rechazo si `receivedUserId` ≠ esperado), `DatosHallazgo.java:19,33` (`@Max(1_000_000)`) y `ProcesadorHallazgos.java:27,49-55` (alertas de salto anómalo). Abierto: `.env.example:72` de la raíz sigue en `false` (SEC-J-11); `cientifico/.../PublicadorEventos.java:50-56` no fija `user_id`; SEC-D-03 y SEC-04 (usuario de RabbitMQ compartido) siguen abiertos |
| SEC-J-02 | **Cerrado** (lado de Juanita) | `DetectorCoordenadas.java:57-66` (decimal, hemisferio, grados-minutos y palabra clave), aplicado de forma recursiva a claves y valores en `ConsumidorEventos.java:67,77-89`. Del lado de científico: `DetectorCoordenadasEnTexto` y `UbicacionServiceImpl.java:93-95` (`nombreCientifico`). La validación en origen (Hernando) sigue pendiente |
| SEC-J-03 | **Cerrado** (lado de Juanita) | `ConsumidorEventos.java:62-74,91-106`: el evento con coordenadas se descarta con ack, se registra el incidente sin valores y no pasa por la DLQ. Sigue abierto SEC-D-07 (`RabbitConfig.java:58`, DLQ sin TTL ni `max-length`) para los demás rechazos |
| SEC-J-04 | **Abierto** → **Iteración 16: Parcial** (zoom 8/10 y `no-referrer` hechos; falta el proxy de Santiago) | `web-interna/src/ubicacion/componentes/MapaHallazgos.jsx:10,27` (`maxZoom` 12 también con un solo punto) y `:45` (`TileLayer` sin `referrerPolicy`) |
| SEC-J-05 | **Abierto** | `docker-compose.yml:94`: el `redis.conf` generado solo tiene `appendonly` y `requirepass`, sin `maxmemory` |
| SEC-J-06 | **Cerrado** | `servicio-cientifico/.env.example:58-62` (variables y aviso). `ConfiguracionUbicacion.java:47-58` exige el perfil `dev`, la bandera **y** una BD local. `:63-70` (`esBaseLocal` con regex anclada: rechaza `localhost.evil`, multi-host `localhost:3306,otro` y `loadbalance`). `ProveedorDeLlavesEntorno.java:26` (id `v9999`). Con el proveedor `keystore` la llave efímera no se puede activar (`ConfiguracionUbicacion.java:34-42` retorna antes) |
| SEC-J-07 | **Parcial** | `web-publica/index.html:9-12`: CSP en `<meta>` y `referrer`. `frame-ancestors` y HSTS no se pueden fijar por `<meta>` y siguen faltando como cabeceras. `web-interna/index.html` sigue sin CSP |
| SEC-J-08 | **Parcial** (puntos 1 y 2 cerrados, 3 abierto) → **Iteración 16: Parcial** (appender propio hecho; falla abierto, SEC-J-18) | (1) `UbicacionExceptionHandler.java:62-65` audita todos los 403, también `POST /recifrado`. (2) `AuditoriaAccesoUbicacion.java:17` pasa `endpoint` por `limpiar(..., 200)`. (3) No hay appender dedicado ni de solo anexado: no existe `logback-spring.xml` en `servicio-cientifico/src/main/resources/`, así que la auditoría se apaga con el nivel de log |
| SEC-J-09 | **Abierto** (Santiago, fuera de alcance) | `infra/keycloak/realm-biohub.json:18` (`accessTokenLifespan: 300`), sin OTP condicional |
| SEC-D-03 / SEC-D-07 | **Abiertos** | Ver SEC-J-11 y SEC-J-03 |

### Verificación específica de la refactorización
- **Coordenadas nunca en claro fuera del permiso:** los DTO movidos a `service/vista` conservan el `toString()` enmascarado (`FeatureResponse.java:14-17,31-34`, `HallazgoUbicacionResponse.java:23-26`), igual que `FilaHallazgo.java:21-24`, `UbicacionResuelta.java:31-34` y `HallazgoRegistrado.java:41-44`. `VisibilidadGeneralizada` no recibe `ServicioDescifrado` y devuelve latitud y longitud `null` (`VisibilidadGeneralizada.java:14-19`). Con ella, `necesitaCoordenadasCifradas()` es `false` y el texto cifrado ni se selecciona (`RepositorioUbicacionesJpa.java:72`).
- **Doble barrera de rol:** `@PreAuthorize` en el mapa (`MapaUbicacionController.java:27`), `exigirCoordenadas` en `MapaService.java:39`, `estrategiaPara` en `PoliticaAccesoUbicacion.java:38-51` y otra vez `exigirCoordenadas` en `ServicioDescifrado.java:39`. El constructor sin argumentos de la política falla cerrado (`:44-46`).
- **Cifrado en reposo con versión:** `DecoradorCifradoCoordenadas.java:30-37` cifra y llama a `asignarCifrado` antes de delegar, y le pasa `null` al componente JPA. Como red de seguridad, `RepositorioUbicacionesJpa.java:53-58` rechaza coordenadas en claro. Hay test que comprueba el orden de la cadena (`DecoradoresRepositorioUbicacionesTest.java:176-185`).
- **Re-cifrado:** `RecifradoService.java:95-121` no cambió de comportamiento: el texto plano no sale de `cifrador.recifrar`, se mantiene `FOR UPDATE SKIP LOCKED` con parámetros enlazados y se audita el agregado (`:87`).
- **Orden de decoradores:** auditoría → cifrado → JPA (`ConfiguracionUbicacion.java:82-90`). El único bean `RepositorioUbicaciones` es la cadena completa; los decoradores y `RepositorioUbicacionesJpa` **no** son beans. Ver SEC-J-10 para los caminos que aun así esquivan la auditoría.

---

## Hallazgos nuevos

### SEC-J-10 · Medio · A01 / A09 (la "segunda barrera" y la auditoría se pueden esquivar desde código del mismo servicio)
**Tras la refactorización, la capacidad de descifrar está en dos interfaces que inyecta cualquier bean, y ArchUnit no lo restringe.**

- **Archivos:**
  - `servicio-cientifico/.../ubicacion/domain/RepositorioUbicaciones.java:43,48`: `obtenerUbicacionExacta` es pública en el puerto general y el javadoc dice "La autorización por rol NO se decide aquí".
  - `.../infrastructure/ConfiguracionUbicacion.java:72-75`: `CifradorCoordenadas` es un bean inyectable con `descifrar` público (`domain/CifradorCoordenadas.java:12`).
  - `.../infrastructure/DecoradorCifradoCoordenadas.java:23` y `RepositorioUbicacionesJpa.java:37`: constructores públicos, así que se puede armar una cadena sin auditoría.
  - `.../infrastructure/DecoradorAuditoriaAcceso.java:29-37`: solo audita el éxito y `CoordenadasNoDescifrablesException`; cualquier otra `RuntimeException` sale sin línea de auditoría.
  - `src/test/.../ArquitecturaUbicacionTest.java:40-47`: la regla de `ServicioDescifrado` solo mira `..ubicacion..` y no hay regla para `CifradorCoordenadas` ni para quién llama a `obtenerUbicacionExacta`.
- **Problema:** `RepositorioUbicaciones` lo reciben `BusquedaHallazgosService`, `MapaService`, `UbicacionServiceImpl` y `ServicioDescifrado`, y lo puede recibir cualquier bean futuro del contexto, incluido el módulo `registro` de Hernando, que convive en el mismo `ApplicationContext`. Cualquiera de ellos puede llamar a `repositorio.obtenerUbicacionExacta(...)` **sin pasar por `PoliticaAccesoUbicacion`**: la llamada se audita como `PERMITIDO`, pero no se controla el rol. Inyectando `CifradorCoordenadas` directamente, se descifra **sin rol y sin auditoría**. Antes de la refactorización el descifrado estaba concentrado en `ServicioDescifrado`. Ahora la documentación (`RepositorioUbicaciones.java:10-11`, README, D40) afirma que "no pueden saltarse el cifrado ni la auditoría", pero solo lo impide la convención.
- **Por qué importa:** RF13/D21 y D28 son los controles centrales del activo más sensible. Basta un error de un desarrollador (por ejemplo, un endpoint de detalle de hallazgo en `registro` que reutilice el repositorio) para que un `investigador_campo` reciba coordenadas, y nada en CI lo detectaría. No es explotable desde fuera hoy: no existe ese código.
- **Corrección:**
  1. Poner el rol **dentro** de la cadena: que el decorador de auditoría reciba la `Authentication` y vuelva a exigir el rol antes de delegar, así la barrera viaja con el repositorio:
     ```java
     // RepositorioUbicaciones
     Coordenadas obtenerUbicacionExacta(Authentication quien, UUID hallazgoId, CoordenadasCifradas cifradas);

     // DecoradorAuditoriaAcceso
     @Override
     public Coordenadas obtenerUbicacionExacta(Authentication quien, UUID id, CoordenadasCifradas c) {
         if (!politica.puedeVerCoordenadas(quien)) {
             auditoria.registrar(nombre(quien), ACCION, false, "hallazgoId=" + id, 0, 0);
             throw new AccessDeniedException("Sin permiso para ver coordenadas");
         }
         try {
             Coordenadas r = envuelto.obtenerUbicacionExacta(quien, id, c);
             auditoria.registrar(quien.getName(), ACCION, true, "hallazgoId=" + id, 1, 0);
             return r;
         } catch (RuntimeException e) {                      // cualquier fallo queda auditado
             auditoria.registrar(quien.getName(), ACCION, true, "hallazgoId=" + id, 0, 1);
             throw e;
         }
     }
     ```
  2. Reglas ArchUnit sobre **todo** `co.edu.unbosque.biohub.cientifico`:
     ```java
     @Test void soloServicioDescifradoPideCoordenadasExactas() {
         noClasses().that().doNotHaveSimpleName("ServicioDescifrado")
             .and().resideOutsideOfPackage("..ubicacion.infrastructure..")
             .should().callMethodWhere(target(name("obtenerUbicacionExacta"))
                 .and(target(owner(assignableTo(RepositorioUbicaciones.class)))))
             .check(CLASES);
     }
     @Test void cifradorSoloEnInfraestructuraYRecifrado() {
         noClasses().that().resideOutsideOfPackage("..ubicacion.infrastructure..")
             .and().doNotHaveSimpleName("RecifradoService")
             .should().dependOnClassesThat().areAssignableTo(CifradorCoordenadas.class).check(CLASES);
     }
     @Test void decoradoresSoloLosConstruyeLaConfiguracion() {
         noClasses().that().doNotHaveSimpleName("ConfiguracionUbicacion")
             .should().callConstructor(DecoradorCifradoCoordenadas.class, RepositorioUbicaciones.class,
                                       CifradorCoordenadas.class, Clock.class)
             .check(CLASES);   // ídem para RepositorioUbicacionesJpa y DecoradorAuditoriaAcceso
     }
     ```
     y ampliar `soloLaEstrategiaExactaUsaElServicioDeDescifrado` a todo el servicio (quitar `resideInAPackage("..ubicacion..")`).
  3. Hacer *package-private* los constructores de los decoradores y de `RepositorioUbicacionesJpa` (los construye `ConfiguracionUbicacion`, que está en el mismo paquete).
- **Esfuerzo:** S · **Responsable sugerido:** ubicación (Juanita).

### SEC-J-11 · Medio · A05 / A08 (ARQ-13 queda anulado por la plantilla de la raíz; SEC-J-01 sigue abierto en la práctica)
**La verificación del publicador AMQP es `true` por defecto en el servicio, pero el `.env.example` de la raíz (el `.env` que cargan todos los servicios) la pone en `false`.**

- **Archivos:**
  - `.env.example:71-72` (raíz): `# ... (false). Activar cuando el publicador lo ponga` y `BIOHUB_EVENTOS_VERIFICAR_PUBLICADOR=false`. No cambió en esta iteración.
  - `servicio-consulta-publica/src/main/resources/application.yml:7`: `import: optional:file:../.env[.properties],optional:file:./.env[.properties]`. Se lee **primero el `.env` de la raíz**; el `.env` del servicio solo gana si existe.
  - `servicio-consulta-publica/.env.example:71` (`true`) contradice a la raíz.
  - `servicio-cientifico/.../eventos/service/PublicadorEventos.java:50-56`: no hace `setUserId(...)`.
  - `docker-compose.yml:111-112`: un único usuario de RabbitMQ, administrador, para todos los servicios (SEC-04 pendiente).
- **Problema:** con D34 ya no hay un despliegue distinto del Compose local, así que la configuración real es la de las plantillas. Quien siga el README y copie `.env.example` → `.env` en la raíz deja la verificación **apagada**: el WARN de `ConsumidorEventos.java:51-54` aparece, pero el servicio arranca igual. Aun con la verificación activa, mientras todos los servicios usen el mismo usuario `biohub` (administrador), el `user_id` no distingue a cientifico de reportes. Además, cuando Hernando publique `hallazgo.*` reales con `PublicadorEventos`, sin `user_id` irán todos a la DLQ (falla cerrado, que es lo correcto, pero empujará a alguien a "arreglarlo" poniendo `false`).
- **Por qué importa:** es la integridad del portal público (SEC-J-01): un evento falsificado crea o desfigura fichas. ARQ-13 se presenta como cerrado y en la práctica no lo está.
- **Corrección:**
  1. `.env.example` (raíz):
     ```properties
     # Verificar el user_id AMQP del publicador de hallazgo.* (ARQ-13, SEC-J-01). true por defecto.
     # false SOLO en local sin publicador real; el servicio avisa con un WARN.
     BIOHUB_EVENTOS_VERIFICAR_PUBLICADOR=true
     # Usuario AMQP con el que publica servicio-cientifico (biohub en el compose local mientras no exista cientifico_mq)
     BIOHUB_EVENTOS_PUBLICADOR_CIENTIFICO=cientifico_mq
     ```
  2. Hernando/Santiago: SEC-D-03 punto 2 en `PublicadorEventos`:
     ```java
     mensaje.getMessageProperties().setUserId(usuarioRabbit);   // @Value("${spring.rabbitmq.username}")
     ```
  3. Santiago: SEC-04/SEC-D-03 punto 1 (usuarios por servicio; los consumidores sin `write` sobre `biohub.eventos`) también en el `docker-compose.yml` de la raíz (por ejemplo con `definitions.json`). Sin esto, la verificación no aísla.
  4. Opcional: con `verificar-publicador=false` y un perfil distinto de `local`/`test`, negarse a arrancar (mismo patrón que SEC-J-06).
- **Esfuerzo:** S (plantilla y `setUserId`), M (usuarios por servicio) · **Responsable sugerido:** consulta-pública (plantilla), Hernando (`setUserId`), Santiago (usuarios de RabbitMQ).

### SEC-J-12 · Bajo · A02 / A05 (gestión de la contraseña del keystore PKCS12)
- **Archivos:** `servicio-cientifico/.../infrastructure/ConfiguracionUbicacion.java:36,41` (`clave.toCharArray()` nunca se borra), `PropiedadesCifrado.java:17-18` (la contraseña queda como `String` en el bean y en el `Environment`), `ProveedorDeLlavesKeyStore.java:40-47` (no revisa los permisos del archivo) y `:70` (usa la misma contraseña para el almacén y las entradas).
- **Problema:** el adaptador está bien: falla cerrado si falta la ruta, la contraseña o la llave activa, no tiene fallback inseguro, no hace fallback a `entorno`, el mensaje de error no incluye ni la ruta ni la causa y exige alias `vN` y AES de 32 bytes, con tests. Pero:
  - La contraseña vive en el heap durante toda la vida del proceso (como en SEC-J-I2).
  - Un `.p12` con permisos `644` se acepta sin aviso.
  - Contraseña y ruta están en el mismo `.env` que todo lo demás, así que el keystore solo añade protección si la contraseña llega por otro canal (variable del sistema o secreto del orquestador).
- **Corrección:**
  ```java
  // ConfiguracionUbicacion
  char[] pwd = clave == null ? null : clave.toCharArray();
  try {
      return new ProveedorDeLlavesKeyStore(Path.of(ruta.trim()), pwd, propiedades.claveActiva());
  } finally {
      if (pwd != null) java.util.Arrays.fill(pwd, '\0');
  }
  // ProveedorDeLlavesKeyStore, al validar la ruta
  try {
      var permisos = Files.getPosixFilePermissions(ruta);
      if (permisos.contains(PosixFilePermission.OTHERS_READ) || permisos.contains(PosixFilePermission.GROUP_READ)) {
          throw new IllegalStateException("UBICACION_KEYSTORE_RUTA: el keystore no debe ser legible por grupo u otros (chmod 600)");
      }
  } catch (UnsupportedOperationException noPosix) { /* Windows: no aplica */ }
  ```
  En el README, recomendar `UBICACION_KEYSTORE_PASSWORD` como variable del sistema o secreto, no en el `.env` junto a la ruta.
- **Esfuerzo:** S · **Responsable sugerido:** ubicación.

### SEC-J-13 · Bajo · A04 (el evento de dominio con coordenadas se puede serializar hacia el broker)
- **Archivos:** `servicio-cientifico/.../eventos/domain/HallazgoRegistrado.java:37-38` (`latitud`/`longitud` como componentes del record, que Jackson serializa) y `.../eventos/service/PublicadorEventos.java:46` (`publicar(..., Map<String,Object> datos)` acepta cualquier mapa, sin guarda de coordenadas).
- **Problema:** el javadoc pide no copiar las coordenadas al `EventoSobre`, y `toString()` las enmascara, pero el `@TransactionalEventListener(AFTER_COMMIT)` de registro armará `datos` a partir de este mismo record. Un `objectMapper.convertValue(evento, Map.class)` las enviaría a `biohub.eventos`. Consulta-pública las descartaría (SEC-J-03), pero llegarían a `reportes.eventos` y quedarían en el broker. El publicador es la única barrera que impide que el mensaje exista.
- **Corrección:**
  ```java
  // HallazgoRegistrado
  @JsonIgnore Double latitud,
  @JsonIgnore Double longitud,
  // PublicadorEventos.publicar, antes de construir el sobre
  if (tipo.startsWith("hallazgo.") && DetectorCoordenadasEnTexto.contieneEnMapa(datos)) {   // claves + texto, como en consulta-pública
      throw new IllegalArgumentException("hallazgo.* no puede llevar coordenadas (D14)");
  }
  ```
  y un test que serialice un `HallazgoRegistrado` y compruebe que no aparecen `latitud`/`longitud`.
- **Esfuerzo:** S · **Responsable sugerido:** ubicación (`@JsonIgnore` y test) y Hernando (guarda del publicador).

### SEC-J-14 · Bajo · A05 (pruebas de carga: credenciales en la línea de comandos y eventos incompatibles con ARQ-13)
- **Archivos:** `pruebas-carga/correr-k6.sh:11-12,20` (`-e RMQ_PASS=<contraseña>` como argumento de `docker run`/`k6`), `pruebas-carga/datos/limpiar.sh:12` (`curl -u "$RABBITMQ_USER:$RABBITMQ_PASSWORD"`), `k6/registro-hallazgo.js:5` (`-e TOKEN=<JWT>`), `correr-k6.sh:20` (`grafana/k6:latest` sin versión fija), `k6/carga-cientifica-simultanea.js:91,104` (`version` hasta ~2·10⁷ y sin `user_id`).
- **Problema:**
  - **No hay secretos ni credenciales reales versionados**: todo sale del `.env` en tiempo de ejecución, `resultados/*.json` solo tiene métricas, la semilla es sintética y sin coordenadas, NGINX y las réplicas solo escuchan en `127.0.0.1`/la red de Docker y el compose es efímero (D30).
  - En cambio, la contraseña del usuario **administrador** de RabbitMQ y el JWT quedan visibles con `ps` para cualquier usuario local mientras corre la prueba (el mismo patrón que SEC-10).
  - `latest` puede cambiar de imagen sin aviso.
  - Los `hallazgo.*` de la carga científica no llevan `user_id` y su `version` supera `VERSION_MAXIMA`. Con ARQ-13 todos se rechazan hacia la DLQ, de modo que ASR4 deja de medir lo que dice medir y es tentador "arreglarlo" apagando la verificación.
- **Corrección:**
  ```bash
  # correr-k6.sh: pasar por entorno, no por argumento
  export RMQ_USER RMQ_PASS
  exec docker run --rm -e RMQ_USER -e RMQ_PASS ... grafana/k6:1.3.0 run ...
  # limpiar.sh
  curl -s -o /dev/null --netrc-file <(printf 'machine 127.0.0.1 login %s password %s\n' "$RABBITMQ_USER" "$RABBITMQ_PASSWORD") -X DELETE ...
  ```
  En `carga-cientifica-simultanea.js`: `properties: { ..., user_id: __ENV.RMQ_USER }`, `version` acotada (por ejemplo, `2 + (i % 900000)`), y en el compose de carga `BIOHUB_EVENTOS_PUBLICADOR_CIENTIFICO: ${RABBITMQ_USER}` (sin desactivar la verificación).
- **Esfuerzo:** S · **Responsable sugerido:** consulta-pública (pruebas de carga).

### Informativos
- **SEC-J-I7 (CI):** `.github/workflows/ci.yml:93` usa una llave AES **fija y pública** (`CI_UBICACION_CLAVE`, texto ASCII legible en base64) desde el commit `5b168b2`. Es ficticia y solo vive en el job, pero D38 dice "las llaves de cifrado de CI se generan en el job". Recomendación: `CI_UBICACION_CLAVE=$(openssl rand -base64 32)` en un paso previo (con `echo "CI_UBICACION_CLAVE=..." >> "$GITHUB_ENV"`), y no reutilizar nunca ese valor fuera de CI. Los cambios de esta iteración en CI y Bruno (`user_id` = `rabbitUser`; `rabbitPassword`/`passwordUsuarios` como `vars:secret`, `bruno/environments/*.bru:14-16`) no introducen secretos.
- **SEC-J-I8 (degradación automática, ARQ-06):** con unas 100 peticiones concurrentes cualquier anónimo puede forzar el modo degradado (`MonitorCarga.java:78-83`). Es el comportamiento buscado, solo reduce filtros, la clave de caché distingue el modo (`CriterioBusquedaPublica.java:65-66`) y el filtro no toca la respuesta. Su acotación depende del rate limit por IP del gateway (SEC-J-05, Santiago). `/actuator/info` muestra `enCurso`/`p95Ms` a cualquier usuario autenticado. No es sensible.
- **SEC-J-I9 (volumen de auditoría):** `DecoradorAuditoriaAcceso` escribe una línea por hallazgo descifrado, hasta 1.000 por petición de mapa (`MapaService.java:21`). Es correcto para D28, pero conviene incluirlo al dimensionar el appender dedicado de SEC-J-08 (3) para no perder líneas por rotación.

---

## Bien hecho (esta iteración)
- **Decorator bien armado:**
  - Un único bean `RepositorioUbicaciones` con el orden auditoría → cifrado → JPA, verificado por test.
  - El componente JPA **se niega** a guardar coordenadas en claro.
  - `UbicacionHallazgoRepository` extiende `Repository` (sin `delete*`/`findAll`) y ArchUnit impide usarlo fuera de `infrastructure`/`domain`.
- **Strategy con falla segura:** cualquier caso que no sea alto rango recibe `VisibilidadGeneralizada`, que ni siquiera tiene acceso al descifrado. La exacta conserva la segunda barrera en `ServicioDescifrado`.
- **Keystore:**
  - Falla cerrado ante cualquier problema, sin fallback a otro proveedor ni a la llave efímera.
  - Los mensajes no incluyen ruta, causa ni contraseña.
  - Exige alias `vN` y AES de 32 bytes.
  - `toString()` enmascarado en el proveedor y en `PropiedadesCifrado`.
  - `*.p12`/`*.jks`/`*.keystore` en `.gitignore:34-37`.
  - El README indica `chmod 700/600` y que `keytool` pida la contraseña de forma interactiva (no queda en el historial).
- **Observer:** síncrono, en la transacción de registro y con `MANDATORY`. Un fallo de cifrado revierte todo el registro (sin hallazgos con coordenadas sin guardar ni guardadas en claro). `HallazgoRegistrado.toString()` enmascara las coordenadas y el evento de reclasificación no las lleva.
- **SEC-J-02/03/06/08 corregidos con tests** (`QaBarridoCoordenadas*`, `QaCifradoYReposoTest`, `QaSeguridadJwtTest`, `RecifradoApiTest`).
- **Dependencias:** ArchUnit 1.4.1 con versión fija y solo en test.
- **CI y Bruno:** la verificación AMQP **sigue activa** en CI (solo cambia el usuario esperado, `ci.yml:168-171`). Bruno pone `user_id` y las contraseñas son `vars:secret`.

## Veredicto (iteración 15h)
**APROBADO CON OBSERVACIONES.** No hay Críticos ni Altos. La refactorización conserva los controles de SEC-J-01 a SEC-J-08. Condiciones:
1. **Antes de que registro (Hernando) integre su código en `servicio-cientifico`:** SEC-J-10 (rol dentro de la cadena y reglas ArchUnit). Si entra código de registro que use `RepositorioUbicaciones` o `CifradorCoordenadas` sin estas reglas, SEC-J-10 pasa a **Alto**.
2. **Antes de publicar `hallazgo.*` reales:** SEC-J-11 (plantilla de la raíz en `true` y `setUserId` en el publicador) y SEC-J-13 (`@JsonIgnore` y guarda en el publicador). Si se publica con la verificación apagada, SEC-J-11 pasa a **Alto**.
3. SEC-J-12, SEC-J-14 y los pendientes parciales (SEC-J-04, 05, 07, 08.3 y 09) en la siguiente iteración de cada responsable.

---

## Re-verificación ciclo 1 (2026-11-04)

**Revisor:** ciberseguridad · **Rama:** `feature/ubicacion-consulta-publica` (HEAD `8cab133`) más el working tree sin commit · **Alcance:** solo las correcciones de SEC-J-10, SEC-J-11 y SEC-J-14 (y ARQ-26/ARQ-27, D43, D44, contrato §7.1) y posibles regresiones. No se repite la auditoría 15h.

### Cómo se verificó
- **Lectura:** D43 y D44 (`03-arquitectura.md:94-95`), contrato §7.1 (`04-contrato-api.md:502-509`), `git diff` de `PublicadorEventos`, sus dos tests, `.env.example` (raíz y consulta-pública) y `ci.yml`. Lectura completa de los cuatro eslabones de la cadena, `ConfiguracionUbicacion`, `ServicioDescifrado`, `ArquitecturaUbicacionTest` y todo `pruebas-carga/` (scripts, compose, k6, `resultados/`).
- **Pruebas:** `mvn -o test -Dtest='Arquitectura*,Publicador*,Decorador*'` en servicio-cientifico. Resultado: `ArquitecturaUbicacionTest` 11/11, `PublicadorEventosTest` 7/7, `PublicadorEventosIntegracionTest` 2/2 (RabbitMQ real: el broker acepta el `user_id`), `DecoradoresRepositorioUbicacionesTest` 11/11 y `DecoradorAuditoriaIntegracionTest` 2/2. Sin fallos.
- **Prueba de evasión de las reglas ArchUnit (solo en el scratchpad, fuera del repositorio):**
  - Se compilaron seis clases "intrusas" en `co.edu.unbosque.biohub.cientifico.registro..` contra `target/classes` y se ejecutaron sobre ellas las 11 reglas reales de `ArquitecturaUbicacionTest`.
  - **Solo una se detectó**: la llamada directa a `obtenerUbicacionExacta`. Las otras cinco pasaron el build (ver SEC-J-15 y SEC-J-16).
  - Después se probaron las reglas corregidas que se proponen abajo: detectan los seis casos y **no** dan falsos positivos sobre el código real.
- **Secretos:** revisión de las líneas añadidas en el `git diff` y de los archivos nuevos de `pruebas-carga/`. No hay credenciales nuevas versionadas: `resultados/*.json` solo tiene métricas, y la contraseña de `PublicadorNoPermitidoIntegracionTest` es de un contenedor efímero de Testcontainers.

### Estado por ID

| ID | Estado | Evidencia |
|---|---|---|
| **SEC-J-11** (ARQ-26) | **Cerrado** (riesgo residual en SEC-04 / SEC-D-03 punto 1, que es de Santiago) | `PublicadorEventos.java:42,50-57`: `user_id` sale de `rabbitTemplate.getConnectionFactory().getUsername()` y falla al construir si no se puede determinar. `:74`: `setUserId(usuarioAmqp)` en cada mensaje. Tests: `PublicadorEventosTest.java:85` (fija el `user_id`), `:98` (no es constante) y `:116` (falla cerrado). `PublicadorEventosIntegracionTest.java:90`: el broker real lo acepta y lo entrega como `receivedUserId`. `.env.example:74` (raíz) `BIOHUB_EVENTOS_VERIFICAR_PUBLICADOR=true`. `:78` `BIOHUB_EVENTOS_PUBLICADOR_CIENTIFICO=biohub`, igual al `RABBITMQ_USER` del Compose. Consumidor: `application.yml:92-93` y `ConsumidorEventos.java:43-44,125-126`, ambos con `true` por defecto. CI conserva la verificación (`ci.yml:168-171`). **Residual:** mientras todos los servicios compartan el usuario administrador `biohub` (SEC-04), el `user_id` prueba que "quien publica tiene las credenciales del broker", no que sea servicio-cientifico. Ver también SEC-J-I10 |
| **SEC-J-10** (ARQ-27, D43, D44) | **Parcial**: los puntos 2 y 3 y la auditoría de cualquier excepción están cerrados; las reglas ArchUnit se pueden esquivar (SEC-J-15 y SEC-J-16, Bajos) | **Cerrado:** (3) eslabones *package-private*: `DecoradorAuditoriaAcceso.java:17,23`, `DecoradorCifradoCoordenadas.java:19,24`, `DecoradorRepositorioUbicaciones.java:18,22` (constructor `protected` en una clase privada del paquete) y `RepositorioUbicacionesJpa.java:25,38`. Solo los construye `ConfiguracionUbicacion.java:86-89`. (Auditoría) `DecoradorAuditoriaAcceso.java:34-38` audita toda `RuntimeException` solo con usuario y `hallazgoId`, sin excepción ni valores. Lo prueba `DecoradoresRepositorioUbicacionesTest.java:164`. (2) Reglas sobre todo `cientifico`: `ArquitecturaUbicacionTest.java:31-33` y `:51-128`. (1) Lo sustituye D44 (ver la evaluación abajo). **Pendiente:** `ArquitecturaUbicacionTest.java:70-71,79-80` (exclusiones por patrón de paquete y por nombre simple), `:81` (`callMethodWhere` no ve las referencias a método) y falta una regla para `ProveedorDeLlaves`/`PropiedadesCifrado` |
| **SEC-J-14** | **Cerrado** (queda un comentario desactualizado, ver SEC-J-I11) | `correr-k6.sh:14`: `grafana/k6:2.3.0` fija, presente en local con digest `sha256:2dd4756e…`. `:17-22,30,37-38`: `RMQ_USER`/`RMQ_PASS`/`TOKEN` se pasan con `-e NOMBRE`, sin valor en `argv`. `limpiar.sh:13,16`: `MYSQL_PWD`/`REDISCLI_AUTH` por entorno. `:18-23`: credenciales de curl por stdin (`-K -`), escapando `\` y `"`. `sembrar.sh:7`: `MYSQL_PWD` por entorno. `compose-carga.yml:31-32` mantiene la verificación **activa** con el publicador esperado igual a `RABBITMQ_USER`, y `:35` usa una cola propia de carga. `carga-cientifica-simultanea.js:123`: `user_id: __ENV.RMQ_USER`. `:40,105,110`: `version` acotada a `VERSION_MAXIMA = 1000000` |
| SEC-J-12 | **Abierto** (fuera de este ciclo) → **Iteración 16: Cerrado** | `ConfiguracionUbicacion.java:40-41` sigue sin borrar el `char[]`. Sigue sin comprobarse el permiso del `.p12` |
| SEC-J-13 | **Abierto** (fuera de este ciclo) → **Iteración 16: Parcial** (`@JsonIgnore` y guarda hechos; guarda eludible, SEC-J-17) | `HallazgoRegistrado.java:37-38` sin `@JsonIgnore`. `PublicadorEventos.publicar` (`:64`) sigue sin guarda de coordenadas. Ahora es **más urgente**: con `user_id` correcto, un `hallazgo.*` con coordenadas que salga de cientifico ya no se rechaza por publicador (consulta-pública lo descarta por SEC-J-03, pero reportes no) |
| SEC-J-04, 05, 07, 08 (3), 09 | Sin cambios (en la iteración 16, SEC-J-04 y SEC-J-08 (3) pasan a Parcial; ver esa sección) | Ver la tabla de la iteración 15h |

### Evaluación de D44 (decisión tomada; opinión de ciberseguridad)
- **La garantía es suficiente para la amenaza que motivó SEC-J-10.** Esa amenaza es un **error de un desarrollador** del mismo servicio, por ejemplo registro reutilizando el repositorio. En ese caso una regla de build que falla es igual de efectiva que una comprobación en tiempo de ejecución, y además avisa antes.
- **Ninguna de las dos opciones protege contra código malicioso dentro de la JVM.** Pasar `Authentication` a la cadena tampoco lo detendría: ese código puede fabricar una `Authentication`, leer las llaves desde `Environment` o usar reflexión, y la prueba con `setAccessible` sobre `DecoradorCifradoCoordenadas` no la detecta ninguna regla. Esa amenaza se cubre con la revisión de código y con D35/D38, no con este control.
- **Condición:** la equivalencia solo se cumple si las reglas no se pueden esquivar por accidente. Hoy sí se puede (SEC-J-15 y SEC-J-16). La forma más probable es una referencia a método `repositorio::obtenerUbicacionExacta`, que es un patrón habitual al mapear listas.
- **Diferencia que queda:** `servicioDescifradoExigeElRol` (`:86-92`) solo comprueba que **exista** una llamada a `exigirCoordenadas`, no que vaya **antes** de descifrar. El orden lo cubre `ServicioDescifradoTest.java:20` (`verifyNoInteractions(repositorio)`). Es aceptable si ese test se mantiene.
- **Conclusión:** D44 es aceptable con SEC-J-15 y SEC-J-16 corregidos **antes de que registro integre código** en servicio-cientifico.

### Hallazgos nuevos

#### SEC-J-15 · Bajo · A01 / A04 (las reglas ArchUnit de D43/D44 se esquivan con nombres homónimos, con un subpaquete `ubicacion.infrastructure` o con referencias a método)
- **Archivos:** `servicio-cientifico/src/test/java/co/edu/unbosque/biohub/cientifico/ubicacion/ArquitecturaUbicacionTest.java`, con estas líneas:
  - `:70`: `resideOutsideOfPackage("..ubicacion.infrastructure..")`.
  - `:71`: `doNotHaveSimpleName("RecifradoService")`.
  - `:79`: el mismo patrón de paquete.
  - `:80`: `doNotHaveSimpleName("ServicioDescifrado")`.
  - `:81`: `callMethodWhere(...)`.
  - `:54` y `:62`: mismo patrón con `..ubicacion..`.
  - `:125-126`: exclusiones por nombre simple.
- **Problema (comprobado, no teórico):** estas tres clases compilan, se inyectan y **pasan las 11 reglas**:
  1. `co.edu.unbosque.biohub.cientifico.registro.ubicacion.infrastructure.Fuga`: el patrón `..ubicacion.infrastructure..` coincide con **cualquier** paquete que contenga ese tramo. La clase inyecta `CifradorCoordenadas` y llama a `descifrar` y a `obtenerUbicacionExacta`.
  2. `co.edu.unbosque.biohub.cientifico.registro.RecifradoService`: la excepción va por nombre simple, no por nombre completo. Inyecta `CifradorCoordenadas` y descifra.
  3. `registro.FugaReferencia`: devuelve `repositorio::obtenerUbicacionExacta`. ArchUnit modela las referencias a método como `JavaMethodReference`, no como `JavaMethodCall`, y `callMethodWhere` no las ve.

  En los casos 1 y 2 se descifra **sin rol y sin auditoría**. En el 3, sin rol y con auditoría a nombre de quien diga el llamador.
- **Riesgo:** bajo. Hace falta código nuevo dentro del servicio y los casos 1 y 2 son poco probables por accidente. El caso 3 sí es un patrón común. Basta uno para que la garantía de D44 sea falsa sin que CI lo note.
- **Corrección** (probada en el scratchpad: detecta los tres casos y pasa sobre el código actual):
  ```java
  private static final String INFRA = "co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure";   // exacto, sin ".."

  @Test void soloServicioDescifradoPideCoordenadasExactas() {
      noClasses().that().resideOutsideOfPackage(INFRA)
          .and().doNotBelongToAnyOf(ServicioDescifrado.class)                 // por clase, no por nombre simple
          .should().accessTargetWhere(target(name("obtenerUbicacionExacta"))  // JavaAccess.Predicates.target: llamadas Y referencias
              .and(target(owner(assignableTo(RepositorioUbicaciones.class)))))
          .check(CLASES);
  }
  ```
  Aplicar lo mismo en las demás reglas:
  - `doNotBelongToAnyOf(RecifradoService.class)` o `doNotHaveFullyQualifiedName(...)` en lugar de `doNotHaveSimpleName(...)`.
  - Paquete exacto (`INFRA`) en lugar de `..ubicacion.infrastructure..`.
  - En `:54` y `:62`, `"co.edu.unbosque.biohub.cientifico.ubicacion.."` en lugar de `"..ubicacion.."`.
- **Responsable sugerido:** ubicación (Juanita). **Esfuerzo:** S.

#### SEC-J-16 · Bajo · A01 / A02 (las llaves AES crudas no tienen regla: `ProveedorDeLlaves` y `PropiedadesCifrado` se inyectan desde cualquier módulo)
- **Archivos:** `servicio-cientifico/src/main/java/co/edu/unbosque/biohub/cientifico/ubicacion/domain/ProveedorDeLlaves.java:9,13`: interfaz pública con `Optional<SecretKey> llave(String version)`, expuesta como bean en `ConfiguracionUbicacion.java:32-33`. `.../infrastructure/PropiedadesCifrado.java:17`: `record` público con `claves()` en texto, registrado por `@EnableConfigurationProperties`. En `ArquitecturaUbicacionTest.java` no hay ninguna regla sobre ninguno de los dos.
- **Problema:** D43 cerró el acceso a `CifradorCoordenadas`, pero cualquier bean de `registro` (u otro módulo) puede inyectar `ProveedorDeLlaves`, obtener la `SecretKey` activa y descifrar con AES-GCM (AAD = UUID del hallazgo, que es público). No pasa ni por el rol ni por la auditoría. Se comprobó con la clase `registro.FugaLlaves`, que pasa todas las reglas. Es un camino **más grave** que el de `CifradorCoordenadas`, porque expone la llave y no solo un dato.
- **Riesgo:** bajo. Exige código nuevo y es poco probable por accidente, pero es exactamente el hueco que D43 quiere cerrar.
- **Corrección** (probada: detecta el caso y pasa sobre el código actual; `RecifradoService.java:41,49,67` es el único usuario legítimo fuera de `infrastructure`):
  ```java
  @Test void llavesYCifradorSoloEnInfraestructuraYRecifrado() {
      noClasses().that().resideOutsideOfPackage(INFRA)
          .and().doNotBelongToAnyOf(RecifradoService.class, CifradorCoordenadas.class, ProveedorDeLlaves.class)
          .should().dependOnClassesThat().areAssignableTo(CifradorCoordenadas.class)
          .orShould().dependOnClassesThat().areAssignableTo(ProveedorDeLlaves.class)
          .orShould().dependOnClassesThat().areAssignableTo(PropiedadesCifrado.class)
          .check(CLASES);
  }
  ```
  Si `RecifradoService` solo necesita `versionActiva()`/`versiones()`, conviene separar en el puerto una vista sin `llave(...)` (por ejemplo, `VersionesDeLlave`) y dejar `ProveedorDeLlaves` solo para `infrastructure`.
- **Responsable sugerido:** ubicación (Juanita). **Esfuerzo:** S.

#### Informativos
- **SEC-J-I10 (plantillas incoherentes):** `servicio-consulta-publica/.env.example:72` tiene `BIOHUB_EVENTOS_PUBLICADOR_CIENTIFICO=cientifico_mq`, mientras que la raíz (`.env.example:78`) tiene `biohub`. Como `application.yml:7` importa `./.env` después de `../.env`, quien copie también la plantilla del servicio verá que **todos** los `hallazgo.*` del Compose local van a la DLQ. Eso falla cerrado, que es seguro, pero es el mismo incentivo a "arreglarlo" con `VERIFICAR_PUBLICADOR=false` que SEC-J-11 quería evitar. El comentario de `:69-70` lo explica. Recomendación: en esa plantilla, dejar la línea comentada o con `biohub` y una nota que diga "cientifico_mq cuando exista SEC-04".
- **SEC-J-I11 (comentario desactualizado):** `pruebas-carga/k6/registro-hallazgo.js:5` todavía documenta `./correr-k6.sh ... -e TOKEN=<JWT>`, que deja el JWT en `argv`. Contradice `correr-k6.sh:9` y el README (`:74-75`). Cambiar por `TOKEN=<jwt> ./correr-k6.sh k6/registro-hallazgo.js -e BASE_URL=...`.
- **SEC-J-I12 (scripts de carga):**
  - `limpiar.sh:10` y `sembrar.sh:5` hacen `set -a; . ./.env`, así que el `.env` se **ejecuta** como shell. Es el archivo propio del desarrollador y no es explotable, pero un valor con `$(...)` o con espacios sin comillas se interpreta. `correr-k6.sh:18` ya usa un lector sin evaluar (`valor_env`); conviene reutilizarlo.
  - `docker run -e RMQ_PASS` deja la contraseña en la configuración del contenedor (`docker inspect`) mientras corre. Solo la ve quien ya tiene acceso a Docker (equivale a root), y el contenedor es `--rm`. Aceptable.

### Regresiones
- **No se encontraron regresiones de seguridad.**
- `EventosPruebaController.java:33,38` también publica con el `user_id` del servicio, pero está restringido a `administrador` y su tipo es fijo (`prueba.ping`), así que no puede publicar `hallazgo.*`.
- `PublicadorEventos` ya no puede arrancar sin usuario AMQP (falla cerrado).
- Las reglas ArchUnit nuevas no quitaron ninguna de las anteriores.
- No hay credenciales nuevas en archivos versionados.

### Veredicto (re-verificación ciclo 1)
**APROBADO CON OBSERVACIONES.** No hay Críticos ni Altos.
- **SEC-J-11** y **SEC-J-14** quedan **cerrados**.
- **SEC-J-10** queda **parcial**, con un riesgo residual que baja de Medio a **Bajo** (SEC-J-15 y SEC-J-16).
- D44 es aceptable con esta condición: **SEC-J-15 y SEC-J-16 deben estar corregidos antes de que registro (Hernando) integre código en servicio-cientifico**. Si entra código de registro sin esas reglas, SEC-J-10 vuelve a **Medio**.
- **SEC-J-13** pasa a ser **condición previa a publicar `hallazgo.*` reales**, porque con el `user_id` correcto el publicador ya no actúa como filtro.

---

# Iteración 16 – re-auditoría (2026-11-04)

**Revisor:** ciberseguridad · **Rama:** `feature/ubicacion-consulta-publica` (HEAD `aeb67d8`) · **Alcance:** `git diff 9c8982b..HEAD` (commits `81f4e28` a `aeb67d8`): SEC-J-13 (D54), SEC-J-12, SEC-J-08.3, SEC-J-04, el refactor ARQ-28, la regla ARQ-29 (D53) y los secretos del diff. No se repiten las auditorías anteriores.

## Resumen

| Severidad | Cantidad (nuevos) |
|---|---|
| Crítico | 0 |
| Alto | 0 |
| Medio | 0 |
| Bajo | 2 (SEC-J-17, SEC-J-18) |
| Informativo | 3 (SEC-J-I13 a SEC-J-I15) |

**Veredicto: APROBADO CON OBSERVACIONES.** No hay Críticos ni Altos. Las cuatro correcciones están bien hechas y probadas. El refactor ARQ-28 y la regla ARQ-29 **no** rompen D44: cada descifrado y cada 403 siguen auditados. No se versionó ningún secreto. Quedan dos Bajos:
- **SEC-J-17:** la guarda del publicador se puede eludir con valores numéricos, objetos o arrays.
- **SEC-J-18:** la auditoría D28 todavía falla abierta si se baja el nivel del logger o si el archivo no se puede escribir.

## Cómo se revisó
- **Documentación:** D44, D53 y D54 (`03-arquitectura.md`, Decisiones), contrato §6 (nota D53) y §7.3, plan de la iteración 16 (`00-plan.md:124-135`) y las fichas SEC-J-04, 08, 12 y 13 de este reporte.
- **Código (estático):** el diff completo, más una lectura completa de:
  - eventos: `DetectorCoordenadas` (cientifico y consulta-pública), `PublicadorEventos`, `HallazgoRegistrado`, `RabbitConfig` (`Jackson2JsonMessageConverter`);
  - ubicación: `ProveedorDeLlavesKeyStore`, `ConfiguracionUbicacion`, `AuditoriaAccesoUbicacion` (puerto), `AuditoriaAccesoUbicacionLog`, `DecoradorAuditoriaAcceso`;
  - configuración, pruebas y web: `logback-spring.xml`, `Dockerfile`, `ArquitecturaUbicacionTest`, `ModuloRegistroNoDependeDeUbicacionInternaTest`, `UmbralesDegradacion`/`MonitorCarga` y `MapaHallazgos.jsx`.
- **Prueba del detector (solo en el scratchpad, fuera del repositorio):** se copió `eventos/service/DetectorCoordenadas.java` sin cambios y se le pasaron 33 casos: 21 de evasión y 12 de posibles falsos positivos. Resultados en SEC-J-17. Los patrones son **idénticos** a los del consumidor de consulta-pública (comparados con `diff`). La única diferencia es `CLAVE_JSON_PROHIBIDA` del análisis en bruto, que el publicador no necesita.
- **Pruebas:** sobre una copia de `HEAD` (`git archive`) en el scratchpad, para no chocar con el build de QA, se ejecutó `mvn -o -pl servicio-cientifico -am test` con `Arquitectura*`, `ModuloRegistro*`, `Publicador*Test`, `DetectorCoordenadas*`, `HallazgoRegistrado*`, `ProveedorDeLlavesKeyStore*`, `AuditoriaLogback*`, `AuditoriaAccesoUbicacionLog*`, `Decorador*` y `AuditoriaYSinFiltraciones*`. Resultado: **75 tests, 0 fallos, 2 omitidos**, los que necesitan un broker desplegado. `PublicadorEventosIntegracionTest` corrió con un RabbitMQ real (Testcontainers).
- **Secretos:**
  - Se buscaron patrones de contraseña, token, llave y base64 largo en las líneas añadidas del diff: solo aparecen nombres de variables y texto de documentación, ningún valor.
  - `git ls-files` no contiene `.env`, `*.p12`, `*.jks`, `*.log` ni `logs/`.
  - `servicio-cientifico/logs/` existe en local y Git lo ignora (`.gitignore:5`, `!!` en `git status --ignored`).
  - No se abrió ningún `.env`.

## Estado de los hallazgos

| ID | Estado | Evidencia |
|---|---|---|
| **SEC-J-13** (D54) | **Parcial**: punto 1 cerrado; punto 2 implementado pero eludible (SEC-J-17) | (1) `HallazgoRegistrado.java:42-43`: `@JsonIgnore` en `latitud`/`longitud`, probado por `HallazgoRegistradoSerializacionTest` (4/4, incluye `convertValue(evento, Map.class)`). `toString()` sigue enmascarado (`:47-49`). (2) `PublicadorEventos.java:70-73`: rechaza el `hallazgo.*` **antes** de construir el sobre, con un mensaje que no incluye ni la clave ni el valor. `DetectorCoordenadas.java:74-99` recorre mapas y colecciones a cualquier profundidad, revisa claves (sin distinguir mayúsculas) y texto, y trata como sospechosa una estructura de más de 64 niveles. Detecta pares decimales con coma o punto, hemisferio, DMS con símbolos, `lat:`/`lng=` y GeoJSON anidado. **No** inspecciona `Number`, records/POJO ni arrays, y le faltan variantes de clave como `decimalLatitude` (ver SEC-J-17) |
| **SEC-J-12** | **Cerrado** (residual aceptado: SEC-J-I2) | `ConfiguracionUbicacion.java:41-49`: el `char[]` se borra en `finally`, también cuando falla. `ProveedorDeLlavesKeyStore.java:48,64-81`: falla cerrado ante **cualquier** bit de grupo u otros, no solo lectura. Si no puede leer los permisos (`IOException`) tampoco arranca. Los mensajes no incluyen la ruta y no hay `cause` (`ProveedorDeLlavesKeyStoreTest`: 644, 640, 604 y 660 rechazados con `hasMessageNotContaining(ruta)` y `hasNoCause`; 600 aceptado; `ConfiguracionUbicacion` propaga el rechazo). `.env.example:71-72` y el README recomiendan pasar la contraseña por otro canal. Ver la nota sobre el `String` más abajo |
| **SEC-J-08** | **Parcial**: puntos 1 y 2 cerrados (15h); punto 3 casi cerrado, queda el fallo abierto (SEC-J-18) | `logback-spring.xml:24-41`: `RollingFileAppender` propio con `append=true`, rotación diaria y por tamaño (50 MB), retención de 365 días con tope de 10 GB, nivel `INFO` fijo y `additivity=true` (también sale por consola). `Dockerfile:11`: `/app/logs` pertenece a `app:app` (UID 10001, no root). `AuditoriaLogbackTest` (3/3) comprueba que la línea llega al archivo y no al log general. **Pendiente:** `logging.level.biohub.auditoria.ubicacion` (o `LOGGING_LEVEL_BIOHUB_AUDITORIA_UBICACION`) sigue apagándola en silencio. Si `logs/` no se puede escribir, Logback solo avisa y la auditoría sale solo por consola. No hay ningún compose de despliegue de servicio-cientifico en el repositorio que monte un volumen en `/app/logs`, así que, tal como está, el archivo se pierde al recrear el contenedor |
| **SEC-J-04** | **Parcial**: la parte de web-interna está cerrada; el proxy de teselas (Santiago) sigue abierto | `MapaHallazgos.jsx:12-13,30`: `fitBounds` con `maxZoom` 8 (un punto) o 10 (varios). `:48`: `referrerPolicy="no-referrer"` en `TileLayer`, opción que Leaflet 1.9.4 admite (`node_modules/leaflet` 1.9.4). Probado en `PaginaMapa.test.jsx:92-101`. No hay otros `setView`/`flyTo`/`panTo` en `web-interna/src`. Riesgo residual: OSMF sigue viendo la IP, la hora y teselas de unos 150 km alrededor del punto. El usuario puede acercar a mano hasta 12 (R6, aceptado). Ver SEC-J-I14 |

**¿Es aceptable que la contraseña del keystore siga como `String` (SEC-J-I2)?** Sí, como riesgo residual. El valor ya está en el entorno del proceso (`/proc/<pid>/environ`, legible por el mismo UID y por root) y en el `Environment` de Spring. Quien puede volcar el heap o leer el entorno del proceso también puede leer el `.p12`, porque son el mismo usuario. Borrar el `char[]` reduce las copias, pero no cambia esa frontera. Condiciones que siguen vigentes:
- actuator solo con `health,info` (`application.yml:58-59`, sin cambios);
- sin `-XX:+HeapDumpOnOutOfMemoryError` hacia un disco compartido;
- la contraseña fuera del `.env` de la ruta.

Si en el futuro se quiere quitar también el `String`, la opción es leer la contraseña de un archivo de secreto (`UBICACION_KEYSTORE_PASSWORD_FILE`) directamente a `char[]` y no enlazarla en `PropiedadesCifrado`.

### Verificación de ARQ-28, ARQ-29 y D44

**Auditoría como puerto** (`ubicacion/domain/AuditoriaAccesoUbicacion.java:8-19`, implementado por `infrastructure/AuditoriaAccesoUbicacionLog.java:20-41`):
- Hay una sola implementación en `src/main`.
- El cuerpo de `registrar` y `limpiar` es igual al de antes: `endpoint`, `usuario` y `filtros` se sanean y se truncan.
- La cadena sigue siendo auditoría → cifrado → JPA (`ConfiguracionUbicacion.java:91-97`).
- `DecoradorAuditoriaAcceso.java:29-39` audita el éxito y **cualquier** `RuntimeException`.
- El manejador de 403 solo cambió el import.
- `DecoradorAuditoriaIntegracionTest` (2/2) y `AuditoriaYSinFiltracionesTest` (5/5) pasan.
- Si `registrar` lanzara, el decorador no devuelve las coordenadas: falla cerrado en el camino de descifrado.

**`EstadoConservacion` movido a `domain`:** es un enum sin lógica de seguridad. Lo cambiado fueron imports.

**Reglas de D44:** se conservan las seis, sin relajar ninguna:
- `cifradorSoloEnInfraestructuraYRecifrado`;
- `soloServicioDescifradoPideCoordenadasExactas`, con `accessTargetWhere` y nombre completo;
- `llavesSoloEnInfraestructuraYRecifrado`;
- `servicioDescifradoExigeElRol`;
- eslabones *package-private*;
- `soloLaConfiguracionArmaLaCadena`.

Las tres reglas nuevas de ARQ-28 (`layeredArchitecture`, `domain` sin dependencias externas, `infrastructure` sin depender de `service`) solo añaden restricciones.

**ARQ-29 (D53):** `ModuloRegistroNoDependeDeUbicacionInternaTest` ahora prohíbe **todo** `co.edu.unbosque.biohub.cientifico.ubicacion` en `registro`, tanto en las fuentes como en el bytecode (con `allowEmptyShould`, porque hoy `registro` solo tiene `package-info`). Es **más estricta** que antes y refuerza D44: registro ya no puede ni siquiera nombrar `RepositorioUbicaciones` ni `ProveedorDeLlaves`.

**consulta-pública:** `UmbralesDegradacion` conserva todas las validaciones del record anterior (`:42-57`). `MonitorCarga` lee `biohub.publica.modo-degradado` con la misma clave. No cambia nada de seguridad.

**Conclusión:** D44 y la garantía "cada descifrado y cada 403 quedan auditados" siguen en pie **en el código**. El hueco que queda es operativo: el nivel del logger y el destino del archivo (SEC-J-18).

## Hallazgos nuevos

### SEC-J-17 · Bajo · A04 (la guarda de coordenadas del publicador se elude con números, objetos o arrays)
**`DetectorCoordenadas` solo recorre `Map`, `Collection` y `String`. Cualquier otro valor se considera limpio, aunque `Jackson2JsonMessageConverter` lo serialice al broker.**

- **Archivos:**
  - `servicio-cientifico/src/main/java/co/edu/unbosque/biohub/cientifico/eventos/service/DetectorCoordenadas.java:78-98`: `contiene(...)` devuelve `false` para `Double`, `BigDecimal`, records/POJO/entidades y `Object[]`/`double[]`.
  - `:28-29`: lista de claves cerrada, sin `decimalLatitude`, `decimalLongitude`, `verbatimCoordinates`, `verbatimLatitude`/`verbatimLongitude` (Darwin Core, el estándar habitual en biodiversidad), ni `lat_dec`, `x`/`y`, `punto`, `location`.
  - `PublicadorEventos.java:70`: la guarda solo se aplica si `tipo.startsWith("hallazgo.")`, que distingue mayúsculas.
  - `config/RabbitConfig.java:30-31`: `Jackson2JsonMessageConverter` serializa cualquier objeto que haya en `datos`.
- **Problema (comprobado en el scratchpad):** estas cargas **pasan** la guarda y llegarían a `biohub.eventos`:

  | Carga `datos` | Resultado |
  |---|---|
  | `{"punto": [4.6097, -74.0817]}` (Double) | pasa |
  | `{"x": -74.0817, "y": 4.6097}` | pasa |
  | `{"decimalLatitude": 4.6097, "decimalLongitude": -74.0817}` | pasa |
  | `{"decimalLatitude": "4.6097"}` (un solo valor como texto) | pasa |
  | `{"ubicacion": <record con latitud/longitud>}` | pasa (Jackson lo serializa con `latitud`/`longitud`) |
  | `{"p": new Object[]{"4.6097, -74.0817"}}` | pasa |
  | `"4.6097° -74.0817°"` (grados sin hemisferio) | pasa |
  | `"POINT(-74.081 4.609)"`, `"-74.081, 4.609"` (orden lon,lat, 3 decimales ≈ 110 m) | pasa |
  | `"4.6097 n, 74.0817 w"` (hemisferio en minúscula) | pasa |
  | `"4 36 35 N 74 04 54 W"` (DMS sin símbolos) | pasa |

  Lo que más probablemente se escape por error es poner una entidad u objeto en `datos` (`Map.of("hallazgo", hallazgo)`) o usar nombres Darwin Core. El consumidor de consulta-pública lo detecta en parte, porque parsea el JSON a mapas y ve `latitud` en el objeto anidado. Pero **no** detecta números bajo claves neutras, y `reportes.eventos` no tiene ninguna barrera. Además, el mensaje ya existe en el broker.

  **Falsos positivos** (iguales en el consumidor, porque los patrones son los mismos), que rompen eventos legítimos:
  - `"segmento lat. 3 con setas"` (abreviatura de *lateral*) y `"antenas long 2 mm"` (`CLAVE_Y_NUMERO`);
  - `"pendiente 5° 30'"` (`GRADOS_MINUTOS`);
  - `"25° N"`;
  - `"peso 1.2345 2.3456 g"`;
  - `"entre 1.200 -3.400 msnm"`.

  No son frecuentes. Con la guarda en `PublicadorEventos`, un falso positivo hace fallar `publicar`. Si registro publica en un `@TransactionalEventListener(AFTER_COMMIT)`, el hallazgo ya quedó guardado y la ficha pública no se crea. Esa inconsistencia es silenciosa: el publicador no registra el incidente.
- **Por qué importa:** D54 presenta la guarda como "la única barrera que impide que el mensaje exista". Hoy cubre el error de serializar `HallazgoRegistrado`, gracias a `@JsonIgnore`, pero no otros errores igual de probables. No es explotable desde fuera hoy: registro no existe todavía.
- **Corrección** (ubicación, S): normalizar `datos` con el mismo `ObjectMapper` que usa el conversor, para que POJO, records y arrays se conviertan en `Map`/`List`/`Number`. Después, aplicar una **lista permitida** de §7.3 en lugar de solo una lista negra:
  ```java
  // PublicadorEventos
  private static final Set<String> CAMPOS_HALLAZGO = Set.of("hallazgoId", "version", "nombreCientifico", "nombreComun",
          "familia", "tipoEspecimen", "fechaRecoleccion", "estadoConservacion", "departamento", "descripcion");

  if (tipo != null && tipo.toLowerCase(Locale.ROOT).startsWith(PREFIJO_HALLAZGO)) {
      Object normalizado = objectMapper.convertValue(datos, Object.class);   // POJO/record/array -> Map/List/Number
      if (!(normalizado instanceof Map<?, ?> m) || !CAMPOS_HALLAZGO.containsAll(m.keySet())
              || DetectorCoordenadas.contiene(normalizado)) {
          log.warn("INCIDENTE: hallazgo.* rechazado antes de publicar (D14) tipo={}", tipo);   // sin valores ni claves
          throw new IllegalArgumentException("hallazgo.* no puede llevar coordenadas ni campos fuera de 04 §7.3 (D14)");
      }
  }

  // DetectorCoordenadas.contiene: un número no entero en cualquier nivel es sospechoso (en §7.3 solo `version` es numérico)
  } else if (nodo instanceof Double || nodo instanceof Float || nodo instanceof java.math.BigDecimal) {
      return true;
  }
  ```
  La lista permitida evita tener que adivinar nombres de clave (Darwin Core, `x`/`y`...) y cumple el contrato, que ya es cerrado. Para los falsos positivos: dejar documentado en `04` §7.3 que `lat.`/`long` seguidos de un número se rechazan, y pedir a registro (Hernando) que ejecute el **mismo** detector **antes del commit** y devuelva un 422 con el campo. Así el usuario reescribe el texto y no se llega a la inconsistencia posterior al commit. Opcional: rechazar en `publicar` cualquier `tipo` que no esté en la lista de §7.1.
- **Responsable sugerido:** ubicación (Juanita), por D54. La validación antes del commit es de Hernando. **Esfuerzo:** S.

### SEC-J-18 · Bajo · A09 (la auditoría D28 sigue fallando abierta ante un nivel de log bajado o un archivo que no se puede escribir)
- **Archivos:**
  - `servicio-cientifico/src/main/resources/logback-spring.xml:14-16,39-41`: el nivel `INFO` está fijado, pero Spring Boot aplica después `logging.level.*`.
  - `AuditoriaAccesoUbicacionLog.java:46-50`: escribe con `info(...)` sin comprobar nada.
  - `Dockerfile:10-11`: `/app/logs` sin volumen declarado.
  - En el repositorio no hay ningún compose de despliegue de servicio-cientifico que monte `/app/logs`. `deploy/` solo tiene `aiven/`.
- **Problema:**
  1. `LOGGING_LEVEL_BIOHUB_AUDITORIA_UBICACION=OFF` en el entorno (o en el `.env`), o un `logging.level.root=WARN` sin excepción explícita, apagan la auditoría sin ningún error. La limitación está documentada en el README y en `.env.example`, pero solo se controla por convención.
  2. Si la carpeta del archivo no se puede escribir, Logback deja el appender sin iniciar y solo emite un aviso de estado. La auditoría sigue saliendo solo por consola, y el servicio arranca igual.
  3. Sin volumen, el archivo vive en la capa escribible del contenedor y se pierde al recrearlo.
  4. "Solo anexado" es `append=true`. El proceso (`app`) puede truncar o borrar el archivo, y la retención (365 días / 10 GB) también borra. Esto es aceptable para el alcance actual. La inmutabilidad real queda para `ubicacion_acceso_log` (Could) o para enviar el log a un colector externo.
- **Por qué importa:** D28/RF11 exigen que **cada** descifrado y cada 403 dejen rastro. El código lo garantiza, pero un único ajuste de configuración lo anula sin que nadie lo note, y la auditoría es justo lo que debe sobrevivir a un operador descuidado.
- **Corrección** (ubicación, S): fallar cerrado al arrancar si la auditoría no está activa. Opcionalmente, también en cada llamada: el decorador ya convierte ese fallo en "no se entregan coordenadas".
  ```java
  // ubicacion/infrastructure/VerificadorAuditoria.java
  @Component
  class VerificadorAuditoria implements ApplicationListener<ApplicationReadyEvent> {
      @Override
      public void onApplicationEvent(ApplicationReadyEvent e) {
          var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("biohub.auditoria.ubicacion");
          var archivo = logger.getAppender("AUDITORIA_UBICACION");
          if (!logger.isInfoEnabled() || archivo == null || !archivo.isStarted()) {
              throw new IllegalStateException("Auditoría D28 inactiva (nivel o archivo): el servicio no arranca");
          }
      }
  }
  // AuditoriaAccesoUbicacionLog.registrar, al inicio (defensa ante un cambio de nivel en caliente)
  if (!auditoria.isInfoEnabled()) {
      throw new IllegalStateException("Auditoría D28 inactiva");
  }
  ```
  Con un test que arranque con `logging.level.biohub.auditoria.ubicacion=OFF` y espere el fallo. Para Santiago: al crear el compose del backend, montar un volumen nombrado en `/app/logs` (propietario 10001) y, si es posible, enviar ese archivo a un colector fuera de la VM.
- **Responsable sugerido:** ubicación (Juanita); el volumen es de Santiago. **Esfuerzo:** S.

### Informativos
- **SEC-J-I13 (puerto de auditoría sustituible):** con `AuditoriaAccesoUbicacion` como interfaz, un bean nuevo `@Primary` que la implemente (por ejemplo, un no-op "para pruebas" que termine en `src/main`) reemplazaría la auditoría en la cadena sin que falle ninguna regla. Conviene añadir a `ArquitecturaUbicacionTest`:
  ```java
  classes().that().implement(AuditoriaAccesoUbicacion.class)
      .should().haveFullyQualifiedName(INFRA + ".AuditoriaAccesoUbicacionLog").check(CLASES);   // CLASES excluye tests
  ```
- **SEC-J-I14 (Referer y política de teselas de OSM):** con la política por defecto del navegador (`strict-origin-when-cross-origin`), una petición entre orígenes ya enviaba solo el **origen** de web-interna, no la ruta. Por eso `no-referrer` aporta poco a la privacidad del punto (oculta que la IP es de un científico de BioHub). La política de uso de `tile.openstreetmap.org` pide un `Referer` válido a las aplicaciones web y puede bloquear teselas sin él. Hay que verificarlo en el navegador. Si OSM las bloquea, `onTeselasFallidas` dejaría el mapa sin fondo. En ese caso, usar `referrerPolicy="strict-origin"` (solo el origen, sin la ruta), que mantiene la protección de SEC-J-04. La solución definitiva sigue siendo el proxy de Santiago.
- **SEC-J-I15 (exclusiones por nombre simple que quedan):** `ArquitecturaUbicacionTest.java:161` (`doNotHaveSimpleName("ConfiguracionUbicacion")`, junto con `haveSimpleNameNotStartingWith("Decorador")`) y `:177-178` (`VisibilidadExacta`, `PoliticaAccesoUbicacion` y `ServicioDescifrado` por nombre simple) todavía permiten un homónimo en otro paquete, el mismo patrón que SEC-J-15. Con D53 (registro no puede nombrar nada de `ubicacion`), el riesgo práctico es despreciable. Si se toca el archivo, cambiarlo a `doNotHaveFullyQualifiedName(...)`.

### Regresiones
- **No se encontraron regresiones de seguridad.**
- Los mensajes de error nuevos no incluyen ni rutas, ni valores, ni causas.
- El Dockerfile sigue con `USER app` (UID 10001).
- Los comentarios de `application-aiven.yml` (ARQ-33) ahora coinciden con el valor por defecto `VERIFY_IDENTITY`.
- `.gitignore` añade `logs/`.

## Veredicto (iteración 16)
**APROBADO CON OBSERVACIONES.** No hay Críticos ni Altos.
- **SEC-J-12:** **cerrado**.
- **SEC-J-13:** **parcial**, porque la guarda existe pero se elude (SEC-J-17).
- **SEC-J-08:** **parcial**, porque el punto 3 falla abierto (SEC-J-18).
- **SEC-J-04:** **parcial**, porque falta el proxy de teselas de Santiago.
- **D44:** se mantiene tras ARQ-28 y ARQ-29.
- **Condición previa a publicar `hallazgo.*` reales (registro):** SEC-J-17, como mínimo la normalización con `ObjectMapper` y la lista permitida de §7.3.
- **SEC-J-18:** en la siguiente iteración de ubicación, y antes del primer despliegue con datos reales (el volumen es de Santiago).

---

# Seguimiento de la parte de Santiago (2026-11-05)

| ID | Estado | Verificación |
|---|---|---|
| SEC-02 | **Corregido en el gateway.** `denyAll` por defecto y roles por prefijo. Falta `denyAll` en los 4 servicios (lista en `11-seguridad-y-acceso.md`) | `MatrizAccesoGatewayTest` |
| SEC-04, SEC-D-03 | **Infraestructura lista.** Permisos mínimos por usuario. Corrige SEC-D-03: los consumidores necesitan `write` sobre `biohub.eventos.dlx` para declarar su cola. Falta que cada servicio migre su `.env` | Corrida completa con `cientifico_mq` y `consulta_mq`; un consumidor recibe `ACCESS_REFUSED` al publicar en `biohub.eventos` |
| SEC-05 | **Corregido.** Fallar cerrado con `ROLE_API_CLIENT` y mismos `RequestMatcher` en el filtro y en SecurityConfig. La rama de portal queda por IP (D16) | `ApiKeyRateLimitFilterTest` |
| SEC-J-05 | **Corregido.** Rate limit por IP y `maxmemory` en Redis | `GatewayIntegracionTest` |
| SEC-J-09 | **Corregido en parte.** Revocación de rol inmediata (D49). MFA sigue fuera del alcance del SRS | `GatewayIntegracionTest` |
| ARQ-03, ARQ-21 | **Corregidos.** API Keys, límites y bloqueo a los 3 intentos | `KeycloakAdminAdapterIntegracionTest` |
| ARQ-20 | **Corregido.** Circuit breaker por servicio destino (D52) | `CircuitoServiciosTest`, `ProxyServiceTest`, `GatewayIntegracionTest` |

---

# Iteración 17 – auditoría (2026-11-08)

**Revisor:** ciberseguridad · **Rama:** `feature/ubicacion-consulta-publica` (HEAD `124d516`) · **Alcance:** `git diff 9643411..HEAD` (commits `d0be1be`, `a0e0d32`, `6e9d2dc`, `b4053c3`, `d9a9031`, `124d516`): `denyAll` y `@PreAuthorize` en servicio-consulta-publica y servicio-cientifico, decisión D55, pruebas de seguridad, `Clock` en `ValidadorDatosHallazgo`, alteración determinista del cifrado en pruebas. Búsqueda de secretos en el árbol y en todo el historial. Efecto del realm y del compose del PR #7 sobre los servicios de Juanita. Tarea 17g de `00-plan.md`.

## Resumen

| Severidad | Cantidad (nuevos) |
|---|---|
| Crítico | 0 |
| Alto | 0 |
| Medio | 0 |
| Bajo | 3 (SEC-J-19 a SEC-J-21) |
| Informativo | 4 (SEC-J-I16 a SEC-J-I19) |

**Veredicto: APROBADO CON OBSERVACIONES.** No hay Críticos ni Altos.
- El `denyAll` de los dos servicios está bien hecho: no hay matchers amplios y `/actuator/**` solo deja pasar `health`.
- Los cinco endpoints de servicio-cientifico y los cuatro de consulta-pública cumplen la matriz §2.
- El cifrado de producción no cambió, y la nueva alteración XOR sí prueba la detección de manipulación.
- El `Clock` no abre ninguna vía de inyección en producción.
- No hay secretos ni en el árbol ni en el historial.
- **D55 se acepta con condiciones** (ver abajo). Los tres Bajos son las condiciones: la prueba de arquitectura se puede eludir (SEC-J-19), el 403 de los endpoints fuera de `ubicacion` no es `ROL_INSUFICIENTE` (SEC-J-20) y el cuerpo se valida antes de comprobar el rol (SEC-J-21).

## Cómo se revisó
- **Documentación:** `11-seguridad-y-acceso.md` (§2 matriz, §3 plantilla, §4 RabbitMQ), D21, D48, D53, D54 y D55 (`03-arquitectura.md:72,98,104-106`), contrato §5.1 y §5.2 (`04-contrato-api.md:252-373`, `ROL_INSUFICIENTE` en `:131`), y las secciones "Iteración 16" y "Seguimiento de la parte de Santiago" de este reporte.
- **Código (estático):** el diff completo y una lectura completa de:
  - los dos `SecurityConfig`, `KeycloakJwtAuthenticationConverter` y todos los `@RestController` y `@RestControllerAdvice` de los dos servicios (`grep` de `@Controller`, `@RequestMapping`, `RouterFunction`, `@Endpoint`, `ServletRegistrationBean`: no hay otros puntos de entrada HTTP);
  - `PoliticaAccesoUbicacion`, `UbicacionExceptionHandler`, `EventosExceptionHandler` y `CifradorAesGcm`;
  - `application.yml` (actuator, `server.address`), los `Dockerfile`, `SecurityConfig` del gateway (`:58-69`), `infra/keycloak/realm-biohub.json`, `infra/rabbitmq/crear-usuarios.sh` y el servicio `rabbitmq-usuarios` de `docker-compose.yml`.
- **Pruebas:** en una copia de `HEAD` (`git archive`) en el scratchpad, para no chocar con el build de QA, se ejecutó `mvn -o -pl servicio-consulta-publica,servicio-cientifico -am test` con `SeguridadEndpointsTest`, `ArquitecturaSeguridadTest`, `ValidadorDatosHallazgoTest`, `CifradorAesGcmTest`, `RecifradoServiceTest`, `UbicacionServiceIntegracionTest`, `BusquedaHallazgosApiTest` y `AuditoriaYSinFiltracionesTest`. Resultado: **172 tests, 0 fallos, 0 omitidos** (las de MySQL con Testcontainers). Sin perfil `aiven` y sin conexión a Aiven.
- **Sondas (solo en la copia, nunca en el repositorio):**
  - `EvasionReglaSeguridadTest`: aplica la regla de `ArquitecturaSeguridadTest` a seis controladores de prueba (resultado en SEC-J-19);
  - `OrdenValidacionTest`: con el `SecurityConfig` real, llama a `POST /api/cientifico/eventos/prueba` y `GET /api/cientifico/ping` con un JWT sin roles y con `investigador_campo` (resultado en SEC-J-20 y SEC-J-21).
- **Secretos:** script propio sobre `git log -p HEAD` (89 commits) y `git log -p --all` (93) que solo imprime commit, archivo, tipo y el valor **enmascarado**. Patrones: asignaciones `*_PASSWORD=`, `*_SECRET=`, `*_TOKEN=`, `*LLAVE*=`, `KEYCLOAK_GATEWAY_CLIENT_SECRET=`, llaves `vN:` + base64 de 32 bytes, cualquier base64 de 32 bytes, URL con usuario:contraseña, hosts `aivencloud.com` con credenciales y prefijo `avns_`, JWT (`eyJ…`), PEM con clave privada, claves AWS y tokens de GitHub. Además: `git ls-files`, `git log --all --diff-filter=A --name-only` y `git check-ignore`. No se abrió ningún `.env`, `certs/`, `*.pem` ni `*.p12`: solo se comprobó que existen y que Git los ignora.

## 1. `denyAll` en los dos servicios

**servicio-consulta-publica** (`config/SecurityConfig.java:29-39`):

| Regla | Evaluación |
|---|---|
| `ERROR` dispatcher `permitAll` (`:30`) | Correcto: no expone nada, solo deja llegar los 400/500 ya autorizados |
| `GET /actuator/health`, `/actuator/health/**` (`:31`) | Correcto. `/actuator/info` y el resto de `/actuator/**` caen en `denyAll` (probado con `/actuator/env`). Ver SEC-J-I17 |
| `GET /api/publica/eventos/recibidos` → `hasRole('administrador')` (`:33`) | Correcto y más estrecho que antes (`/api/publica/eventos/**` sin método). Doble barrera: URL y `@PreAuthorize` |
| `GET /api/publica/ping`, `/api/publica/fichas`, `/api/publica/fichas/*` → `permitAll` (`:35-36`) | Correcto. `*` es **un solo segmento**: `/fichas/{id}/extra` se deniega (probado). El `id` se valida además como UUID canónico en el controlador |
| `anyRequest().denyAll()` (`:39`) | Correcto. `POST`/`PUT`/`PATCH`/`DELETE`, `HEAD`, otras rutas de `/api/publica/**`, `/api/cientifico/**`, `/api/v1/externa/**` y `/` se rechazan: 401 sin token y 403 con token, aunque sea de administrador |

**servicio-cientifico** (`config/SecurityConfig.java:29-38`): `ERROR` y `health` como arriba, `/api/cientifico/**` → `authenticated()` (`:36`) y `anyRequest().denyAll()` (`:38`). Todo lo que está fuera del prefijo se rechaza aunque el usuario tenga los 5 roles (probado con 10 rutas y 3 métodos, entre ellas `/api/cientificoX/ping`, `/actuator/env` y `/actuator`). No hay matchers demasiado amplios: el único comodín es el prefijo propio del servicio, y D55 lo justifica.

### Decisión sobre D55: **aceptada, con condiciones**

D55 no exige el rol en la URL (`.authenticated()`), sino en el `@PreAuthorize` de cada método. **No exijo cambiar a `hasAnyRole` + `AccessDeniedHandler`**, por estas razones:
1. **Defensa en profundidad real.** El gateway exige uno de los 5 roles para `/api/cientifico/**` (`api-gateway/.../SecurityConfig.java:68`, D48). El servicio escucha en `127.0.0.1` (`application.yml:50`). El JWT debe llevar `aud=biohub-api`, y ese claim solo lo emiten `web-interna` y `biohub-pruebas`: el token de la cuenta de servicio `biohub-gateway` no lo lleva (realm comprobado) y el servicio lo rechaza con 401. Así, un JWT "autenticado sin rol" solo llega al método si alguien se salta el gateway desde el propio host.
2. **`hasAnyRole` de los 5 roles en la URL aporta poco frente a eso.** Solo frena a cuentas sin ningún rol (como `sinrol1`, que es una cuenta de desarrollo). No ayuda en el caso que de verdad importa: un rol válido (por ejemplo `analista_ambiental`) que entra a un endpoint que no le corresponde. Ese caso lo controla solo el `@PreAuthorize`, con o sin D55.
3. **Lo que D55 consigue es concreto.** En `ubicacion`, el 403 sin roles llega a `UbicacionExceptionHandler` y sale como problem+json `ROL_INSUFICIENTE`, con `no-store` y auditoría D28 (probado por `elRol403SinRolesConservaElProblemJsonDelContrato`). Con `hasAnyRole` habría que reproducir todo eso en un `AccessDeniedHandler`.

**Condiciones** (D55 pasa a ser la única barrera de rol dentro del servicio, así que su red de seguridad debe ser fuerte):
- **Antes de que registro (Hernando) integre controladores en servicio-cientifico:** corregir SEC-J-19 (endurecer `ArquitecturaSeguridadTest`) y SEC-J-20 (403 uniforme). Si registro entra sin SEC-J-19, la alternativa de D55 (`hasAnyRole` en la URL) pasa a ser **obligatoria**.
- **Para el despliegue en contenedores (Santiago):** el `Dockerfile` pone `SERVER_ADDRESS=0.0.0.0` (`servicio-cientifico/Dockerfile:14`), así que el supuesto "solo `127.0.0.1`" se convierte en "solo una red interna". Los puertos 8081 y 8083 no se publican en el host y solo el gateway comparte red con ellos (SEC-J-I19).
- Matiz para el texto de D55: la ventaja del problem+json solo existe hoy en `ubicacion`. En `/api/cientifico/ping` y `/api/cientifico/eventos/prueba`, el 403 sin rol **sigue saliendo vacío** (comprobado; SEC-J-20).

## 2. `@PreAuthorize` y coherencia con la matriz §2

| Endpoint | `@PreAuthorize` | Matriz §2 / contrato | ¿Coincide? |
|---|---|---|---|
| `GET /api/cientifico/ubicacion/hallazgos` (`BusquedaHallazgosController.java:27`) | `hasAnyRole` de los 5 | 5 roles; solo alto rango recibe lat/lon (lo decide `PoliticaAccesoUbicacion.estrategiaPara`) | Sí |
| `GET /api/cientifico/ubicacion/mapa` (`MapaUbicacionController.java:27`) | `@politicaUbicacion.puedeVerCoordenadas(authentication)` | Solo `cientifico_alto_rango` | Sí |
| `POST /api/cientifico/ubicacion/recifrado` (`RecifradoController.java:26`) | `hasRole('administrador')` | Solo `administrador` | Sí |
| `GET /api/cientifico/ping` (`PingController.java:21`) | `hasAnyRole` de los 5 | Fuera de la matriz (diagnóstico) | Aceptable |
| `POST /api/cientifico/eventos/prueba` (`EventosPruebaController.java:33`) | `hasRole('administrador')` | Fuera de la matriz (diagnóstico) | Aceptable |
| `GET /api/publica/ping`, `/fichas`, `/fichas/{id}` | `permitAll()` explícito | Públicos | Sí |
| `GET /api/publica/eventos/recibidos` (`EventosRecibidosController.java:29`) | `hasRole('administrador')` | Solo administrador | Sí |

- **`puedeVerCoordenadas`** (`PoliticaAccesoUbicacion.java:53-58`) compara la authority **exacta** `ROLE_cientifico_alto_rango`, rechaza la autenticación anónima y no cambió en este diff. La prueba rechaza las variantes `ADMINISTRADOR`, `Cientifico_Alto_Rango`, `administrador ` y `ROLE_administrador`, y el converter descarta `offline_access`, `uma_authorization` y `default-roles-*`.
- **RF12:** `administrador` recibe 403 en `/mapa` (`administradorNoRecibeElMapaYAltoRangoNoRecifra`) y, en `/hallazgos`, la vista generalizada sin lat/lon (`AuditoriaYSinFiltracionesTest.java:77`, que incluye `administrador`). Caso límite: un usuario con **los dos** roles (ver SEC-J-I16).
- **`SeguridadEndpointsTest`** es sólido:
  - servicio-cientifico (56 casos): endpoint × 5 roles, 401 sin token, 403 con JWT sin roles, 403 con roles técnicos, variantes de mayúsculas o espacios, denyAll fuera del prefijo y 404 (no 403) para rutas futuras del prefijo;
  - servicio-consulta-publica (40 casos): públicos con y sin token, eventos solo para administrador y 15 combinaciones no listadas con 401 y 403.

  Dos huecos menores: en servicio-cientifico solo se comprueba el cuerpo `ROL_INSUFICIENTE` en `/hallazgos` (con SEC-J-20 conviene parametrizarlo para todos los endpoints), y la prueba usa `jwt().authorities(...)`, que no pasa por `KeycloakJwtAuthenticationConverter` (el converter sí se ejerce en `BaseUbicacionMySqlTest` y en `EventosPruebaControllerTest`).
- **`ArquitecturaSeguridadTest`:** la regla es correcta para el caso típico, y su prueba de control (`laReglaFallaSiFaltaElRol`) evita que quede vacía sin que nadie lo note. Pero se puede eludir: ver SEC-J-19.

## 3. Recifrado y cifrado (d9a9031, 124d516)

- **Solo tocan pruebas.** `git show --stat`: `RecifradoServiceTest`, `UbicacionServiceIntegracionTest`, `BaseUbicacionMySqlTest`, `AuditoriaYSinFiltracionesTest` y `BusquedaHallazgosApiTest`, todas en `src/test`. `git diff 9643411..HEAD -- '*/src/main/*'` no incluye ningún archivo de `ubicacion`. `CifradorAesGcm.java` se modificó por última vez en `900ed86`.
- **Producción sin cambios** (`CifradorAesGcm.java`): `AES/GCM/NoPadding` con llave de 256 bits del proveedor, `BITS_TAG = 128` (`:27`), `BYTES_NONCE = 12` (`:28`), un `SecureRandom` por instancia con `nextBytes` en **cada** cifrado y recifrado (`:32,85-86`), AAD = UUID canónico en minúsculas (`:88,106,117-119`), y fallo cerrado ante un nonce de otra longitud, un texto nulo o un plano que no mide 16 bytes (`:99-111`).
- **La alteración XOR sí prueba la detección:**
  - `CHAR(ASCII(coord_cifrada) ^ 0x01 USING binary)` invierte un bit del primer byte del texto cifrado, que va en una columna distinta del nonce;
  - `alterarPrimerByteDelCifrado` comprueba, fila por fila, que el valor **cambió**, que conserva el tamaño y que `ahora[0] == previo[0] ^ 0x01`, y exige que haya filas (`isNotEmpty`);
  - así desaparece la variante anterior, que fallaba en silencio 1 de cada 256 veces (sobrescribir con `0xFF` un byte que ya valía `0xFF` no alteraba nada).

  Las tres consecuencias se siguen comprobando: `CoordenadasNoDescifrablesException` por integridad, `NO_DISPONIBLES`/`omitidos` solo para alto rango, y que el log no lleve datos. `CifradorAesGcmTest` cubre aparte la alteración del texto (byte 3), del nonce, de la longitud del nonce, del AAD (otro hallazgo) y de la versión de llave. Las 52 pruebas de cifrado y ubicación ejecutadas pasan.

## 4. `Clock` en `ValidadorDatosHallazgo` (b4053c3)

- **Ningún reloj arbitrario en producción.** Spring usa el constructor público con `@Autowired` (`:60-63`), que fija `Clock.system(ZONA_BOGOTA)`. El constructor que recibe un `Clock` es *package-private*: ni un bean `Clock` del contexto ni una propiedad pueden llegar a él. El único otro `Clock` del servicio es el de `MonitorCarga`, que tampoco se inyecta como bean.
- **La validación de fechas futuras no cambia:** `LocalDate.ofInstant(reloj.instant(), ZONA_BOGOTA)` (`:123`) equivale a `LocalDate.now(ZONA_BOGOTA)` con el reloj del sistema, y la regla sigue igual (`isAfter(hoy.plusDays(1))` o año < 1800, `:124`). El día se calcula siempre en Bogotá, aunque el reloj venga en otra zona.
- Las pruebas nuevas cubren justo el borde que antes hacía fallar la de SEC-J-01: medianoche de Bogotá, 05:00Z. Los 20 casos pasan.

## 5. Secretos

- **Árbol actual:**
  - `git ls-files` no contiene `.env`, `certs/`, `*.pem`, `*.p12`, `*.jks`, `*.pfx` ni `*.key`.
  - Solo están rastreados `*.env.example`, en los que **todas** las variables `*PASSWORD*`, `*SECRET*` y `*TOKEN*` son marcadores `CAMBIAR…` o están vacías (comprobado sin imprimir valores), y `web-*/.env.development`, que solo tiene URL locales y el `client_id` público.
  - `.env`, `certs/`, `*.p12` y los `.env` de servicio están ignorados (`.gitignore:15,35,46`). `.env` y `certs/` existen en local, sin rastrear, y no se abrieron.
  - El realm usa marcadores `${…}` para las 6 contraseñas de usuarios de prueba y para el secret de `biohub-gateway`.
- **Historial completo:**
  - `git log --all --diff-filter=A` no muestra ningún `.env`, `certs/`, `*.pem` ni `*.p12` añadido nunca. Solo `*.env.example`, incluidos `deploy/backend/.env.example` y `deploy/datos/.env.example` de `origin/feature/despliegue`.
  - Ninguna versión del realm (`bc82cd7`, `73636c9`) tuvo contraseñas o secrets literales.
  - No aparecen llaves `vN:`+base64, URL con credenciales, hosts Aiven con credenciales, `avns_`, JWT, PEM, claves AWS ni tokens de GitHub.
  - Las coincidencias de `*_PASSWORD=` son referencias `$VAR`, marcadores o ejemplos de documentación (por ejemplo, `servicio-cientifico/README.md` en `d92c920`: `UBICACION_KEYSTORE_PASSWORD=la…`, texto explicativo). Los hosts `*.a.aivencloud.com` que aparecen son de ejemplo (`mysql-xxxx-proyecto`, `CAMBIAR`, `mysql-x`).
- **Único valor literal:** `.github/workflows/ci.yml`, commit `5b168b2`, tiene contraseñas de CI (`ci…`) y la llave `CI_UBICACION_CLAVE` (base64 de 32 bytes, `Ym…`). Son valores ficticios de contenedores efímeros del job, ya registrados como **SEC-J-I7**. Se comprobó que esa llave no se reutiliza en ningún otro archivo ni commit. Sigue abierto como informativo.

## 6. Observaciones sobre el realm y el compose del PR #7

- **SEC-04 en los servicios de Juanita:** `rabbitmq-usuarios` (`docker-compose.yml:128-148`) ya crea `cientifico_mq` (configure y write solo sobre `biohub.eventos`, sin read: `crear-usuarios.sh:34-37`) y `consulta_mq`. servicio-cientifico y consulta-pública **siguen usando el usuario administrador `biohub`** hasta que se migren sus `.env` (`11-seguridad-y-acceso.md` §4, incluido `BIOHUB_EVENTOS_PUBLICADOR_CIENTIFICO=cientifico_mq`). Mientras tanto, SEC-J-I10 (el `user_id` solo prueba que "tiene las credenciales del broker") sigue vigente. Responsable: Juanita (configuración, sin cambiar código).
- **Para Santiago:** `consulta_mq` y `reportes_mq` necesitan write sobre `biohub.eventos.dlx` para declarar su cola. Como las dos DLQ se enlazan a ese exchange con el nombre de la cola como clave de enrutamiento, un consumidor comprometido podría publicar mensajes falsos en la DLQ del otro (`reportes.eventos.dlq` ← `consulta_mq`). El impacto es bajo: son colas de inspección. Si se quiere cerrar, que la declaración de colas y DLX la haga `rabbitmq-usuarios` con el administrador y que los consumidores queden sin configure ni write.
- **Realm y D55:** `sinrol1` y `biohub-pruebas` (password grant con `aud=biohub-api`) son justo la población "autenticada sin rol" que D55 deja llegar al método si alguien se salta el gateway. Fuera de local deben eliminarse, como ya se pidió en este reporte (sección del esqueleto, punto 2 de "fuera de local").
- La API de usuarios del gateway asigna **exactamente un rol** (`ServicioUsuarios.java:18`, `UsuarioDtos.java:20`). Por eso el caso de SEC-J-I16 solo puede darse desde la consola de Keycloak.

## Estado de los hallazgos previos afectados por este diff

| ID | Estado | Evidencia |
|---|---|---|
| **SEC-02** (lado servicio) | **Cerrado** en servicio-consulta-publica y servicio-cientifico. Sigue pendiente en equipos y reportes (Nicolás) | Secciones 1 y 2. `SeguridadEndpointsTest` (96 casos) y `ArquitecturaSeguridadTest` en los dos servicios |
| **SEC-J-01** (validación estricta del payload) | **Sigue cerrado** | Mismo criterio de fecha. El `Clock` solo hace determinista la prueba (sección 4) |
| **D44 / SEC-J-10, 15, 16** | **Sin cambios** | El diff no toca `ArquitecturaUbicacionTest` ni la cadena auditoría → cifrado → JPA. `UbicacionExceptionHandler` sigue auditando cada 403 de `ubicacion`, ahora también el de un JWT sin roles |
| **SEC-J-17, SEC-J-18** | **Siguen abiertos** (no son de este diff) | Sin cambios en `PublicadorEventos`/`DetectorCoordenadas` ni en `logback-spring.xml` |
| **SEC-J-I7** | **Abierto** (informativo) | Sección 5 |
| **SEC-04** (servicios de Juanita) | **Abierto**: infraestructura lista, falta migrar los `.env` | Sección 6 |

## Hallazgos nuevos

### SEC-J-19 · Bajo · A01 (`ArquitecturaSeguridadTest` se puede eludir y no mira el contenido del `@PreAuthorize`)
- **Archivos:** `servicio-cientifico/src/test/java/co/edu/unbosque/biohub/cientifico/ArquitecturaSeguridadTest.java:34-39`, y la regla idéntica en `servicio-consulta-publica/.../ArquitecturaSeguridadTest.java:34-39`.
- **Problema:** la regla solo mira métodos **declarados** en clases anotadas **directamente** con `@RestController`, y solo exige que **exista** un `@PreAuthorize`. Resultado de la sonda en la copia:

  | Controlador de prueba | ¿Pasa la regla sin control de rol? |
  |---|---|
  | `@Controller` + `@ResponseBody` en la clase | **Sí** |
  | `@Controller` con método que devuelve `ResponseEntity` | **Sí** |
  | `@RestController` que hereda un `@GetMapping` de una clase base sin anotar | **Sí** |
  | `@RestController` que implementa una interfaz con `@GetMapping` (estilo OpenAPI generator) | **Sí** |
  | `@GetMapping` con `@PreAuthorize("isAuthenticated()")` | **Sí** |
  | `@PreAuthorize` a nivel de clase y métodos sin anotar | No (falso positivo: más estricto, aceptable) |

  Tampoco cubre `RouterFunction`/`HandlerFunction` ni un estereotipo propio meta-anotado con `@RestController`, porque `areAnnotatedWith` no sigue meta-anotaciones. En servicio-cientifico, con D55, `isAuthenticated()`, `permitAll()` o `true` en un endpoint de registro dejarían entrar a **cualquiera de los 5 roles** (por ejemplo, `analista_ambiental` en el historial), y no fallaría ninguna prueba.
- **Por qué importa:** D55 quita la barrera de rol de la URL y se apoya en esta prueba, que pasa a ser la red de seguridad de HU-10 c.2 dentro del servicio. Hoy no hay ningún endpoint afectado. El riesgo aparece cuando registro empiece a añadir controladores.
- **Corrección:** ampliar la selección y validar el contenido. En servicio-cientifico, prohibir expresiones que no nombren un rol:
  ```java
  private static final DescribedPredicate<CanBeAnnotated> ES_ENDPOINT =
          CanBeAnnotated.Predicates.metaAnnotatedWith(RequestMapping.class)
                  .or(CanBeAnnotated.Predicates.annotatedWith(RequestMapping.class));

  // 1) Solo @RestController: cierra @Controller + @ResponseBody y los estereotipos propios
  static ArchRule controladoresSonRest() {
      return classes().that().areMetaAnnotatedWith(Controller.class)
              .should().beAnnotatedWith(RestController.class);
  }
  // 2) Los mappings se declaran en la clase concreta del controlador: cierra la herencia y las interfaces
  static ArchRule mappingsSoloEnRestControllerConcreto() {
      return methods().that(ES_ENDPOINT)
              .should().beDeclaredInClassesThat().areAnnotatedWith(RestController.class)
              .andShould().beDeclaredInClassesThat().areNotInterfaces()
              .andShould().beDeclaredInClassesThat().doNotHaveModifier(JavaModifier.ABSTRACT);
  }
  // 3) Solo servicio-cientifico: el @PreAuthorize debe nombrar un rol o la política de ubicación
  static ArchRule endpointsConRolExplicito() {
      return methods().that(ES_ENDPOINT).should(new ArchCondition<JavaMethod>("tener @PreAuthorize con un rol explícito") {
          @Override
          public void check(JavaMethod m, ConditionEvents eventos) {
              String expr = m.tryGetAnnotationOfType(PreAuthorize.class).map(PreAuthorize::value).orElse("");
              boolean conRol = expr.matches(".*\\b(hasRole|hasAnyRole)\\(.*|.*@politicaUbicacion\\..*")
                      && !expr.matches(".*\\b(permitAll|isAuthenticated|isFullyAuthenticated|isAnonymous|true)\\b.*");
              if (!conRol) {
                  eventos.add(SimpleConditionEvent.violated(m, m.getFullName() + ": @PreAuthorize sin rol explícito"));
              }
          }
      });
  }
  // 4) Sin endpoints funcionales
  static ArchRule sinRouterFunction() {
      return noClasses().should().dependOnClassesThat()
              .areAssignableTo(org.springframework.web.servlet.function.RouterFunction.class);
  }
  ```
  En consulta-pública, `permitAll()` sí es válido en los públicos, porque allí el `denyAll` de la URL exige un matcher explícito. Basta con aplicar las reglas 1, 2 y 4 junto con la actual. Añadir a las dos pruebas de control un fixture por cada evasión de la tabla.
- **Responsable sugerido:** ubicación (Juanita), porque la prueba es suya; avisar a Hernando. **Condición previa** a integrar controladores de registro (ver D55). **Esfuerzo:** S.

### SEC-J-20 · Bajo · A05 / A09 (fuera de `ubicacion`, el 403 por rol no es `ROL_INSUFICIENTE`: la justificación de D55 es parcial)
- **Archivos:** `servicio-cientifico/.../ubicacion/controller/UbicacionExceptionHandler.java:32,56-68`: el manejador de `AccessDeniedException` está limitado a `basePackages = "...ubicacion.controller"`. `EventosExceptionHandler.java:15` no maneja `AccessDeniedException`. `SecurityConfig.java:24-41` no define un `accessDeniedHandler`.
- **Problema (comprobado en la copia):** con un JWT sin roles, `GET /api/cientifico/ping` responde **403 sin cuerpo ni `Content-Type`**. Lo mismo ocurre con `POST /api/cientifico/eventos/prueba` y con cualquier 403 de un rol válido en esos endpoints. En el servidor real, la excepción termina en el `/error` de Spring Boot (`{timestamp,status,error,path}`), no en problem+json `ROL_INSUFICIENTE` con `no-store`. Esa es precisamente la situación que D55 dice evitar, y D48 promete `ROL_INSUFICIENTE` para todo 403. Cuando registro añada controladores, sus 403 heredarán este comportamiento.
- **Por qué importa:** son respuestas inconsistentes con el contrato, y `web-interna` tendría que tratar dos formatos de 403. No filtran datos (el cuerpo del `/error` no incluye trazas). No se pierde auditoría D28, porque la de `ubicacion` sigue funcionando.
- **Corrección:** un 403 uniforme para todo servicio-cientifico, sin tocar la auditoría de `ubicacion` (que ya tiene `@Order(HIGHEST_PRECEDENCE)` y se evalúa primero):
  ```java
  // config/AccesoDenegadoGlobal.java
  @RestControllerAdvice
  @Order(Ordered.LOWEST_PRECEDENCE)
  class AccesoDenegadoGlobal {
      @ExceptionHandler(AccessDeniedException.class)
      ResponseEntity<ProblemDetail> denegado(AccessDeniedException e, HttpServletRequest req) throws AccessDeniedException {
          Authentication a = SecurityContextHolder.getContext().getAuthentication();
          if (a == null || a instanceof AnonymousAuthenticationToken) throw e;          // 401 lo resuelve Security
          ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "No tienes permiso para ver esta información.");
          pd.setTitle("Acceso denegado");
          pd.setProperty("codigo", "ROL_INSUFICIENTE");
          return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                  .cacheControl(CacheControl.noStore()).body(pd);
      }
  }
  // SecurityConfig: el mismo cuerpo para los 403 de la cadena de filtros (denyAll fuera del prefijo).
  // ProblemaAccesoDenegadoHandler implementa AccessDeniedHandler y escribe el mismo problem+json con no-store.
  .exceptionHandling(ex -> ex.accessDeniedHandler(new ProblemaAccesoDenegadoHandler()))
  ```
  Y parametrizar `elRol403SinRolesConservaElProblemJsonDelContrato` sobre `ENDPOINTS`.
- **Responsable sugerido:** ubicación (Juanita), porque `config/` es compartido con Hernando. **Esfuerzo:** S.

### SEC-J-21 · Bajo · A04 (el cuerpo se deserializa y se valida antes de comprobar el rol)
- **Archivos:** `servicio-cientifico/.../config/SecurityConfig.java:36` (sin rol en la URL) y `eventos/controller/EventosPruebaController.java:34` (`@Valid @RequestBody`).
- **Problema (comprobado en la copia):** `@PreAuthorize` se evalúa al invocar el método, **después** de resolver los argumentos. Un JWT **sin roles** y uno de `investigador_campo` que mandan a `POST /api/cientifico/eventos/prueba` (endpoint solo para administrador) un `mensaje` de 300 caracteres reciben `400` con el detalle `"mensaje: máximo 200 caracteres"`, y con JSON inválido también `400`, en vez de `403`. Esto es propio de la seguridad por método. D55 lo extiende a los usuarios sin rol, que con `hasAnyRole` en la URL recibirían 403 antes de leer el cuerpo.
- **Por qué importa:** hoy el impacto es mínimo (un endpoint de diagnóstico). Con registro cambia: los adjuntos de hasta 10 MB (`11` §3, Hernando) se leerían y validarían en memoria para usuarios que no pueden registrar (`auditor_cientifico`, `analista_ambiental`, `administrador`). Además, los mensajes de validación revelan la forma del contrato a quien no debería usarlo.
- **Corrección:** en los endpoints con cuerpo grande o multipart, repetir el rol en la URL, **antes** del matcher general (no contradice D55: el 403 lo resuelve el `accessDeniedHandler` de SEC-J-20):
  ```java
  .requestMatchers(HttpMethod.POST, "/api/cientifico/hallazgos", "/api/cientifico/hallazgos/*/adjuntos")
          .hasAnyRole("investigador_campo", "cientifico_alto_rango")
  .requestMatchers("/api/cientifico/**").authenticated()
  .anyRequest().denyAll())
  ```
  Y configurar `spring.servlet.multipart.max-file-size`/`max-request-size` acordes al límite del gateway.
- **Responsable sugerido:** Hernando (endpoints de registro), con revisión de Juanita en `SecurityConfig`. **Esfuerzo:** S.

### Informativos
- **SEC-J-I16 (usuario con `administrador` y `cientifico_alto_rango` a la vez):** `puedeVerCoordenadas` solo busca `ROLE_cientifico_alto_rango`. Un usuario que además sea `administrador` recibiría coordenadas, lo que choca con la lectura literal de RF12 ("administrador nunca recibe coordenadas"). La API del gateway impide esa combinación (exactamente un rol), pero la consola de Keycloak no. Opcional: `&& authorities.noneMatch("ROLE_administrador")` en `puedeVerCoordenadas`, con una prueba, o una regla de Keycloak/gateway que lo vigile. Decisión de Juanita y el Tech Lead.
- **SEC-J-I17 (actuator `info` expuesto pero denegado):** `management.endpoints.web.exposure.include: health,info` (`servicio-cientifico/.../application.yml:59`, `servicio-consulta-publica/.../application.yml:79`), pero la URL solo permite `health`. No hay riesgo; para alinear la configuración con la intención, usar `include: health`.
- **SEC-J-I18 (comentario desactualizado):** `servicio-consulta-publica/.../config/SecurityConfig.java:38` cita `ArquitecturaConsultaPublicaTest`, que no existe. La prueba es `ArquitecturaSeguridadTest`.
- **SEC-J-I19 (D7 y D55 en contenedores):** `servicio-cientifico/Dockerfile:14` y los demás Dockerfile ponen `SERVER_ADDRESS=0.0.0.0`. Al crear el compose del backend (Santiago): no publicar 8081 ni 8083 en el host, usar una red `internal` compartida solo con el gateway, y documentarlo en `09-despliegue.md` como condición de D55.

## Bien hecho (esta iteración)
- `denyAll` real en los dos servicios, con matchers **exactos** por método y ruta en consulta-pública. El de eventos se estrechó de `/eventos/**` a `GET /eventos/recibidos`.
- `@PreAuthorize("permitAll()")` explícito en los públicos: la intención queda escrita y la prueba de arquitectura lo exige.
- Pruebas de matriz exhaustivas (endpoint × rol, sin token, sin roles, roles técnicos, variantes de nombre, denyAll con varios métodos), más la prueba de control de la propia regla ArchUnit.
- D55 está documentada con su alternativa y deja la decisión a ciberseguridad. El código mantiene la auditoría D28 de **todos** los 403 de `ubicacion`, también el de un JWT sin roles.
- Las correcciones de pruebas inestables arreglan la causa (1 de cada 256) en vez de reintentar. La alteración verifica que el dato cambió, y la ayuda quedó en la base común de las tres clases.
- El `Clock` se inyecta por un constructor de paquete sin abrir superficie en producción, y las pruebas cubren los bordes exactos de la medianoche Bogotá/UTC.
- Ningún secreto nuevo. Los mensajes de commit describen el cambio sin incluir valores.

## Veredicto (iteración 17)
**APROBADO CON OBSERVACIONES.** No hay Críticos ni Altos.
- **D55:** **aceptada**. No se exige `hasAnyRole` + `AccessDeniedHandler`, con dos condiciones:
  - SEC-J-19 y SEC-J-20 corregidos **antes** de que registro integre controladores en servicio-cientifico. Si no, la alternativa `hasAnyRole` pasa a ser obligatoria.
  - En contenedores, la red interna sin puertos publicados (SEC-J-I19).
- **SEC-J-21:** la aplica Hernando en los endpoints con cuerpo o adjuntos.
- **SEC-02:** cerrado para los dos servicios de Juanita.
- **Cifrado:** sin cambios en producción. La detección de manipulación sigue probada.
- **Secretos:** ninguno en el árbol ni en el historial (SEC-J-I7 sigue como informativo).
- **SEC-04:** migrar los `.env` de servicio-cientifico y consulta-pública a `cientifico_mq` y `consulta_mq`.

---

# Iteración 18 – re-verificación de SEC-J-19 y SEC-J-20 (2026-11-09)

**Revisor:** ciberseguridad · **Rama:** `feature/ubicacion-consulta-publica` (HEAD `913aece`) · **Alcance:** `git show d718be5` (SEC-J-19) y `git show e49373a` (SEC-J-20), condiciones de D55 antes de que `registro` integre controladores, y lectura de los 3 controladores de `origin/feature/registro-cientifico` (`0137053`). Antes del push y del PR a `develop`.

## Resumen

| Severidad | Cantidad (nuevos) |
|---|---|
| Crítico | 0 |
| Alto | 0 |
| Medio | 1 (SEC-J-23, en la rama de Hernando, no en este diff) |
| Bajo | 1 (SEC-J-22) |
| Informativo | 3 (SEC-J-I20 a SEC-J-I22) |

| ID | Estado |
|---|---|
| **SEC-J-19** | **Parcial.** Las 6 vías de la tabla de la iteración 17, y las que se añadieron (estereotipos propios, rutas funcionales, handlers alternativos, `isAuthenticated()`, `true`, vacío, `rol or permitAll()`, `rol or 1 == 1`), quedan detectadas. Siguen pasando 3 vías nuevas, todas comprobadas: `@GetExchange`/`@HttpExchange` y los métodos `final` o `static` (SEC-J-22) |
| **SEC-J-20** | **Cerrado.** 403 problem+json `ROL_INSUFICIENTE` con `no-store` en todo servicio-cientifico y consulta-pública. El 401 va sin cuerpo y sin la descripción interna. La auditoría de `ubicacion` no cambia |
| SEC-J-21 | Abierto (Hernando). Sin cambios en este diff |

**Veredicto: APROBADO CON OBSERVACIONES** (detalle al final).

## Cómo se revisó
- **Documentación:** D55 (`03-arquitectura.md:106`), contrato §2, §5.1 y la tabla de códigos (`04-contrato-api.md:131,252-330`), matriz §2 de `11-seguridad-y-acceso.md` y la sección "Iteración 17" de este reporte.
- **Código (estático):** los dos `ArquitecturaSeguridadTest`, las dos `SondasElusion`, los dos `ProblemaSeguridadHandler`, los dos `SecurityConfig`, `ConsultaPublicaExceptionHandler`, `UbicacionExceptionHandler` y `EventosExceptionHandler`. En la rama de Hernando: `HallazgoController`, `FichaPlantillaController`, `HistorialController`, `RegistroExceptionHandler` y los `import` de `ubicacion` dentro de `registro`.
- **Pruebas:** en una copia de `HEAD` (`git archive`) en el scratchpad, para no chocar con la suite de QA, con `mvn -o`:
  - servicio-cientifico: `ArquitecturaSeguridadTest` (69) y `SeguridadEndpointsTest` (101), **0 fallos**;
  - servicio-consulta-publica: `ArquitecturaSeguridadTest` (66) y `SeguridadEndpointsTest` (76), **0 fallos**.
- **Sondas (solo en la copia, nunca en el repositorio):**
  - `Sonda18ReglaTest`: aplica `ArquitecturaSeguridadTest.reglas()` (R1 a R6) a 4 controladores nuevos;
  - `Sonda18HttpTest`: `@WebMvcTest` con el `SecurityConfig` real; llama a esos 4 controladores con un JWT **sin roles**;
  - `Sonda18ExprTest`: pasa las 8 expresiones `@PreAuthorize` de la rama de Hernando por `expresionAceptada`.
- **Artefacto:** `mvn -o package -DskipTests` en la copia. El jar de servicio-cientifico **no contiene** `sondaseguridad` ni ninguna clase `*Test`, y sí contiene `ProblemaSeguridadHandler`.

## 1. SEC-J-19: vías de elusión, una por una

| Vía | ¿Cubierta? | Evidencia |
|---|---|---|
| `@Controller` + `@ResponseBody` / `ResponseEntity` | Sí (R1, R2, R4) | Sondas propias del commit |
| Estereotipo propio meta-anotado con `@RestController` | Sí (R1: exige `@RestController` **directo**) | `ConEstereotipoPropio` |
| `@Component` + `@RequestMapping` de tipo | Sí (R2) | `ComponenteConRequestMapping` |
| Mapping heredado de clase base (con o sin rol) o de una interfaz, aunque el padre esté fuera del escaneo | Sí (R3; R2 si el padre está en el escaneo) | `HeredaMapping*`, `ImplementaInterfazConMapping`, `laClaseBaseConMappingEsDetectadaPorR2` |
| `@PreAuthorize` **solo en la clase** | Sí. R4 lo exige en cada método (más estricto, a propósito) | Lectura de R4 (`tryGetAnnotationOfType` del método) |
| `@PreAuthorize` vía **meta-anotación** propia (`@SoloAdmin`) | Sí. No cuenta como anotación directa, así que R4 falla (más estricto). Si un método lleva a la vez una directa y una meta-anotada, Spring Security 6 rechaza la duplicidad al invocar (falla cerrado; no se comprobó con sonda) | Lectura de R4 |
| `@Secured` / `@RolesAllowed` en lugar de `@PreAuthorize` | Sí. No están habilitados (`@EnableMethodSecurity` por defecto) y R4 exige `@PreAuthorize`, así que el método falla la regla | `SecurityConfig.java:20` |
| SpEL con `or` y algo siempre verdadero (`permitAll()`, `isAuthenticated()`, `true`, `1 == 1`, `!hasRole`, paréntesis, `;`) | Sí. La lista blanca exige que **toda** la expresión sean términos de rol con roles del realm; no hay ninguna combinación de términos aceptados que sea siempre verdadera | 30 casos rechazados en `laListaBlancaRechazaExpresionesSinRolExplicito` |
| `@politicaUbicacion` con otro método u otro bean | Sí (lista `METODOS_POLITICA_UBICACION`) | `PoliticaNoListada` |
| `RouterFunction`, también oculto tras `Object` | Sí (R5, por dependencia de paquete) | `RutasFuncionales*` |
| `HttpRequestHandler`, `mvc.Controller`, `Servlet`, `ServletRegistrationBean` | Sí (R6) | `ManejadorHttpRequestHandler`, `ServletPropio` |
| Controladores fuera del paquete escaneado | Sí en la práctica. Spring escanea desde `ServicioCientificoApplication` (`...biohub.cientifico`), el mismo paquete que importa la regla. Un `@ComponentScan`/`@Import` de otro paquete, o un controlador que venga en una dependencia, no los ve la regla; queda el `denyAll` fuera de `/api/cientifico/**` | Ver SEC-J-I22 |
| **`@GetExchange` / `@HttpExchange` en un `@RestController` (Spring 6.1+)** | **No** | SEC-J-22 |
| **Método de endpoint `final`** (con `@PreAuthorize` válido) | **No** | SEC-J-22 |
| **Método de endpoint `static`** (con `@PreAuthorize` válido) | **No** | SEC-J-22 |

- **Sondas fuera de producción:** cumplido. Las dos `SondasElusion` están en `src/test/java/co/edu/unbosque/biohub/sondaseguridad/`, fuera del paquete base de cada servicio. Por eso no las ve el escaneo de componentes ni de `@SpringBootApplication` ni de `@WebMvcTest`, y la regla las excluye con `DO_NOT_INCLUDE_TESTS`. Ningún `pom.xml` usa `test-jar`, `build-helper` ni `testSourceDirectory`. El `Dockerfile` copia solo el jar empaquetado, y se comprobó que el jar no las incluye.
- **Prueba de control positiva:** `unControladorCorrectoPasaTodasLasReglas` evita que las reglas rechacen todo, y `elEscaneoNoEstaVacio` evita que el escaneo quede vacío sin que nadie lo note. Bien.
- **Controladores de registro (Hernando):** los 3 controladores (8 métodos) son `@RestController` directos, con métodos `public` no `final` y sin herencia. Sus 8 expresiones `hasAnyRole(...)` son **aceptadas** por la lista blanca (`Sonda18ExprTest`: 8 de 8 `true`), así que la regla los deja pasar sin cambios. Pero la regla solo valida la **forma** de la expresión, no la matriz §2 (ver SEC-J-23).

## 2. SEC-J-20: `ProblemaSeguridadHandler`

- **Sin filtración de información:**
  - el cuerpo del 403 es fijo (`ProblemaSeguridadHandler.java:49-53,70-84`): no incluye el mensaje de la excepción, el rol exigido, el usuario ni una traza;
  - `instance` es `getRequestURI()`, serializado por Jackson como `application/problem+json`: no hay XSS reflejado, y `StrictHttpFirewall` ya rechaza `;`, `%2F` y `%25`;
  - la prueba `elCuerpoDel403NoFiltraTrazasNiDetallesInternos` lo cubre.
- **Cabeceras:**
  - el 401 (`:56-68`) solo emite `Bearer` y, si aplica, `error="<código RFC 6750>"`, filtrado por la lista `invalid_request|invalid_token|insufficient_scope`. Se elimina el `error_description`, que antes reflejaba el mensaje del decodificador JWT. Probado con `BadJwtException("... interno-7")`;
  - el 403 ya no envía el `WWW-Authenticate` con descripción que ponía `BearerTokenAccessDeniedHandler`.
- **`no-store`:** se pone en el 401 y en el 403 (`:67,80`), y lo comprueba `esRolInsuficiente`/`esNoAutenticado` en todos los endpoints, en el `denyAll` y en el JWT inválido.
- **Registro en la cadena:** el handler se registra en `exceptionHandling` **y** en `oauth2ResourceServer` (`SecurityConfig.java:42-45`). Esto cubre el JWT inválido o vencido (que no pasa por `ExceptionTranslationFilter`) y el `denyAll` (`AuthorizationFilter`). Un anónimo que llega a `denyAll` recibe 401, no 403 (`ExceptionTranslationFilter`).
- **Auditoría de `ubicacion` intacta:**
  - `UbicacionExceptionHandler` no cambió (`git show --stat e49373a`). Sigue con `@Order(HIGHEST_PRECEDENCE)` y `basePackages = ...ubicacion.controller`, y resuelve y audita la `AccessDeniedException` **antes** de que llegue a Spring Security;
  - como `/api/cientifico/**` es `authenticated()`, un usuario autenticado nunca recibe un 403 de la cadena de filtros en `ubicacion`: todo 403 de `ubicacion` pasa por el método y queda auditado;
  - en las pruebas, `ProblemaSeguridadHandler` produce el mismo cuerpo, así que el contrato es uniforme.
- **`ConsultaPublicaExceptionHandler.seguridad`** (`:61`): relanzar la misma excepción hace que `ExceptionHandlerExceptionResolver` devuelva `null` sin registrar un aviso. La excepción llega a `ExceptionTranslationFilter`, en vez de acabar como 500 en el `@ExceptionHandler(Exception.class)` genérico. Correcto.
- **Registro (Hernando):** `RegistroExceptionHandler` no captura `AccessDeniedException` ni `Exception`, así que sus 403 saldrán por `ProblemaSeguridadHandler` con el formato del contrato. Riesgo futuro en SEC-J-I20.

**Estado: Cerrado.**

## 3. Condiciones de D55 antes de integrar `registro`

| Condición (iteración 17) | Estado |
|---|---|
| SEC-J-19: endurecer `ArquitecturaSeguridadTest` | **Parcial**: falta SEC-J-22 (S, dos reglas más) |
| SEC-J-20: 403 uniforme | **Cumplida** |
| SEC-J-I19: red interna sin puertos publicados (Santiago) | Sin cambios; sigue pendiente para el compose del backend |
| Nueva: los endpoints de registro se añaden a `ENDPOINTS` de `SeguridadEndpointsTest` con su matriz | **Pendiente (Hernando)**. Es la única prueba que compara el rol con la matriz §2; la regla ArchUnit no lo hace (SEC-J-23) |

Los 3 controladores actuales de Hernando **no** usan ninguna de las vías de SEC-J-22, así que hoy no hay ningún endpoint expuesto por ellas. Por eso no exijo pasar a `hasAnyRole` en la URL. La condición queda así: **SEC-J-22 corregido antes de que `registro` se fusione en `develop`** (si se puede, en este mismo PR; el esfuerzo es S). Si `registro` entra antes, la alternativa de D55 (`hasAnyRole` de los 5 roles en la URL) pasa a ser obligatoria hasta corregirlo.

Sobre D53 (`registro` importa `ubicacion.service.EstadoConservacion`, `UbicacionInvalidaException` y `UbicacionNoEncontradaException`): son tipos de la capa de servicio. No hay `import` de `ubicacion.domain` ni de `ubicacion.infrastructure` (texto cifrado, llaves), así que **no afecta a la seguridad** de las coordenadas. Es un tema de arquitectura.

## Hallazgos nuevos

### SEC-J-22 · Bajo · A01 (quedan tres vías que pasan `ArquitecturaSeguridadTest` y dejan el endpoint sin control de rol)
- **Archivos:** `servicio-cientifico/src/test/java/co/edu/unbosque/biohub/cientifico/ArquitecturaSeguridadTest.java:107-109` (`ES_ENDPOINT`) y `:161-178` (R4), y lo mismo en `servicio-consulta-publica/.../ArquitecturaSeguridadTest.java:104-106,158-175`.
- **Problema (comprobado en la copia):** las 4 sondas pasan **las seis reglas** (R1 a R6) y responden **200 con el dato** a un JWT **sin roles**, con el `SecurityConfig` real:

  | Sonda | Reglas | `GET` con JWT sin roles |
  |---|---|---|
  | `@RestController` con `@GetExchange("/api/cientifico/...")` y sin `@PreAuthorize` | todas pasan | **200** |
  | `@RestController` que implementa una interfaz `@HttpExchange` con `@GetExchange` | todas pasan | **200** |
  | `@GetMapping` + `@PreAuthorize("hasRole('administrador')")` en un método **`final`** que llama a un método auxiliar del propio controlador | todas pasan | **200** |
  | `@GetMapping` + `@PreAuthorize("hasRole('administrador')")` en un método **`static`** | todas pasan | **200** |

  - **`@HttpExchange`:** desde Spring Framework 6.1 (el proyecto usa 6.2.x, Boot 3.5.16), `RequestMappingHandlerMapping` también registra como endpoints de servidor los métodos con `@HttpExchange`/`@GetExchange`/`@PostExchange`. Esas anotaciones **no** llevan `@RequestMapping` como meta-anotación, así que `ES_ENDPOINT` no las ve: R2, R3 y R4 no se aplican.
  - **`final` / `static`:** `@PreAuthorize` se aplica con un proxy CGLIB, y CGLIB no puede sobrescribir métodos `final` ni `static`. Spring MVC invoca el método sin pasar por el interceptor. En el caso `final`, el cuerpo corre sobre la instancia del proxy, y cualquier llamada a otro método no `final` se delega al bean real, con sus dependencias. Así, el endpoint devuelve datos reales. El `@PreAuthorize` **está escrito y la regla lo acepta**, pero no se ejecuta, lo que lo hace más difícil de detectar en una revisión.
- **Por qué importa:** con D55, el `@PreAuthorize` es la única barrera de rol dentro del servicio, y esta prueba es su red de seguridad. Cualquiera de las 3 vías dejaría un endpoint restringido (por ejemplo, el historial o la reclasificación) abierto a **cualquiera de los 5 roles** que dejó pasar el gateway, sin que falle ninguna prueba. Hoy no hay ningún endpoint afectado (tampoco en la rama de Hernando).
- **Corrección:** ampliar `ES_ENDPOINT` y añadir una regla sobre los modificadores, más una sonda por vía:
  ```java
  private static final DescribedPredicate<CanBeAnnotated> ES_ENDPOINT =
          CanBeAnnotated.Predicates.metaAnnotatedWith(RequestMapping.class)
                  .or(CanBeAnnotated.Predicates.annotatedWith(RequestMapping.class))
                  .or(CanBeAnnotated.Predicates.metaAnnotatedWith(HttpExchange.class))
                  .or(CanBeAnnotated.Predicates.annotatedWith(HttpExchange.class));

  // R7: el proxy de @PreAuthorize solo intercepta métodos de instancia no final; la clase tampoco puede ser final
  static ArchRule r7EndpointsInterceptables() {
      return methods().that(ES_ENDPOINT)
              .should().bePublic()
              .andShould().notHaveModifier(JavaModifier.FINAL)
              .andShould().notHaveModifier(JavaModifier.STATIC)
              .andShould().beDeclaredInClassesThat().doNotHaveModifier(JavaModifier.FINAL)
              .because("un método final o static no pasa por el interceptor de @PreAuthorize (SEC-J-22)")
              .allowEmptyShould(true);
  }
  ```
  Y en R3 (herencia), el mismo `ES_ENDPOINT` ampliado cubre la interfaz `@HttpExchange`. Para cerrar a la vez esta clase de errores, se puede añadir en `SeguridadEndpointsTest` un caso que recorra **todos** los `RequestMappingInfo` de `RequestMappingHandlerMapping` y compruebe que cada ruta de `/api/cientifico/**` responde 403 con un JWT sin roles. Así se prueba el comportamiento real, no la forma del código, y aparecería cualquier vía que la regla aún no conozca.
- **Responsable sugerido:** ubicación (Juanita); avisar a Hernando. **Condición de D55** antes de fusionar `registro` en `develop`. **Esfuerzo:** S.

### SEC-J-23 · Medio · A01 (rama de Hernando: los `@PreAuthorize` de `registro` no siguen la matriz §2; la regla los acepta)
- **Archivos** (en `origin/feature/registro-cientifico`, `0137053`), en `servicio-cientifico/.../registro/controller/`:
  - `HallazgoController.java:48` (`POST /hallazgos`: `investigador_campo, cientifico_alto_rango, administrador`) y `:66` (`PUT /hallazgos/{id}`: `cientifico_alto_rango, administrador`);
  - `FichaPlantillaController.java:37` (`PUT /fichas/{n}/atributos`: `cientifico_alto_rango, administrador`);
  - `HistorialController.java:34,41,53` (`versiones`, `auditoria`, `comparar`: `auditor_cientifico, cientifico_alto_rango, administrador`).
- **Problema:** la matriz §2 de `11-seguridad-y-acceso.md` dice:
  - registrar hallazgo y ficha: investigador y alto rango;
  - reclasificar: investigador, auditor y alto rango;
  - ver historial: auditor y alto rango;
  - y "`administrador` nunca recibe coordenadas (RF12) **ni edita datos científicos**".

  En la rama, `administrador` puede **registrar, reclasificar y editar fichas**, y ver el historial. Además, `investigador_campo` y `auditor_cientifico` no pueden reclasificar, aunque la matriz se lo permite (esto es funcional, no de seguridad). `ArquitecturaSeguridadTest` acepta todas estas expresiones porque solo valida que nombren roles del realm. Las rutas de registro están marcadas como propuestas (`*`) en la matriz, pero la regla del administrador no lo está.
- **Por qué importa:** con D55, ese `@PreAuthorize` es el **único** control de rol. Un administrador (cuenta técnica de gestión) podría crear o modificar datos científicos y su historial inmutable, lo que rompe la separación de funciones que fija la matriz. No es un fallo de este diff, pero es justo el caso que la red de D55 no detecta.
- **Corrección:** ajustar las expresiones a la matriz, o actualizar la matriz con el Tech Lead si el cambio es intencional, y añadir los 8 endpoints a `ENDPOINTS` de `SeguridadEndpointsTest` con sus roles permitidos, para que la matriz quede probada:
  ```java
  // HallazgoController
  @PostMapping
  @PreAuthorize("hasAnyRole('investigador_campo', 'cientifico_alto_rango')")
  // HistorialController
  @GetMapping("/versiones")
  @PreAuthorize("hasAnyRole('auditor_cientifico', 'cientifico_alto_rango')")
  // SeguridadEndpointsTest.ENDPOINTS
  new Endpoint(HttpMethod.POST, "/api/cientifico/hallazgos", Set.of(INVESTIGADOR, ALTO_RANGO)),
  ```
- **Responsable sugerido:** Hernando (registro), con validación del Tech Lead sobre la matriz. **No bloquea** el PR de esta rama. **Bloquea** la integración de `registro` hasta que se corrija o se documente. **Esfuerzo:** S.

### Informativos
- **SEC-J-I20 (manejadores genéricos y 403 futuros):** si un `@RestControllerAdvice` de `registro` (u otro módulo) añade `@ExceptionHandler(Exception.class)` o `RuntimeException.class` sin excluir `AccessDeniedException`, sus 403 se convertirían en 500. Es lo que ya pasó en consulta-pública y se corrigió en `e49373a`. Recomendación para Hernando: copiar el patrón `seguridad(RuntimeException e) { throw e; }` de `ConsultaPublicaExceptionHandler.java:60-63`, o tratar `AccessDeniedException` antes del genérico. `todoEndpointDa403ProblemJsonConRolNoPermitido` lo detecta si los endpoints de registro se añaden a `ENDPOINTS`.
- **SEC-J-I21 (`PUBLICOS_PERMIT_ALL` por nombre simple):** `servicio-consulta-publica/.../ArquitecturaSeguridadTest.java:90,165` identifica los públicos como `ClaseSimple.metodo`. Otra clase llamada `PingController` en otro paquete, con un método `ping`, podría usar `permitAll()`. El riesgo es nulo mientras exista el `denyAll` con matchers exactos en la URL. Para mayor precisión, usar `metodo.getFullName()`.
- **SEC-J-I22 (fuera del alcance de la regla):** la regla no ve controladores que vengan en dependencias ni paquetes añadidos con `@ComponentScan`/`@Import`, y tampoco comprueba que `@EnableMethodSecurity` siga con `prePostEnabled` activo. Ambos casos quedan cubiertos por el `denyAll` fuera del prefijo y por `SeguridadEndpointsTest`, que fallaría si se desactivara la seguridad por método. La prueba de comportamiento que se propone en SEC-J-22 cerraría también este hueco.

## Bien hecho (esta iteración)
- La lista blanca de expresiones es la solución correcta: exige que **toda** la expresión sean términos de rol del realm, en vez de buscar palabras prohibidas. Rechaza las 30 variantes probadas, incluidas `!rol`, paréntesis, `;`, comillas dobles y roles con otra capitalización.
- Las sondas viven en `src/test`, fuera del paquete escaneado. Hay una por vía, con control positivo y comprobación de que el escaneo no está vacío. No llegan al jar.
- `ProblemaSeguridadHandler` registrado en los dos puntos de la cadena (resource server y `exceptionHandling`), con el cuerpo fijo del contrato y `no-store`. Además, quita del `WWW-Authenticate` la descripción interna del decodificador, una filtración que no estaba en el alcance de SEC-J-20.
- La corrección de consulta-pública (`AccessDeniedException` como 500) se detectó y se arregló en el mismo commit.
- Pruebas parametrizadas de 401 y 403 sobre todos los endpoints y sobre el `denyAll`.

## Veredicto (iteración 18)
**APROBADO CON OBSERVACIONES.** No hay Críticos ni Altos en este diff.
- **SEC-J-20: Cerrado.**
- **SEC-J-19: Parcial.** Lo que quedó abierto se sigue como **SEC-J-22** (Bajo, S). Debe corregirse antes de que `registro` se fusione en `develop`; si no, `hasAnyRole` en la URL pasa a ser obligatorio (alternativa de D55).
- **SEC-J-23** (Medio, rama de Hernando): no bloquea este PR, pero sí la integración de `registro`. Hay que alinear los `@PreAuthorize` con la matriz §2 (el administrador no edita datos científicos) y añadir los endpoints a `SeguridadEndpointsTest`.
- **D55** sigue aceptada con las condiciones de la sección 3.

---

## Iteración 18 – re-verificación de SEC-J-22 (2026-11-09)

**Revisor:** ciberseguridad · **Rama:** `feature/ubicacion-consulta-publica` (HEAD `89e280e`) · **Alcance:** `git show 99fdf15` (SEC-J-22, solo `src/test`) y `git show 89e280e` (BUG-J18-1, solo la comprobación del `user_id` falsificado). Estado de SEC-J-I22 y de las condiciones de D55 para integrar `registro`.

### Resumen

| ID | Estado |
|---|---|
| **SEC-J-22** | **Cerrado.** Las 3 vías (`@HttpExchange`/`@GetExchange`, método `final`, método `static`) fallan el build por dos caminos independientes: la regla de forma (R7 y `ES_ENDPOINT` ampliado) y la prueba de comportamiento |
| **SEC-J-19** | **Cerrado** (lo que quedaba abierto era SEC-J-22) |
| **SEC-J-I22** | **Cerrado en lo esencial; queda un residuo** (`prePostEnabled=false` y controladores sin proxy: cerrado; controladores de fuera del paquete base con argumentos obligatorios: ver SEC-J-I23) |
| BUG-J18-1 | Correcto. La prueba es **más estricta** que antes, no más débil |

Hallazgos nuevos: **0 Críticos, 0 Altos, 0 Medios, 0 Bajos, 1 Informativo (SEC-J-I23).**

### Cómo se revisó
- **Lectura:** los dos `ArquitecturaSeguridadTest` (diff), `VerificadorDeEndpoints`, `MocksDeDependencias` y `SondasElusion` (idénticos en los dos servicios, comprobado con `diff`), los dos `TodosLosEndpointsExigenRolTest`, `SondasDeComportamientoTest`, `PoliticaAccesoUbicacion` y `PublicadorNoPermitidoIntegracionTest` (diff).
- **Pruebas en una copia de `HEAD`** (`git archive`, en el scratchpad; nada en el repositorio), `mvn -o -pl servicio-cientifico test -Dtest='ArquitecturaSeguridadTest,TodosLosEndpointsExigenRolTest,SondasDeComportamientoTest'`: 79 + 5 + 9 pruebas, **0 fallos**.
- **Mutaciones (solo en la copia):**
  - controlador del paquete de la aplicación, **sin** `@PreAuthorize`, con un `@RequestParam` obligatorio (la petición de relleno da **400**, que es "no concluyente"): la prueba **falla** por la invocación directa ("el bean no tiene proxy de seguridad") y por `laSeguridadPorMetodoSigueActiva`. El 400 queda compensado;
  - controlador en `com.terceros` (fuera de `co.edu.unbosque.biohub`), traído con `@Import` y mapeado en `/api/cientifico/externo`, sin control: con el parámetro **opcional**, la prueba HTTP lo marca (`respondió 200 a un JWT sin roles`); con el parámetro **obligatorio**, **pasa sin ser detectado** (SEC-J-I23).
- **Artefacto:** `mvn -o package -DskipTests` en la copia. Los jar de servicio-cientifico y servicio-consulta-publica tienen **0** entradas de `sondaseguridad`, `VerificadorDeEndpoints`, `MocksDeDependencias` ni `*Test.class`. Ningún `pom.xml` usa `test-jar`, `build-helper`, `testSourceDirectory` ni configura `maven-jar-plugin`.

### 1. Las tres vías de SEC-J-22

| Vía | Regla de forma | Prueba de comportamiento |
|---|---|---|
| `@GetExchange` / `@HttpExchange` sin rol, también desde una interfaz | R2/R3/R4, por `ES_ENDPOINT` ampliado (`ArquitecturaSeguridadTest.java:119-123`). Sondas `ConGetExchange`, `ConHttpExchange`, `ApiExchange`, `ImplementaInterfazHttpExchange` | HTTP 200 → violación; invocación sin `AccessDeniedException` → violación (`SondasDeComportamientoTest:70-85`) |
| Método `final` con `@PreAuthorize` válido | R7 (`:211-221`), sonda `MetodoFinal` | La invocación corre el cuerpo sobre el proxy y no lanza `AccessDeniedException` → violación "es final" (`:87-91`) |
| Método `static` con `@PreAuthorize` válido | R7, sonda `MetodoStatic` | Invocación con destino `null` → violación "es static" (`:93-97`) |
| Extra: método no público y clase `final` | R7, sondas `MetodoNoPublico` y `ClaseFinal` | — |

Hay control positivo en los dos niveles: `ControladorCorrecto` y `ConGetExchangeYRol` pasan las 7 reglas y la prueba de comportamiento no los marca. Así se evita que las reglas rechacen todo o que la prueba no detecte nada. `elRecorridoNoEstaVacio` y `lasSondasEstanRegistradas` evitan un recorrido vacío. La R7 aplica exactamente lo que propuse en la iteración 18.

### 2. ¿Aceptar 400/406/415 como "no concluyente" abre un hueco?

**Para los endpoints de la aplicación, no.** `invocacionDirecta` (`VerificadorDeEndpoints.java:154-185`) no pasa por la resolución de argumentos de Spring MVC. Toma el mismo bean (el proxy) y el mismo `Method` que usa el `HandlerMethod`, pone un JWT sin roles en el `SecurityContext` y exige **exactamente** `AccessDeniedException`. Cualquier otro resultado es una violación: que el método se ejecute, cualquier otra excepción o que el bean no tenga proxy. Por eso, todo endpoint que por HTTP diera 400/406/415 queda cubierto por la segunda comprobación. Lo comprobé con la primera mutación. El caso "el cuerpo del método lanza su propia `AccessDeniedException`" no es un hueco: un usuario sin rol también sería rechazado en la petición real. Los roles válidos que no corresponden siguen siendo cosa de R4 y de `SeguridadEndpointsTest` (SEC-J-23).

**Para los endpoints de fuera del paquete base, sí queda un residuo pequeño.** La invocación se filtra con `esDeLaAplicacion()` (`:76-78`; `TodosLosEndpointsExigenRolTest.java:126`), así que un mapping de otro paquete solo pasa por HTTP, donde el 400 se tolera. Ver SEC-J-I23.

En consulta-pública, `soloLasRutasPublicasExplicitasEstanAbiertas` compara el **conjunto de ids** de los mappings públicos con `PUBLICOS_PERMIT_ALL`. Así, un segundo handler en la misma ruta pública (por ejemplo, con una condición `params`/`headers`, que `rutas()` no distingue) cambia el conjunto y hace fallar la prueba. No hay hueco por `esPublico`. SEC-J-I21 (comparación por nombre simple) sigue igual.

### 3. ¿Puede `MocksDeDependencias` ocultar un control de seguridad?

**No, en el diseño actual.**
- Solo registra un mock para un tipo de la aplicación que aparece **en un constructor** y **no tiene bean** (`MocksDeDependencias.java:35-41`). En servicio-cientifico, `PoliticaAccesoUbicacion` se importa **real** (`TodosLosEndpointsExigenRolTest.java:88`) y ya está registrada cuando corre el post-procesador (`ConfigurationClassPostProcessor` es `PriorityOrdered`), así que no se simula.
- Aunque un día se quitara ese `@Import` y un controlador inyectara la política por constructor, el mock se registra con el nombre `mock#<clase>`, **nunca** como `politicaUbicacion`. El `@politicaUbicacion` del SpEL no lo resolvería y lanzaría una excepción que no es `AccessDeniedException`, lo que da violación. Es decir, **falla cerrado**.
- La lista blanca de R4 solo admite `hasRole`/`hasAnyRole` y `@politicaUbicacion` con métodos listados, así que no hay otro bean en el SpEL que un mock pudiera suplantar.
- Un mock de Mockito no lanza `AccessDeniedException` por defecto. Si un control vive en el **cuerpo** del método (un servicio simulado que debería denegar), el mock lo hace más permisivo: la prueba falla, no lo oculta.
- Recomendación (no es hallazgo): si se añade otro bean a la lista blanca de SpEL, importarlo real y comprobar `Mockito.mockingDetails(ctx.getBean("<nombre>")).isMock() == false`.

### 4. BUG-J18-1 (`elBrokerRechazaUnUserIdFalsificado`)

No debilita la comprobación; la **refuerza**. Antes bastaba con que **cualquier** operación lanzara algo (`assertThatThrownBy(...).isNotNull()`), incluido un fallo de autenticación o de permisos que nada tiene que ver con el `user_id`. Ahora exige:
- que el broker cierre **el canal**, no la conexión (`isHardError() == false`);
- motivo `Channel.Close` con código **406 PRECONDITION_FAILED** y un texto que contiene `user_id`, así que un 403 por permisos haría fallar la prueba;
- que no se haya creado ninguna ficha (`fichas() == 0`).

La espera tiene un tope de 15 s y no reintenta. Si el broker aceptara el mensaje, la prueba falla por tiempo. El `user_id` falsificado es el del publicador legítimo (`Contenedores.usuarioAmqp()`). No ejecuté esta prueba (necesita contenedores); me baso en la lectura y en el verify en verde que reporta el backend.

### 5. SEC-J-I22

- **`@EnableMethodSecurity` desactivado o `prePostEnabled=false`:** cerrado. `laSeguridadPorMetodoSigueActiva` y `invocacionDirecta` exigen el proxy. Aunque otro aspecto (`@Transactional`, `@Validated`) creara el proxy, la invocación ejecutaría el método y no lanzaría `AccessDeniedException`, así que también falla.
- **Controladores de dependencias o de paquetes añadidos con `@Import`/`@ComponentScan`:** la regla ArchUnit sigue sin verlos (importa solo el paquete base). La prueba de comportamiento sí los recorre (todos los `RequestMappingHandlerMapping`, más `otrasVias`), pero solo por HTTP. Los detecta si responden algo distinto de 403/400/406/415, **no** si exigen un argumento que el relleno no aporta. Este es el límite anotado por el backend, y se sigue como SEC-J-I23.

### Hallazgo nuevo

#### SEC-J-I23 · Informativo · A01 (un endpoint de fuera del paquete base con argumentos obligatorios pasa la prueba de comportamiento)
- **Archivos:** `servicio-*/src/test/java/co/edu/unbosque/biohub/sondaseguridad/VerificadorDeEndpoints.java:64,205` (`NO_CONCLUYENTES` se aplica a todos los mappings) y `TodosLosEndpointsExigenRolTest.java:126` (la invocación directa filtra con `esDeLaAplicacion`).
- **Problema (comprobado en la copia):** un `@RestController` en `com.terceros`, traído con `@Import` y mapeado en `GET /api/cientifico/externo` con `@RequestParam` obligatorio y sin control, responde 400 al relleno y no se invoca directamente. **Las 5 pruebas pasan.** Con un usuario sin rol y el parámetro puesto, respondería 200. ArchUnit tampoco lo ve.
- **Riesgo:** bajo. Exige introducir a propósito un controlador de otro paquete bajo `/api/cientifico/**` (o `/api/publica/**` fuera de las 3 rutas). Fuera del prefijo sigue el `denyAll`. Lo dejo como informativo porque es la red de D55 y es barato cerrarlo.
- **Corrección:** fuera de la aplicación, no tolerar respuestas no concluyentes. Opcionalmente, fijar una lista cerrada de handlers del framework:
  ```java
  // VerificadorDeEndpoints.peticionHttp
  boolean tolerable = endpoint.esDeLaAplicacion() && NO_CONCLUYENTES.contains(estado);
  if (estado != 403 && !tolerable) { violaciones.add(...); }

  // TodosLosEndpointsExigenRolTest: ningún handler ajeno salvo los del framework conocidos
  assertThat(VerificadorDeEndpoints.registrados(ctx).stream().filter(e -> !e.esDeLaAplicacion())
          .map(e -> e.metodo().getBeanType().getName()).distinct())
      .allMatch(n -> n.equals("org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController"));
  ```
- **Responsable sugerido:** ubicación (Juanita). **No bloquea.** **Esfuerzo:** XS.

### Condiciones de D55 para integrar `registro`

| Condición | Estado |
|---|---|
| SEC-J-19 / SEC-J-22: red de seguridad de `@PreAuthorize` | **Cumplida** |
| SEC-J-20: 403 uniforme | Cumplida (iteración 18) |
| SEC-J-I19: red interna sin puertos publicados (Santiago) | Sin cambios; pendiente para el compose del backend (no depende de esta rama) |
| Endpoints de registro en `ENDPOINTS` de `SeguridadEndpointsTest` con su matriz, y SEC-J-23 | **Pendiente (Hernando)**. `TodosLosEndpointsExigenRolTest` ya cubrirá sola a los usuarios sin rol en los endpoints de `registro`, sin editar la prueba. La comparación con la matriz §2 (roles válidos que no corresponden) sigue dependiendo de `ENDPOINTS` |

**La condición de D55 que dependía de esta rama queda cumplida.** No hace falta `hasAnyRole` en la URL. Para fusionar `registro` solo queda lo que es de Hernando (SEC-J-23 y sus entradas en `ENDPOINTS`).

### Veredicto (re-verificación de SEC-J-22)
**APROBADO.** No hay Críticos ni Altos.
- **SEC-J-22: Cerrado.**
- **SEC-J-19: Cerrado.**
- **SEC-J-I22:** cerrado salvo el residuo de **SEC-J-I23** (Informativo, XS, no bloquea).
- **BUG-J18-1:** correcto; la comprobación del `user_id` falsificado queda más estricta.
- **D55:** la condición de esta rama está cumplida; quedan SEC-J-23 y `ENDPOINTS` de registro (Hernando).
