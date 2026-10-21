# 07 – Reporte de QA del esqueleto

Fecha: 2026-10-21. Entorno: Compose con 7 contenedores, servicios Java desde sus jars (8080-8084), Vite en 5173/5174, Keycloak 26.8.1. No se modificó código ni configuración; solo se escribieron este reporte y `06-plan-pruebas.md`. Los IDs de casos son los del plan.

## Resumen
| Concepto | Cifra |
|---|---|
| Casos del plan | 69 |
| Pasa | 66 |
| Falla | 1 (SE-20) |
| No ejecutado | 2 (IG-10, IN-13) |
| `mvn -B package` | BUILD SUCCESS; 17 tests, 0 fallos, 0 errores (gateway 9 = 4 ProxyServiceTest + 5 GatewaySecurityTest; cientifico 2, equipos 2, consulta-publica 2, reportes 2) |
| `npm test` web-interna / web-publica | 2/2 y 2/2 (vitest 5.0.5) |
| `npm run build` ambas webs | OK (JS 248 kB y 220 kB sin comprimir) |
| `npm audit --omit=dev` | 0 vulnerabilidades en ambas |
| Cobertura de código | No disponible (no hay JaCoCo ni `vitest --coverage` configurado) |

Nota: `mvn package` reescribió los jars de `target/` mientras los servicios corrían desde ellos; tras eso los 5 `/actuator/health` siguieron en 200.

## Resultado por caso
### Unitarias / componentes
UC-01 a UC-11: **Pasa** (cifras arriba).

### Integración vía gateway
| ID | Resultado | Evidencia |
|---|---|---|
| IG-01 | Pasa | 25/25 respuestas 200; usuario y rol coinciden con el token (ej. `auditor1 -> ["auditor_cientifico"]`) |
| IG-02 | Pasa | `{"servicio":"servicio-consulta-publica","usuario":"anonimo","roles":[]...}` 200 |
| IG-03 | Pasa | Solo un rol de negocio por usuario; `fecha` ISO con Z |
| IG-04 | Pasa | 200 con query con `%20`, `%26`, `%C3%B1` y con `?` vacío |
| IG-05 | Pasa | POST JSON a `/api/cientifico/hallazgos` -> 404 del servicio; a `/ping` -> 405 del servicio |
| IG-06 | Pasa | PUT/DELETE/POST sin cuerpo/5 MB/chunked -> 404 del servicio (con 100-continue en el de 5 MB) |
| IG-07 | Pasa | `/api/inexistente/ping` y `/api/` -> 404 (cuerpo vacío) |
| IG-08 | Pasa | 405 y 404 JSON del servicio llegan intactos |
| IG-09 | Pasa | 200 `{"status":"UP","groups":["liveness","readiness"]}` en gateway y servicios |
| IG-10 | **No ejecutado** | Exige detener un servicio y se pidió no detener nada. Solo cubierto en código (`GatewayExceptionHandler`) y sin prueba unitaria del 502 |
| IG-11 | Pasa | `/ping/` y `/CIENTIFICO/ping` -> 404 |
| IG-12 | Pasa (limitado) | 200; no se pudo observar qué cabecera recibe el servicio |

### Seguridad
| ID | Resultado | Evidencia |
|---|---|---|
| SE-01 | Pasa | 401 en las 4 rutas |
| SE-02, SE-03, SE-04, SE-05, SE-06 | Pasa | 401 en todos (firma, payload, `alg:none`, basura, Basic, `Bearer ` vacío) |
| SE-07 | Pasa | Token admin1 con `exp`=1792625222: 200 a las 23:23:50; a las 23:28:15 (>exp+60 s) 401 en gateway (`gateway`, `cientifico`, `publica`) y directo a 8081 y 8083 |
| SE-08 | Pasa | Token `master`/`admin-cli`: 401 en las 5 rutas y en 8081 directo |
| SE-09 | Pasa | Token `admin-cli` del realm biohub (`aud` ausente): 401 en gateway y 8081; la validación de audiencia funciona |
| SE-10 | Pasa | `/api/publica/ping` con token basura o con firma mala -> 401 (no cae a anónimo) |
| SE-11 | Pasa | POST/PUT/PATCH/DELETE sin token -> 401 |
| SE-12 | Pasa | POST con token -> 405 |
| SE-13 | Pasa | HEAD sin token -> 401 (ver observación O-3) |
| SE-14 | Pasa | `Access-Control-Allow-Origin` igual al origen para 5173 y 5174 |
| SE-15 | Pasa | 403 "Invalid CORS request" para `evil.example`, `null`, `127.0.0.1:5173` (también sin token y en `/api/publica`) |
| SE-16 | Pasa | Preflight permitido 200 con Allow-Methods/Max-Age 3600; origen malo y `x-evil` -> 403 |
| SE-17 | Pasa | 8081, 8082, 8084: sin token 401, firma mala 401, token válido 200 |
| SE-18 | Pasa | 8083: sin token 200 anónimo; `/api/otra` sin token 401; firma mala 401 |
| SE-19 | Pasa | 401 |
| SE-20 | **Falla** | Ver BUG-01 |
| SE-21 | Pasa | `%zz`, `%00`, URL de 9000 caracteres -> 400 (HTML de Tomcat, ver BUG-02), sin 5xx |
| SE-22 | Pasa | `ñ` codificada 404; `;jsessionid` 401; `//api/...` 401; `/api//cientifico/ping` 401; sin 5xx |
| SE-23 | Pasa | `/actuator/env`: 401 sin token; 404 con token (no expuesto) |
| SE-24 | Pasa | `web-interna` password grant: 400 `unauthorized_client` |
| SE-25 | Pasa | `invalid_grant`; `client_credentials`: 401 `unauthorized_client` |
| SE-26 | Pasa | `.env` modo 600; `.env` en `.gitignore` |

### Infraestructura
| ID | Resultado | Evidencia |
|---|---|---|
| IN-01 | Pasa | 7/7 `healthy` (keycloak reporta "Up 9 minutes" frente a 14 de los demás: se reinició o recreó antes de mi sesión; sin impacto) |
| IN-02 | Pasa | version 1 `base` `success=1` en los 5 esquemas |
| IN-03 | Pasa | transaccional: cientifico+equipos; lectura: consulta_publica; analitica: reportes; gateway: gateway |
| IN-04 | Pasa | `cientifico_app` -> `equipos`: ERROR 1142 SELECT denied |
| IN-05 | Pasa | `equipos_app` -> `cientifico`: ERROR 1142 SELECT denied |
| IN-06 | Pasa | `GRANT ALL PRIVILEGES ON <su esquema>.*` y `USAGE ON *.*` |
| IN-07 | Pasa | `NOAUTH Authentication required` y `WRONGPASS` |
| IN-08 | Pasa | `PONG` |
| IN-09 | Pasa | 200 en `/api/overview` |
| IN-10 | Pasa | 401 sin y con credenciales malas |
| IN-11 | Pasa | `biohub.eventos`, topic, durable; 3 conexiones AMQP abiertas (servicios) |
| IN-12 | Pasa | Todos en 127.0.0.1 |
| IN-13 | **No ejecutado** | No existe aún endpoint que use la caché Redis desde servicio-consulta-publica |

### E2E navegador
E2E-01 a E2E-07: **Pasa**. Con investigador1 y con admin1: web-pública conectada; redirección a Keycloak con `S256`/`web-interna`; ping muestra usuario y rol correctos; sin token en storage; logout vuelve al login de Keycloak; consola sin errores.

## Bugs
Ninguno es Crítico ni Alto.

### BUG-01 (Medio) – Recorrido de directorios codificado escapa del prefijo autorizado en el gateway
- Responsable: backend (api-gateway). Archivo: `api-gateway/src/main/java/co/edu/unbosque/biohub/gateway/service/ProxyService.java:51-59` (usa `request.getRequestURI()` crudo y `build(true)` sin normalizar) y `resolverDestino` (:40-48).
- Reproducir: `GET http://localhost:8080/api/cientifico/%2e%2e/%2e%2e/actuator/health` con token válido; y sin token `GET http://localhost:8080/api/publica/%2e%2e/%2e%2e/actuator/health`.
- Esperado: el gateway rechaza (400/404). Obtenido: 200 `{"status":"UP",...}`. El gateway autoriza y enruta según la ruta cruda, pero Tomcat del servicio decodifica y normaliza a `/actuator/health`. La ruta pública llegó sin autenticación a una ruta no pública del servicio (aquí solo `health`, que ya es público; el riesgo es futuro: reglas por prefijo/rol en el gateway o rutas públicas del servicio se pueden saltar).
- Evidencia: salida de curl indicada (200). Con `/api/cientifico/%2e%2e/%2e%2e/actuator/env` el servicio respondió 404 con `"path":"/actuator/env"`, confirmando que la ruta efectiva salió de `/api/cientifico`.
- Corrección sugerida: en `ProxyService.reenviar` rechazar con 400 si la ruta (cruda o decodificada) contiene `..`, `%2e`, `%2f`, `%5c`, `;` o `\`; o normalizar con `URI.normalize()` y re-verificar que el resultado empieza por `/api/<prefijo>/`. Añadir prueba en `ProxyServiceTest`.

### BUG-02 (Bajo) – Formato de error inconsistente
- Responsable: backend (api-gateway). Archivos: `SecurityConfig.java` (401 sin cuerpo), `ProxyService.java:54` (`notFound().build()` sin cuerpo), Tomcat.
- Reproducir: `GET /api/cientifico/%zz` con token -> 400 con HTML de Tomcat; `GET /api/inexistente/ping` -> 404 sin cuerpo mientras `/api/cientifico/noexiste` -> 404 JSON `{"timestamp",...}`; origen no permitido -> texto plano "Invalid CORS request"; 401 sin cuerpo.
- Esperado: errores en un formato único (p. ej. `application/problem+json` como el 502 del contrato). Obtenido: cuatro formatos distintos. El contrato no los define, pero la web los tratará igual.
- Corrección sugerida: `ProblemDetail` para 404 de prefijo y un `AuthenticationEntryPoint`/`AccessDeniedHandler` JSON; documentar en el contrato.

### BUG-03 (Bajo) – Cabecera `Vary` duplicada en respuestas proxeadas
- Responsable: backend. Archivo: `ProxyService.java:73` (excluye `access-control-*` pero no `Vary`).
- Reproducir: `curl -i -H "Authorization: Bearer <t>" -X POST localhost:8080/api/equipos/algo`: aparecen `Vary:` y `vary:` repetidas (3 + 3 valores).
- Corrección sugerida: no copiar `vary` del servicio, o fusionar valores.

## Observaciones (no son bugs del esqueleto)
- O-1: `admin-cli` del realm `biohub` (cliente por defecto de Keycloak) permite password grant y emite tokens para usuarios del realm. Sus tokens no tienen `aud=biohub-api`, así que la API los rechaza (SE-09), pero conviene deshabilitar `directAccessGrantsEnabled` en ese cliente o dejar solo `biohub-pruebas`. Solo desarrollo.
- O-2: El preflight CORS de `/api/publica/**` anuncia POST/PUT/PATCH/DELETE (`SecurityConfig.java:61,66`) aunque el prefijo es solo GET; el servidor igualmente devuelve 401/405. Considerar CORS más estricto por prefijo cuando se implemente la API pública.
- O-3: HEAD a `/api/publica/**` da 401 (la regla es solo GET). Aceptable por contrato; decidir si se desea soportar HEAD.
- O-4: Spring Security admite 60 s de tolerancia de reloj: un token vencido sigue siendo válido hasta `exp`+60 s.
- O-5: No hay pruebas automáticas del 502 (servicio caído) ni de timeouts del gateway; el caso IG-10 quedó sin verificar en vivo.
- O-6: No hay medición de cobertura.

## Veredicto: APROBADO CON OBSERVACIONES
El esqueleto cumple el contrato: build y tests verdes (17 Java + 4 web), matriz 5x5 de pings correcta, autenticación robusta frente a tokens manipulados, vencidos, de otro realm y sin audiencia, servicios no accesibles sin JWT, CORS correcto, infraestructura sana con Flyway aplicado, aislamiento entre esquemas, Redis con contraseña y E2E con dos usuarios. No se rechaza porque no hay defectos Críticos/Altos y el único fallo (BUG-01, Medio) no expone hoy nada sensible, pero debe corregirse antes de añadir reglas de autorización por ruta o rutas públicas con datos. Quedan sin verificar: 502 con servicio caído (IG-10) y uso de Redis desde la aplicación (IN-13).

## Seguimiento del Tech Lead
| Bug | Resolución |
|---|---|
| BUG-01 | **Falso positivo, pero se reforzó.** Reproducido con `curl -v`: sin `--path-as-is`, curl normaliza `%2e%2e` en el cliente y envía `GET /actuator/health` (público). Con `--path-as-is` el gateway respondía 401 (firewall de Spring). Aun así se agregó validación de rutas no normalizadas en `ProxyService` (400) y `HttpStatusRequestRejectedHandler(400)`. Re-verificado: 400. |
| BUG-02 | **Mejorado**: prefijo inexistente → 404 ProblemDetail; URI inválida → 400 ProblemDetail; cuerpo grande → 413 ProblemDetail. 401 sin cuerpo y 403 CORS en texto quedan como están (Bajo). |
| BUG-03 | **Corregido**: el proxy ya no copia `Vary` del servicio; ahora solo salen los 3 `Vary` distintos de CORS. |
| IG-10 (502 no ejecutado) | Ejecutado por el Tech Lead en la primera verificación: servicio-reportes detenido → 502 ProblemDetail. |

Re-verificación completa tras `docker compose down -v`: 7/7 healthy, Flyway V1 en 5 esquemas, 5 pings 200, negativos OK, E2E navegador OK con investigador1 y admin1.
Tests automatizados: Maven 31 (gateway 23: ProxyServiceTest 18 + GatewaySecurityTest 5; 2 por servicio), web-interna 2, web-publica 2.
