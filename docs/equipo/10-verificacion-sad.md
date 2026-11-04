# 10 – Verificación del código contra el SAD e impacto de Aiven y Flyway

**Fecha:** 2026-11-03 · **Rama:** `feature/ubicacion-consulta-publica` (base `e186843` + cambios en curso de devops y dba de la iteración 15) · **Autor:** arquitecto-software (tarea 15c)
**Fuentes de verdad:** `docs/entregas/SAD-BioHub.txt`, `docs/entregas/SRS-BioHub.txt` (§2.2.1, ASR1–ASR9), `docs/entregas/C4-nivel2.txt`. Decisiones de equipo: `03-arquitectura.md` (D1–D38).
**Método:** para cada táctica y patrón del SAD se buscó la clase o la configuración que la implementa. "Implementada" = existe en el código y tiene pruebas; "Parcial" = existe una parte o solo en configuración sin evidencia de cumplimiento; "Ausente" = solo documentada. Los archivos `application*.yml` y las migraciones los están cambiando devops y dba en paralelo: las líneas citadas son las del momento de la revisión.

---

## 1. Resumen

- **Estilo:** el código respeta la arquitectura basada en servicios: 4 servicios de dominio y un gateway, cada uno con su `pom`, sin código compartido entre servicios (D4), API Gateway propio con doble validación del JWT, eventos en topología broker (exchange topic `biohub.eventos`, una cola con DLQ por consumidor) y Keycloak con roles de realm. Ver §2.
- **ASR:** 1 implementado (ASR4), 1 implementado con desviaciones respecto del texto del SAD (ASR6), 4 parciales (ASR1, ASR2, ASR3, ASR9) y 3 ausentes (ASR5, ASR7, ASR8). Los tres ausentes no son de Juanita (ASR5 y ASR7 dependen de Hernando y ASR8 de Santiago).
- **Parte de Juanita:** ASR4 y ASR6 están bien resueltos en el código y probados con Testcontainers. A ASR1 le faltan el escalado horizontal con balanceador y la prueba x60. De los patrones del SAD §6 en el módulo ubicacion, el Adapter está implementado y los otros tres (Strategy, Decorator, Observer) cumplen su objetivo con otra forma (ARQ-08 a ARQ-11).
- **Aiven:** con **un** servidor MySQL compartido se rompe en la práctica el aislamiento que el SAD usa para justificar ASR2, ASR3 y ASR4 (ARQ-05). **Recomiendo dos servicios de Aiven** (§8.5). La latencia por internet cabe en los presupuestos de RNF4 y RNF5 (§8.3), salvo en el re-cifrado (ARQ-12).
- **Hallazgos:** 1 Crítico, 5 Altos, 11 Medios y 8 Bajos (§5).

---

## 2. Estilo y piezas estructurales del SAD

| Elemento del SAD | Dónde está en el código | Estado | Nota |
|---|---|---|---|
| Arquitectura basada en servicios (§4.1, §4.5): Científico, Equipos, Consulta Pública, Reportes | `servicio-cientifico`, `servicio-equipos`, `servicio-consulta-publica`, `servicio-reportes`; `pom.xml` agregador | Implementada | Sin módulo común (D4); cada servicio con su `application.yml`, su Flyway y su `SecurityConfig` |
| Capas controller/service/domain/infrastructure/config (§7.1) | Paquetes en los 5 proyectos | Parcial | Los `service` de Juanita importan DTOs de `controller` (ARQ-18) |
| Módulos registro y ubicacion en Científico (§2.4, §7.1) | `cientifico.registro.*` (solo `package-info.java`), `cientifico.ubicacion.*` | Parcial | Registro vacío (ARQ-01). Prueba de arquitectura `ModuloRegistroNoDependeDeUbicacionInternaTest` |
| API Gateway como único punto de entrada (§4.5, ASR8, ASR9) | `api-gateway/.../ProxyController.java`, `ProxyService.java`, `GatewayProperties` | Implementada (enrutamiento) | Rutas por variable (`SERVICIO_*_URL`); servicios en `127.0.0.1` (D7). API Keys y rate limit ausentes (ARQ-03) |
| Eventos en topología broker (§4.8) | `cientifico/eventos/service/PublicadorEventos.java` (publisher confirms, persistente); `consultapublica/config/RabbitConfig.java`, `reportes/config/RabbitConfig.java` (cola + DLQ propias) | Implementada (infraestructura) | Ningún productor real publica `hallazgo.*` todavía (ARQ-07) |
| Keycloak, OAuth 2.0/OIDC, PKCE, token de 5 min (§5.2, §7.2.1) | `infra/keycloak/realm-biohub.json` (`accessTokenLifespan: 300`, `bruteForceProtected: true`); `KeycloakJwtAuthenticationConverter` y `SecurityConfig` en cada servicio | Implementada | `failureFactor: 5` frente a los 3 intentos de HU-11 (ARQ-21) |
| Datos (§4.5): Científico y Equipos comparten la BD transaccional (esquemas propios); Consulta Pública, Reportes y Gateway con BD propia | `docker-compose.yml`: 4 MySQL (transaccional, lectura, analítica, gateway); `infra/mysql/transaccional/01-esquemas-y-usuarios.sh` | Implementada en local | Con Aiven depende de P-A1 (§8) |

---

## 3. Tabla de ASR

| ASR | Táctica del SAD | Dónde está en el código | Estado | Responsable |
|---|---|---|---|---|
| **ASR1** (RNF1, pico x60, ≥ 95 % de éxito) | Servicio aparte que escala solo (§4.7); bulkhead (§5.1); redundancia y escalado horizontal tras NGINX (§5.1, §5.3); degradación controlada (§5.1); caché Redis (§5.1); rate limiting (§5.1) | `servicio-consulta-publica` aislado; `ConsultaFichasService` (`@Cacheable(sync = true)`); `CacheTolerante`/`CacheManagerTolerante`; `management.health.redis.enabled=false`; `ValidadorBusqueda.java:78` + `application.yml:99` (modo degradado por variable) | **Parcial**: no hay balanceador ni réplicas, la degradación es manual y no hay prueba x60 (ARQ-06); falta el rate limit por IP (ARQ-03) | **Juanita** (escalado, k6, degradación); Santiago (rate limit) |
| **ASR2** (RNF2, registro ≤ 5 s durante el pico) | Servicio Científico con recursos reservados y base de escritura propia (§4.7); bulkhead (§5.1) | `servicio-cientifico` separado; BD `cientifico` en `mysql-transaccional` (local) | **Parcial**: el registro no existe (ARQ-01); no hay límites de CPU y memoria (ARQ-24); con un solo servidor de Aiven la base de escritura comparte el servidor con la consulta pública (ARQ-05) | Hernando (registro); Santiago/devops (recursos); Juanita + tech lead (P-A1) |
| **ASR3** (RNF3, reporte sin degradar más del 10 %) | Procesamiento asíncrono con colas y base analítica propia (§4.7, §5.1, SD-08) | `servicio-reportes`: `RabbitConfig` (cola `reportes.eventos` + DLQ), `ConsumidorEventos` solo para eventos de prueba; BD `reportes` con `V1__base.sql` vacía | **Parcial**: hay infraestructura, pero no hay reportes, flujo 202 ni modelo analítico (ARQ-04) | Nicolás |
| **ASR4** (RNF5, consulta ≤ 2 s con carga científica) | CQRS: copia de lectura alimentada por eventos (§4.7, §5.1); caché Redis (§5.1); Decorator de caché y Observer (§6.5) | `ConsumidorEventos` → `ProcesadorHallazgos` → `ProyectorFichas` + `EscrituraFichasJdbc` ("gana la versión mayor"); `BusquedaFichasJdbc` (FULLTEXT); `InvalidadorCacheFichas` (después del commit); `CacheConfig` (TTL 5 min / 60 s); Hikari `connection-timeout: 2000`; Redis 300 ms | **Implementada** (pruebas de integración: `ConsumidorHallazgosIntegracionTest`, `CacheRedisIntegracionTest`, `RedisCaidoIntegracionTest`, `BusquedaFichasIntegracionTest`). Queda en riesgo con un servidor Aiven compartido (ARQ-05) | **Juanita** |
| **ASR5** (RNF8, campo personalizado < 2 min) | Columna JSON en MySQL y Fábrica de Atributos (§4.7, SD-09) | No existe | **Ausente** (ARQ-02) | Hernando |
| **ASR6** (RNF9, coordenadas cifradas) | AES-256 antes de persistir; llaves en un almacén separado de la BD (JKS) (§4.7, §5.2); las coordenadas no salen del servicio (§4.8, §5.2); auditoría de accesos (§5.2); Adapter, Decorator y Strategy (§6) | `CifradorAesGcm` (AES-256-GCM, nonce de 12 B por cifrado, AAD = UUID); `ProveedorDeLlavesEntorno` (llaves versionadas por variable, el servicio no arranca sin llave válida); `UbicacionHallazgo` sin columnas en claro (`V20261026_1010__ubicacion_hallazgo.sql`); `PoliticaAccesoUbicacion` + `ServicioDescifrado` (doble barrera); `ValidadorDatosHallazgo` (rechaza eventos con claves de coordenadas); `AuditoriaAccesoUbicacion` (log dedicado); `RecifradoService` (rotación) | **Implementada con desviaciones**: llaves en variables y no en JKS (ARQ-11); patrones con otra forma (ARQ-08 a ARQ-10); SEC-J-01/02/03 abiertos (ARQ-13, ARQ-14); auditoría solo en log (ARQ-23) | **Juanita** |
| **ASR7** (RNF10, historial inmutable) | Versiones append-only con Builder (§4.7, §6.2.1); usuario de BD con solo INSERT y SELECT sobre el historial (§5.2) | No existe. `infra/mysql/transaccional/01-esquemas-y-usuarios.sh:25` concede `ALL PRIVILEGES` | **Ausente** (ARQ-01, ARQ-16) | Hernando; dba (permisos) |
| **ASR8** (RNF11, 60 peticiones/min por consumidor) | Rate limiting y API Keys en el gateway (§4.7, SD-07) | `api-gateway/.../ApiKeyRateLimitFilter.java:30-33` solo deja pasar; `gateway` `V1__base.sql` vacía | **Ausente** (ARQ-03) | Santiago |
| **ASR9** (RNF12, 0 cambios para los consumidores al migrar) | Proxy inverso: los consumidores solo conocen la URL del gateway (§4.7, SD-07) | `ProxyService` + `biohub.gateway.rutas` por variable; servicios en `127.0.0.1`; web-publica solo usa `VITE_API_URL` (`web-publica/src/api/cliente.js:1`); el paso a Aiven solo cambia variables (D35, D36) | **Parcial**: el núcleo está implementado; falta la API externa versionada `/api/v1/externa/**` (D16, 03 §J12 punto 4) | Santiago (gateway); **Juanita** (web-publica y consulta pública ya cumplen) |

---

## 4. Detalle de los ASR de Juanita

### 4.1 ASR1 – pico x60 en la consulta pública

| Táctica del SAD | Implementación | Estado |
|---|---|---|
| Servicio aparte (§4.7, Tabla 1) | `servicio-consulta-publica` con proceso, BD (`mysql-lectura`) y cola propios; no llama a `servicio-cientifico` (03 §J1 regla 2) | Implementada |
| Caché distribuida (§5.1) | `@Cacheable(sync = true)` en `ConsultaFichasService.java:29,38` (una sola carga por clave e instancia: evita la estampida); `Cache-Control: public, max-age=60/30` en `FichasPublicasController.java:48,58` | Implementada |
| Tolerancia a fallos de la caché | `CacheTolerante` (decorador de `Cache`: un fallo cuenta como miss), Lettuce `REJECT_COMMANDS` (`CacheConfig.java:85-93`), Redis fuera del health | Implementada y probada (`RedisCaidoIntegracionTest`) |
| Degradación controlada (§5.1, SD-05: "elige una estrategia según la carga") | `ValidadorBusqueda.java:78`: si `CONSULTA_MODO_DEGRADADO=true` ignora `familia` y `departamento`; la respuesta lo indica (`modoDegradado`, `filtrosIgnorados`) | Parcial: se activa a mano y requiere reiniciar; no hay "recomendaciones" que desactivar |
| Redundancia y escalado horizontal tras NGINX (§5.1, §5.3) | No existe: no hay balanceador ni compose con réplicas (el compose de despliegue que fijaba `container_name` se retiró con D34) | Ausente |
| Rate limiting (§5.1) | `ApiKeyRateLimitFilter` vacío | Ausente (Santiago) |
| Health checks (§5.1) | `management.endpoint.health.probes.enabled=true`; `HEALTHCHECK` en el Dockerfile | Implementada |
| Evidencia del ≥ 95 % y de la estabilización < 5 min | `pruebas-carga/README.md`: "Hoy está vacía" | Ausente |

### 4.2 ASR4 – consulta ≤ 2 s con carga científica
- **CQRS real:** el modelo de lectura `ficha_publica` vive en otra base, se alimenta solo por eventos y nunca consulta la base de escritura. La proyección es idempotente por `version` (D23) y tolera el desorden (`ProyectorFichas`).
- **Rutas de lectura acotadas:** la ficha se lee por PK; la búsqueda usa FULLTEXT en modo booleano o `LIKE` por prefijo, siempre con parámetros enlazados (`BusquedaFichasJdbc`), y el límite de paginación es `(page+1)*size <= 10.000` (D33).
- **Fallos rápidos:** Hikari 2 s → 503; Redis 300 ms → miss. Un fallo de un componente no cuelga la petición.
- **Pendiente externo:** sin productor de `hallazgo.*` (ARQ-07), la frescura del modelo de lectura solo se ha probado con eventos sintéticos.

### 4.3 ASR6 – coordenadas cifradas

| Requisito del SAD | Código | Valoración |
|---|---|---|
| AES-256 antes de persistir | `CifradorAesGcm.java:39-50` (llave de 32 B, GCM con tag de 128 bits); `UbicacionServiceImpl.java:60-63` cifra antes de `saveAndFlush` | Cumple y mejora el SAD: GCM autentica el dato y la AAD impide mover el texto cifrado de una fila a otra |
| Ninguna coordenada en claro en la BD | La entidad y la tabla no tienen columnas de latitud/longitud; `CHECK` de grupo en la migración | Cumple por construcción (no depende de que un decorador se aplique) |
| Llaves fuera de la BD (JKS, §4.7, §5.2, C4) | `ProveedorDeLlavesEntorno`: `UBICACION_CLAVES` + `UBICACION_CLAVE_ACTIVA` (D15, D20); valida al arrancar sin mostrar valores | Cumple el objetivo (separación llave/dato); **difiere del SAD** en el mecanismo (ARQ-11). Con Aiven la separación es mayor: llave y dato quedan en proveedores distintos |
| Las coordenadas nunca salen del servicio (§4.8, §5.2) | Los eventos no las llevan (contrato 04); el consumidor rechaza claves de coordenadas en cualquier nivel (`ValidadorDatosHallazgo.java:47,116`); `aFichaPublica` en web-publica es una lista blanca | Cumple para campos con nombre; abierto para texto libre y DLQ (SEC-J-02/03, ARQ-14) |
| Solo personal autorizado ve coordenadas exactas (RF13) | `@PreAuthorize` + `PoliticaAccesoUbicacion` + `ServicioDescifrado.java:38` (segunda barrera); `ConsultorHallazgos` ni siquiera lee las columnas cifradas si el rol no puede verlas | Cumple; el Strategy del SAD no está con su forma (ARQ-09) |
| Auditoría de accesos (§5.2) | `AuditoriaAccesoUbicacion` (logger `biohub.auditoria.ubicacion`): usuario, endpoint, resultado, filtros, cantidad y omitidos; también los 403 | Cumple como log; sin persistencia ni retención (ARQ-23) |
| Rotación de llaves | `RecifradoService` (lotes de 500, `FOR UPDATE SKIP LOCKED`, nonce nuevo, reanudable) | Cumple; lento contra Aiven (ARQ-12) |

### 4.4 ASR9 – lo que aporta la parte de Juanita
- web-publica solo conoce la URL del gateway (`VITE_API_URL`); consulta-publica escucha en `127.0.0.1` y no se expone.
- La migración a Aiven es, en sí misma, una prueba de ASR9: cambian host, puerto y credenciales de las bases por variables de perfil (D36) y ni web-publica ni un consumidor externo cambia nada. Conviene dejarlo registrado como evidencia en `07-reporte-qa.md` (petición antes y después del cambio de perfil con la misma URL pública).

### 4.5 Patrones del SAD §6 que tocan a Juanita

| Patrón (SAD) | Clases que nombra el SAD | Código actual | Estado | Hallazgo |
|---|---|---|---|---|
| Adapter – Gestor de Llaves (§6.3.1) | `ServicioCifrado`, `AdaptadorCifradoJks` (.p12) | Puerto `CifradorCoordenadas` + adaptador `CifradorAesGcm` sobre JCA; puerto `ProveedorDeLlaves` + `ProveedorDeLlavesEntorno` | Implementado, con otros nombres y otro almacén | ARQ-11 |
| Decorator – Repositorio de Ubicaciones (§6.3.2) | `RepositorioUbicaciones`, `RepositorioUbicacionesJpa`, `DecoradorCifradoCoordenadas`, `DecoradorAuditoriaAcceso` | `UbicacionHallazgoRepository extends JpaRepository`; el cifrado está en `UbicacionServiceImpl`, el descifrado en `ServicioDescifrado` y la auditoría la invoca cada servicio | No aplicado como Decorator | ARQ-10 |
| Strategy – Política de Acceso (§6.4.1) | `EstrategiaVisibilidadUbicacion`, `VisibilidadExacta`, `VisibilidadGeneralizada` | `PoliticaAccesoUbicacion` con `puedeVerCoordenadas` (booleano) y ramas `if` en `BusquedaHallazgosService.convertir` | Punto único sí; patrón no | ARQ-09 |
| Observer – Publicador de Eventos (§6.4.2), lado ubicacion | `ServicioUbicacion` con `@EventListener` en la misma transacción | Llamada directa de registro a `UbicacionService` (`MANDATORY`) (D18, D22) | Distinto del SAD | ARQ-08 |
| Decorator de caché en Consulta Pública (§6.5) | Decorador del repositorio de lectura que consulta Redis | `@Cacheable` (proxy de Spring, que ya es un decorador) + `CacheTolerante` (decorador explícito de `Cache`) | Implementado (variante) | – |
| Strategy búsqueda completa/degradada (§6.5) | Dos estrategias elegidas según la carga | Un booleano por configuración en `ValidadorBusqueda` | Parcial | ARQ-06 |
| Observer en Consulta Pública (§6.5) | Consumidor del broker que actualiza la copia e invalida la caché | `ConsumidorEventos` (`@RabbitListener`) → `ProcesadorHallazgos` → `InvalidadorCacheFichas` | Implementado | – |

---

## 5. Hallazgos

Severidad: **Crítico** = un atributo crítico del SAD (§3.2) no tiene ninguna táctica implementada; **Alto** = un ASR queda incumplido o sin evidencia; **Medio** = el código cumple el objetivo, pero difiere del SAD o tiene un riesgo concreto; **Bajo** = calidad, documentación o riesgo menor. Esfuerzo: S ≤ 1 día, M 2–4 días, L > 4 días.

| ID | Sev. | Archivo:línea | Problema | Por qué importa | Cómo corregirlo | Esf. | Responsable |
|---|---|---|---|---|---|---|---|
| ARQ-01 | **Crítico** | `servicio-cientifico/.../registro/*/package-info.java` (solo existen estos); `infra/mysql/transaccional/01-esquemas-y-usuarios.sh:25` | ASR7 sin implementar: no hay `VersionHallazgo.Builder`, repositorio append-only ni historial, y el usuario de BD del servicio tiene `ALL PRIVILEGES` | La integridad es un atributo crítico (SAD §3.2). El SAD §5.2 exige que la inmutabilidad "no dependa únicamente del código" | Implementar el módulo registro según SAD §6.2.1; usuario de aplicación con `INSERT, SELECT` en las tablas de historial y usuario de migración aparte (D37, ARQ-16); prueba que intente `UPDATE`/`DELETE` y espere el error 1142 | L | Hernando; dba (permisos) |
| ARQ-02 | Alto | (no existe) | ASR5 sin implementar: no hay columna JSON ni Fábrica de Atributos | RNF8 sin ninguna táctica | Implementar según SAD §4.7 y SD-09 | L | Hernando |
| ARQ-03 | Alto | `api-gateway/.../infrastructure/filtro/ApiKeyRateLimitFilter.java:30-33`; `api-gateway/src/main/resources/db/migration/V1__base.sql` | ASR8 sin implementar: el filtro solo deja pasar; no hay tabla de API Keys ni contadores. Tampoco el rate limit por IP de `/api/publica/**` (D16) | ASR8 incumplido; ASR1 pierde su primera barrera contra el tráfico x60 | Contadores por ventana de 1 min en Redis (prefijo distinto de `biohub:publica:`), API Key solo como hash en la BD del gateway, 429 + `Retry-After` (SD-07) y confiar en `X-Forwarded-For` solo del proxy propio | M | Santiago |
| ARQ-04 | Alto | `servicio-reportes/.../eventos/service/ConsumidorEventos.java:27`; `servicio-reportes/src/main/resources/db/migration/V1__base.sql` | ASR3 parcial: solo hay un consumidor de prueba; no existe el reporte, el 202 ni el modelo analítico. Además, `hallazgo.reclasificado` y `hallazgo.actualizado` no están en `TIPOS_CONOCIDOS` y se irán a la DLQ en cuanto se publiquen | ASR3 sin evidencia; pérdida de eventos en reportes | Implementar SD-08; aceptar los tres tipos `hallazgo.*` (contrato 04) | L | Nicolás |
| ARQ-05 | Alto | D-I15-2 (`00-plan.md`); `servicio-*/src/main/resources/application-aiven.yml:11` | Con un solo servicio MySQL de Aiven (1 vCPU, 1 GB, `max_connections` 76), la base de escritura, la de lectura pública y la analítica comparten CPU, memoria, E/S y conexiones | El SAD §4.7 justifica ASR2 con "su propia base de escritura: el tráfico público nunca llega a esa base", y el SRS (ASR3) pide "dos instancias físicas distintas". Una tormenta de misses del pico x60 o un reporte pesado degradaría el registro: es el problema que originó el proyecto (SAD §3.2) | Dos servicios de Aiven (§8.5). Si se mantiene uno, aplicar las mitigaciones de §8.4 y declararlo en SAD §5.3 como limitación del prototipo | S | **Juanita** + tech lead (P-A1) |
| ARQ-06 | Alto | `pruebas-carga/README.md` ("Hoy está vacía"); `servicio-consulta-publica/.../service/ValidadorBusqueda.java:78`; `servicio-consulta-publica/src/main/resources/application.yml:99` | ASR1 sin evidencia: no hay script k6, ni compose con varias instancias de consulta-publica detrás de un balanceador (SAD §5.3), y la degradación es un booleano fijo al arrancar | ASR1 es el driver principal del SAD y no hay forma de demostrar el ≥ 95 % ni la estabilización en menos de 5 min | (1) `pruebas-carga/compose-carga.yml` efímero con `servicio-consulta-publica` escalado (`--scale`, sin `container_name`) detrás de NGINX; (2) k6 con línea base N y pico 60N (03 §J13); (3) Strategy `EstrategiaBusqueda` (`BusquedaCompleta`/`BusquedaDegradada`) elegida en cada petición por un `MonitorCarga` (peticiones en curso o p95 de la última ventana) con el booleano actual como forzado manual | M | **Juanita** |
| ARQ-07 | Medio | `servicio-cientifico/.../eventos/controller/EventosPruebaController.java:26` (único uso de `PublicadorEventos`) | Nadie llama a `UbicacionService` ni publica `hallazgo.*`: el flujo de punta a punta registro → ubicación → evento → ficha pública no existe | ASR4 y ASR6 solo se verifican con pruebas sintéticas; los contratos pueden desviarse sin que nadie lo note | Prueba de contrato compartida: un `HallazgoRegistrado` de ejemplo en `04` que Hernando use como fixture del publicador y Juanita como fixture del consumidor | S | Hernando (Juanita aporta el fixture) |
| ARQ-08 | Medio | `servicio-cientifico/.../ubicacion/service/UbicacionService.java:8`; D18/D22 | El SAD (§2.5, §6.4.2, §7.1) dice que registro y ubicacion "no se referencian entre sí" y que el Servicio de Ubicación es un observador con `@EventListener` en la misma transacción. El código hace que registro importe `UbicacionService` | El diagrama C4-4 y el texto del SAD no coinciden con el código: un evaluador lo verá. Acoplar registro a ubicacion contradice la regla de módulos del SAD | `ObservadorUbicacion` en `ubicacion.service` con `@EventListener` (síncrono, misma transacción) sobre `HallazgoRegistrado`/`HallazgoReclasificado`, definidos en `cientifico.eventos.domain`; llama a `UbicacionService`, que conserva `MANDATORY`. Si el cifrado falla, la excepción revierte el registro, como dice el SAD. Hernando publica el evento de dominio con `ApplicationEventPublisher` en lugar de llamar al servicio (P-A3) | S | **Juanita** (+ Hernando) |
| ARQ-09 | Medio | `servicio-cientifico/.../ubicacion/service/PoliticaAccesoUbicacion.java:12-29`; `BusquedaHallazgosService.java:81-102` | No existe el Strategy del SAD §6.4.1 (`EstrategiaVisibilidadUbicacion`, `VisibilidadExacta`, `VisibilidadGeneralizada`): la política devuelve un booleano y la decisión se repite con `if` en la búsqueda y en el mapa | El SAD lo presenta como el patrón que asegura RF13 "en un único punto". Funciona hoy, pero añadir un nivel de acceso obliga a tocar varias clases | Interfaz `EstrategiaVisibilidadUbicacion.resolver(fila, auth)`; `VisibilidadGeneralizada` nunca recibe el `ServicioDescifrado`; `VisibilidadExacta` es la única que lo recibe; `PoliticaAccesoUbicacion.estrategiaPara(auth)` devuelve la generalizada por defecto. Se mantienen `puedeVerCoordenadas` (SpEL) y la segunda barrera | S | **Juanita** |
| ARQ-10 | Medio | `servicio-cientifico/.../ubicacion/domain/UbicacionHallazgoRepository.java:7`; `UbicacionServiceImpl.java:60-63`; `MapaService.java:77`; `BusquedaHallazgosService.java:75` | No existe el Decorator del SAD §6.3.2. El repositorio hereda de `JpaRepository` (expone `delete*`, `deleteAll`, `saveAll`), cuando el SAD dice que no tiene operaciones de actualización ni de borrado, y la auditoría depende de que cada servicio se acuerde de llamarla | Un servicio nuevo podría leer coordenadas cifradas o borrar filas sin pasar por la auditoría. El cifrado sí está protegido por construcción (no hay columnas en claro) | Opción A (recomendada): `RepositorioUbicaciones` (`guardar`, `buscar`, `obtenerUbicacionExacta`) con `RepositorioUbicacionesJpa` + `DecoradorCifradoCoordenadas` + `DecoradorAuditoriaAcceso`, ensamblados en `ConfiguracionUbicacion`; el resto del módulo solo inyecta la interfaz. Opción B (mínima): `UbicacionHallazgoRepository extends Repository<…>` con solo `findById`, `existsById` y `save`, y ajustar el texto del SAD | M (A) / S (B) | **Juanita** |
| ARQ-11 | Medio | `servicio-cientifico/.../ubicacion/infrastructure/ProveedorDeLlavesEntorno.java:29`; SAD §4.7, §5.2, §6.3.1; C4-nivel2 ("Almacén de llaves [Java KeyStore .p12]") | Las llaves se leen de variables de entorno (D15/D20), no de un JKS protegido con contraseña | El SAD y el C4 describen otra pieza. La separación llave/dato se cumple; el mecanismo no es el documentado | Actualizar el SAD (§9, texto propuesto). Opcional: `ProveedorDeLlavesKeyStore` (PKCS12) como segunda implementación del puerto, elegida por propiedad. Sería la demostración del Adapter que promete el SAD ("solo se escribe un adaptador nuevo") | S (doc) / M (JKS) | **Juanita** |
| ARQ-12 | Medio | `servicio-cientifico/.../ubicacion/service/RecifradoService.java:96-121`; `api-gateway/src/main/resources/application.yml:59` (`timeout-lectura: 30s`) | El re-cifrado hace un `UPDATE` por fila, sin batching JDBC, dentro de una petición HTTP síncrona. Contra Aiven, cada `UPDATE` cuesta una ida y vuelta por internet | Con 80 ms de ida y vuelta, un lote de 500 tarda unos 40 s: el gateway corta a los 30 s (504) aunque el proceso sigue. La rotación de llaves (ASR6) deja de ser operable | `hibernate.jdbc.batch_size=100` y `order_updates=true`, más `rewriteBatchedStatements=true` en la URL de `aiven`. Si aun así supera 30 s: 202 + `GET /recifrado/estado` (cambio de contrato en `04`) | S | **Juanita** |
| ARQ-13 | Medio | `servicio-consulta-publica/src/main/resources/application.yml:92` (`verificar-publicador` en `false`) | SEC-J-01: se aceptan `hallazgo.*` de cualquier usuario AMQP que ponga `origen: servicio-cientifico` | Un servicio comprometido puede crear o alterar fichas públicas (integridad de ASR4) | Valor por defecto `true` y `setUserId` en `PublicadorEventos` (08, SEC-D-03) | S | **Juanita** (valor por defecto); Santiago (permisos del broker) |
| ARQ-14 | Medio | `servicio-consulta-publica/.../eventos/service/ValidadorDatosHallazgo.java:47,116` | SEC-J-02/03: solo se detectan coordenadas en claves con nombre; en texto libre pasan, y un evento rechazado queda en la DLQ con su contenido | ASR6: "las coordenadas exactas nunca salen del servicio" | Detector de patrones de coordenadas en los campos de texto y "descartar con ack + incidente sin valores" en lugar de enviar a la DLQ (08) | M | **Juanita** (consumidor); Hernando (validación antes de publicar) |
| ARQ-15 | Medio | `servicio-*/src/main/resources/application-aiven.yml:11` (`sslMode=${AIVEN_SSL_MODE:VERIFY_CA}`); consola de Aiven | La base pasa a estar en internet: el valor por defecto es `VERIFY_CA` (no valida el nombre del host) y el servicio de Aiven acepta conexiones de cualquier IP mientras no se configure su lista de IPs | El SAD §5.2 se apoyaba en "una red privada, sin acceso directo desde internet" para el tráfico interno | Valor por defecto `VERIFY_IDENTITY` (el certificado de Aiven trae el nombre del servicio); lista de IPs en Aiven con las de los integrantes cuando sea posible; contraseñas de usuario por servicio de al menos 24 caracteres; `avnadmin` solo desde la consola | S | **Juanita** (perfil y consola) |
| ARQ-16 | Medio | `servicio-cientifico/src/main/resources/application.yml:29` (`user: ${CIENTIFICO_MIGR_USER:${spring.datasource.username}}`) | El usuario de migración separado es opcional: si no se define, Flyway y la aplicación usan el mismo usuario con permisos de DDL | Con un solo usuario es imposible dejar el historial con solo `INSERT, SELECT` (ASR7, SAD §5.2) | En el perfil `aiven` (y en CI) exigir `CIENTIFICO_MIGR_USER` (sin valor por defecto); en Aiven crear `cientifico_migr` (DDL sobre `cientifico`) y `cientifico_app` (DML, y solo `INSERT, SELECT` en el historial cuando exista) | S | dba; Hernando |
| ARQ-17 | Medio | `servicio-*/src/main/resources/application-aiven.yml:16-17` (pool 5); Aiven gratuito (`max_connections` 76) | El presupuesto de conexiones no está repartido: 5 servicios × pool 5 por instancia, más instancias extra de consulta-publica para ASR1, más cada integrante que arranque sus servicios contra la misma base | Por encima de 76, MySQL rechaza conexiones nuevas (`Too many connections`) y el primer afectado puede ser el registro | Tabla de presupuesto (§8.4); `ALTER USER … WITH MAX_USER_CONNECTIONS n` por usuario de servicio; perfil `local` por defecto para desarrollar y `aiven` solo para integración y demo | S | **Juanita** (sus servicios); dba (límites por usuario) |
| ARQ-18 | Bajo | `ubicacion/service/MapaService.java:11-12`, `BusquedaHallazgosService.java:12-13`, `RecifradoService.java:19`; `consultapublica/service/ConsultaFichasService.java:10-12`, `infrastructure/BusquedaFichasJdbc.java:11-12`, `config/CacheConfig.java:26-27` | `service` e `infrastructure` importan los DTOs de `controller` | SAD §7.1: las dependencias apuntan hacia `domain` y `controller` no contiene lógica | Mover los `record` de respuesta a `service` (p. ej. `service.vista`) o mapear en el controlador; regla ArchUnit que lo impida | S | **Juanita** |
| ARQ-19 | Bajo | `docker-compose.yml:28,46` (`TZ: America/Bogota`); `V20261026_1010__ubicacion_hallazgo.sql` (`DEFAULT CURRENT_TIMESTAMP(3)`) | MySQL local corre en hora de Bogotá y Aiven en UTC: `creado_en`/`actualizado_en` difieren 5 h según el perfil | Datos de auditoría inconsistentes entre entornos | `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` en las URL de los dos perfiles (D36) | S | dba / devops |
| ARQ-20 | Bajo | `api-gateway/.../config/RestClientConfig.java:17-21` | No hay circuit breaker (SAD §5.1); solo timeouts de 2 s y 30 s | Con un servicio lento, cada petición espera hasta 30 s y ocupa un hilo del gateway | Resilience4j (o un contador simple) por destino, con 503 inmediato mientras está abierto | M | Santiago |
| ARQ-21 | Bajo | `infra/keycloak/realm-biohub.json:12` (`failureFactor: 5`) | HU-11 y SAD §7.2.1 dicen que la cuenta se bloquea tras 3 intentos | Diferencia verificable en la demo | `failureFactor: 3` | S | Santiago |
| ARQ-22 | Bajo | SAD: Tabla 3, §6.3.1, Figura 14, §1.2, §3.5 | Inconsistencias del SAD: la fila Decorator de la Tabla 3 dice "Repositorio de Hallazgos y Versiones" y §6.3.2 "Repositorio de Ubicaciones"; §6.3.1 repite un párrafo de §6.3.2; la Figura 14 enlaza `SD-08`; §1.2 promete vistas de despliegue y datos que no existen; §3.5 dice que solo se cifran las especies en peligro (D15 cifra todas) | El documento entregable se contradice | Textos de §9 | S | Equipo (documentador) |
| ARQ-23 | Bajo | `ubicacion/service/AuditoriaAccesoUbicacion.java:11` | La auditoría de accesos a coordenadas solo va a un log (D28); sin persistencia ni retención | SAD §5.2: "permite detectar accesos inusuales". Si el log se pierde al reiniciar, no se puede revisar | Tabla `ubicacion_acceso_log` de `05` (Could), insertada en una transacción propia (`REQUIRES_NEW`) para que una consulta de solo lectura también deje rastro | M | **Juanita** |
| ARQ-24 | Bajo | `servicio-*/Dockerfile`; no hay compose de servicios desde D34 | Sin límites de CPU y memoria por servicio | El bulkhead del SAD §5.1 ("recursos propios") no está expresado en ninguna parte | En el compose de carga (ARQ-06) y en cualquier despliegue: `cpus`/`mem_limit` por servicio y `-XX:MaxRAMPercentage` en la JVM | S | Santiago / devops |
| ARQ-25 | Bajo | SAD §6.5; `servicio-consulta-publica` | El SAD no describe la tolerancia a Redis caído ni la invalidación después del commit, que sí están implementadas | Se pierde en el documento una táctica real de ASR1/ASR4 | Añadir el párrafo propuesto en §9 | S | **Juanita** |

**Hallazgos Crítico y Alto por responsable:** Hernando: ARQ-01 (Crítico) y ARQ-02. Santiago: ARQ-03. Nicolás: ARQ-04. Juanita: ARQ-05 (con el tech lead) y ARQ-06.

---

## 6. Lo que está bien hecho

- **Cifrado mejor que el exigido:** AES-256-GCM con AAD = UUID del hallazgo detecta tanto la alteración como el traslado del dato a otra fila; nonce aleatorio por cifrado con `UNIQUE (clave_version, coord_nonce)` como red de seguridad; llaves versionadas con re-cifrado reanudable; el servicio no arranca sin una llave válida y los mensajes de error nunca muestran el valor.
- **Ninguna coordenada en claro por construcción:** no hay columnas de latitud/longitud, `Coordenadas`/`CoordenadasCifradas` enmascaran su `toString()` y `ConsultorHallazgos` no lee las columnas cifradas para los roles sin permiso (minimización).
- **Doble barrera de autorización** (`@PreAuthorize` + `ServicioDescifrado`) y falla segura: rol ausente o desconocido = sin coordenadas.
- **CQRS con consumidor idempotente y tolerante al desorden** ("gana la versión mayor" con `SELECT … FOR UPDATE`), invalidación de caché después del commit y DLQ sin registrar el cuerpo del mensaje.
- **Caché realmente opcional:** `CacheTolerante` corrige un defecto de `@Cacheable(sync = true)` de Spring (que pierde la excepción de negocio cuando falla Redis), Lettuce falla rápido y Redis no participa en el health. Probado con Redis detenido.
- **Defensa en profundidad en el borde:** doble validación del JWT, servicios en `127.0.0.1`, cuerpo limitado en el gateway, validación de rutas y CORS explícito.
- **Pruebas que respetan D30:** Testcontainers para MySQL, RabbitMQ y Redis; prueba de arquitectura de módulos; pruebas QA de inyección, límites y DLQ.
- **Perfiles de Aiven bien orientados** (trabajo de devops en curso): sin valores por defecto que apunten a un servidor, CA verificado, `connectTimeout`/`socketTimeout`, pool acotado, `keepalive` y `max-lifetime`.

---

## 7. Flyway y Aiven: compatibilidad de las migraciones de Juanita

| Comprobación | `servicio-cientifico` (ubicacion) | `servicio-consulta-publica` |
|---|---|---|
| Toda tabla con PK (`sql_require_primary_key` activo en Aiven) | `ubicacion_region`, `ubicacion_departamento` y `ubicacion_hallazgo` con PK | `departamento` y `ficha_publica` con PK |
| Sin `DEFINER`, triggers, funciones ni `SET GLOBAL` | Sí | Sí |
| `CHECK`, `REGEXP` y FULLTEXT de InnoDB (MySQL 8.0.16+) | `CHECK` y `REGEXP` | FULLTEXT `ft_ficha_publica_nombres` |
| Bloqueo entre instancias que migran a la vez | Flyway usa `GET_LOCK`, disponible en Aiven | Igual: varias réplicas de consulta-publica pueden arrancar a la vez |
| `out-of-order` + versiones por fecha | Necesario: Hernando agregará migraciones en el mismo esquema | – |
| `FOR UPDATE SKIP LOCKED` (re-cifrado) | Soportado en MySQL 8 | – |

Conclusión: las migraciones actuales funcionan en Aiven sin cambios. El riesgo está en las futuras (Hernando, Nicolás, Santiago): las reglas de D37 deben ir en el README de cada servicio (tarea 15e del dba).

---

## 8. Impacto del cambio a Aiven y Flyway sobre los ASR

### 8.1 Qué cambia
| Antes (D11/D12, retiradas) | Ahora (D34–D37) |
|---|---|
| 4 servidores MySQL propios en una red privada | MySQL administrado en Aiven, accesible por internet con TLS |
| Latencia de red local (< 1 ms) entre servicio y BD | Ida y vuelta por internet (decenas a cientos de ms según la región) |
| Servidores de lectura, analítica y escritura físicamente separados | 1 servidor de Aiven compartido (D-I15-2) o 2 (recomendado, P-A1) |
| Conexiones limitadas solo por la RAM | `max_connections` = 76 en el plan gratuito |
| Copias de seguridad propias | Copias de Aiven (incluidas en el plan gratuito) |

### 8.2 Impacto por ASR

| ASR | Impacto | Mitigación |
|---|---|---|
| ASR1 | Neutro si la caché funciona: un acierto no toca la BD. Con misses masivos (búsquedas variadas durante el pico), la carga llega al servidor de Aiven compartido | Redis + `sync=true` + caché HTTP; pool acotado por instancia, de modo que consulta-publica nunca pueda usar más de `5 × réplicas` conexiones; rate limit por IP (ARQ-03) |
| ASR2 | **Negativo con un servidor:** el registro comparte CPU, buffer pool y conexiones con la consulta pública y los reportes | Dos servidores (§8.5). Con uno: `MAX_USER_CONNECTIONS` para `consulta_publica` y `reportes`, reservando conexiones para `cientifico` |
| ASR3 | **Negativo con un servidor:** un reporte que cruce millones de filas consume la única vCPU del servidor que atiende el registro (el SRS pide dos instancias físicas) | Reportes en el servidor de lectura; procesamiento por lotes con pausas; ejecución programada fuera de horas pico |
| ASR4 | Un miss de búsqueda cuesta 2 idas y vueltas (`COUNT` + `SELECT`) más la ejecución; sigue dentro de 2 s (§8.3). Con un servidor compartido, la escritura científica puede subir el tiempo de la consulta | Igual que ASR2; índices y FULLTEXT ya presentes |
| ASR6 | **Positivo:** la llave (variables del servicio) y el dato (Aiven) quedan en proveedores distintos, y las copias de Aiven solo contienen texto cifrado. **Nuevo requisito:** TLS hacia la BD, porque ya no hay red privada | `VERIFY_IDENTITY` (ARQ-15); lista de IPs; las llaves nunca se cargan en Aiven ni en CI |
| ASR7 | Neutro, si se separa el usuario de migración; sin eso, imposible (ARQ-16) | D37 |
| ASR8 | Neutro: los contadores del rate limit irán a Redis local, no a Aiven | Las API Keys conviene cachearlas en memoria con TTL corto para no consultar la BD del gateway en cada petición |
| ASR9 / RNF12 | **Positivo:** es una migración real de infraestructura hecha solo con configuración; los consumidores no cambian. Riesgo de dependencia del proveedor bajo: MySQL 8 estándar, sin funciones propias de Aiven (a diferencia del serverless descartado en SAD §4.9) | No usar extensiones ni API propietarias de Aiven; mantener el perfil `local` funcional como plan de salida |
| RNF13 | Sin impacto | – |

### 8.3 Presupuesto de latencia (medir `r`, la ida y vuelta real a Aiven)

Supuesto de trabajo: `r` ≈ 60–150 ms desde Bogotá hacia una región de Norteamérica (este). **Debe medirse** (p. ej. 50 ejecuciones de `SELECT 1` con el perfil `aiven`) y registrarse en `07-reporte-qa.md`.

| Operación | Idas y vueltas a la BD | Estimado con r = 100 ms | Meta | ¿Cabe? |
|---|---|---|---|---|
| Registro de hallazgo (RNF4): BEGIN, INSERT hallazgo, INSERT versión, INSERT auditoría, `existsById` + INSERT ubicación, flush, COMMIT | ~10–12 | 1,0–1,2 s + archivos + confirmación de RabbitMQ local | ≤ 5 s | Sí, mientras r < ~300 ms |
| Ficha pública, acierto de caché (RNF5) | 0 | < 50 ms | ≤ 2 s | Sí |
| Ficha pública, miss | 1 | ~0,1–0,2 s | ≤ 2 s | Sí |
| Búsqueda pública, miss (`COUNT` + `SELECT`) | 2 | ~0,2–0,4 s | ≤ 2 s | Sí |
| Búsqueda interna HU-13 (+ ≤ 100 descifrados en memoria) | 2 | ~0,3 s | < 3 s | Sí |
| Mapa (1.001 filas + `COUNT`) | 2 + transferencia (~100 KB) | ~0,4–0,6 s | < 3 s | Sí |
| Proyección de un evento en consulta-publica (`SELECT … FOR UPDATE`, INSERT/UPDATE, COMMIT) | ~4 | ~0,4 s por evento y hilo | Consistencia eventual | Sí, pero limita a ~2–3 eventos/s por hilo: en una ráfaga el portal se actualiza con retraso |
| Conexión nueva (TCP + TLS + autenticación) | ~3–4 | 0,3–0,6 s | – | Se evita con `minimum-idle` y `keepalive-time` |
| Re-cifrado de 500 filas sin batching | ~500 | ~50 s | < 30 s (timeout del gateway) | **No** (ARQ-12) |

### 8.4 Mitigaciones si se mantiene un solo servidor (o como capa extra con dos)
1. **Bulkhead de conexiones:** pool por instancia y `MAX_USER_CONNECTIONS` por usuario de servicio. Propuesta sobre 76: `cientifico` 15 (pool 5, hasta 3 instancias), `consulta_publica` 15 (pool 5, hasta 3 réplicas), `reportes` 6, `gateway` 6, `equipos` 4, migración 3 por servicio solo al arrancar; resto (~20) de margen para la consola y los integrantes. Así el pico público no puede quitarle conexiones al registro.
2. **Desarrollo en `local`:** cada integrante desarrolla con el perfil `local`; `aiven` es para la integración y la demo. Evita que 4 personas × 5 servicios agoten las 76 conexiones.
3. **Reportes acotados:** lectura por lotes con `LIMIT` y pausas, nunca un `SELECT` de millones de filas sin paginar; programados fuera de la ventana de la demo.
4. **Disponibilidad del plan gratuito:** Aiven puede apagar un servicio por inactividad y cambiar su región; encenderlo y medir `r` antes de cada demo. El perfil `local` es el plan B.
5. **Declararlo en el SAD §5.3** como simplificación del prototipo, con el mismo tono que el escalado manual.

### 8.5 Recomendación: dos servicios MySQL en Aiven
- **Servicio 1, escritura:** bases `cientifico` y `equipos` (como dice SAD §4.5: comparten la transaccional porque la carga de Equipos es baja).
- **Servicio 2, lectura y análisis:** bases `consulta_publica`, `reportes` y `gateway`.
- **Por qué:** es la separación física que el SAD (§4.7, ASR2) y el SRS (ASR3: "dos instancias físicas distintas") usan como argumento. Con un solo servidor de 1 vCPU, ningún ajuste de software garantiza que un pico público o un reporte no degraden el registro. Cada servidor tiene además su propio cupo de 76 conexiones.
- **Costo:** cero. El plan gratuito permite un MySQL por organización, así que el segundo se crea en la organización de otro integrante. El código ya lo permite: cada servicio tiene su host y su puerto (D35, D36).
- **Qué no se separa más:** consulta pública y reportes comparten el servidor 2. El efecto de un reporte sobre la consulta lo amortigua la caché Redis (ASR4), y la separación total costaría un tercer servicio sin que ningún ASR lo exija.

---

## 9. Propuesta de cambios al SAD

| Sección | Cambio | Texto propuesto |
|---|---|---|
| §2.3 Figura 2 (C4 nivel 2) | Tecnología de las bases y del almacén de llaves | Bases: "[MySQL 8 administrado – Aiven, TLS]". Almacén de llaves: "Llaves AES-256 versionadas [variables de entorno del Servicio Científico]" |
| §2.4 / §2.5 (nivel 3 y 4) | Alinear con el código o con la corrección (P-A2) | Si se aplican ARQ-08/09/10, el texto actual vale; solo cambian `ServicioCifrado` → `CifradorCoordenadas` y `AdaptadorCifradoJks` → `CifradorAesGcm` + `ProveedorDeLlaves`. Si no se aplican: "La Política de Acceso a Ubicación decide con una sola regla por rol; el único punto de descifrado vuelve a exigirla…" |
| §3.5 Tensión | Corregir "se cifran solo las especies en peligro" | "Se cifran **todas** las coordenadas, porque el estado de conservación puede cambiar con una reclasificación y decidir qué cifrar abriría una ventana de exposición. El costo es bajo: AES-GCM tarda microsegundos por registro, muy por debajo de la latencia de red hacia la base, y la consulta pública nunca descifra." |
| §4.5 último párrafo | Despliegue de los datos | "En el prototipo, las bases se alojan en un servicio de MySQL administrado (Aiven) con TLS obligatorio. El Servicio Científico y el de Equipos usan el servidor de escritura; la Consulta Pública, los Reportes y el API Gateway, el de lectura y análisis. Cada servicio tiene su propia base y su propio usuario, y las direcciones se configuran por entorno, de modo que separar o mover una base no requiere cambiar código." |
| §4.7 ASR2 | Precisar el aislamiento | "…tiene su propia base de escritura, en un servidor MySQL distinto del que atiende la consulta pública y los reportes. El tráfico público nunca llega a ese servidor…" |
| §4.7 ASR6 | Mecanismo de llaves | "…cifra con AES-256-GCM todas las coordenadas antes de persistirlas. Las llaves, versionadas para permitir la rotación, se cargan desde la configuración del servicio y nunca se guardan en la base ni en el repositorio. La base, alojada en otro proveedor, solo recibe texto cifrado. El acceso a las llaves está detrás de la interfaz ProveedorDeLlaves, por lo que cambiar a un Java KeyStore o a un KMS solo requiere otro adaptador." |
| §4.7 ASR9 | Evidencia | Añadir: "La migración de las bases a un servicio administrado durante el desarrollo es un ejemplo: cambió la ubicación de los datos y los consumidores siguieron usando la misma URL del gateway." |
| §5.1 Bulkhead | Bulkhead de datos | Añadir: "A nivel de datos, cada servicio usa un pool de conexiones acotado y un límite de conexiones por usuario en MySQL, para que un pico en un servicio no agote las conexiones de otro." |
| §5.1 Degradación | Activación | Si se aplica ARQ-06: "…la consulta pública cambia a la búsqueda degradada cuando su monitor de carga supera el umbral, o cuando el operador la fuerza por configuración." Si no: "…el operador activa la búsqueda degradada por configuración." |
| §5.2 Cifrado en tránsito | La base ya no está en la red privada | "Toda la comunicación externa pasa por el API Gateway con TLS. Las conexiones de los servicios a MySQL usan TLS con verificación del certificado y del nombre del servidor, porque la base administrada se alcanza por internet y solo acepta conexiones cifradas. El broker, la caché y el servidor de identidad siguen en la red privada de contenedores." |
| §5.2 Restricción a nivel de BD | Usuario de migración | Añadir: "Las migraciones de esquema (Flyway) se ejecutan con un usuario distinto del de la aplicación; el usuario de la aplicación no tiene permisos de DDL, ni UPDATE o DELETE sobre el historial." |
| §5.3 Alcance en el prototipo | Nuevas simplificaciones | Añadir: "Las bases de datos usan el plan gratuito de un servicio MySQL administrado (1 vCPU, 1 GB y 76 conexiones por servidor). Los tiempos de RNF4 y RNF5 se miden con la latencia real hacia ese servicio." Si P-A1 se resuelve con un servidor: "…la escritura y la lectura comparten un servidor; el aislamiento se limita con pools y límites de conexiones por usuario, y la separación física queda para producción." |
| §6 Tabla 3 | Corregir la fila Decorator | Componente: "Repositorio de Ubicaciones"; ASR: "ASR6" |
| §6.3.1 | Quitar el párrafo duplicado y renombrar | Eliminar "El Servicio de Ubicación recibe la cadena ya armada…" (pertenece a §6.3.2). Gestor de Llaves: "el dominio define CifradorCoordenadas (cifrar, descifrar, recifrar) y ProveedorDeLlaves; CifradorAesGcm traduce esas llamadas a JCA y ProveedorDeLlavesEntorno lee las llaves versionadas de la configuración" |
| §6.5 | Añadir la tolerancia de la caché | "El decorador de caché trata cualquier fallo de Redis como un fallo de caché y lee de la base, de modo que Redis nunca es un punto único de falla. La caché se invalida solo después de confirmar la transacción que aplica el evento." |
| §7 (nuevo §7.3) | Vista de despliegue (prometida en §1.2) | Nodos: navegador (web-interna, web-publica); host de contenedores (API Gateway, 4 servicios, RabbitMQ, Redis, Keycloak) en una red privada, con solo el gateway publicado; Aiven (servidor de escritura y servidor de lectura, TLS); integración continua (los mismos contenedores, más MySQL en contenedor, sin acceso a Aiven). Diagrama: el de `03-arquitectura.md` §"Topología de ejecución" |
| §7 (nuevo §7.4) | Vista de datos | Tabla de bases → dueño → servidor → usuario de aplicación y de migración; Flyway con versiones por fecha y `ddl-auto=validate`; "ninguna base contiene coordenadas en claro" |
| §7.2.9 | Figura 14 | Archivo `SD-09_Agregar_atributo.png` |
| Nuevo §8 (antes de Referencias) | ADR prometidos en §1.2 | Resumen de las decisiones D14–D38 de `03-arquitectura.md` que afectan al SAD (D14, D15, D20–D23, D25, D34–D38) y tabla de trazabilidad ASR → decisión → clase (la de §3 de este informe) |

---

## 10. Pendientes para el tech lead
1. **P-A1:** 1 o 2 servicios de Aiven (recomendación: 2, §8.5).
2. **P-A2:** implementar Strategy, Observer y Decorator en ubicacion (ARQ-08 a ARQ-10, unos 3 días entre los tres) o ajustar el SAD. Recomiendo implementar Strategy y Observer, porque son baratos y el SAD los muestra en el C4-4, y decidir el Decorator según el tiempo disponible (opción B como mínimo).
3. **P-A3:** confirmar con Hernando que registro publica eventos de dominio en lugar de llamar a `UbicacionService` (ARQ-08).
4. Antes de la demo con Aiven: ARQ-12, ARQ-15, ARQ-16, ARQ-17 y la medición de `r` (§8.3).
