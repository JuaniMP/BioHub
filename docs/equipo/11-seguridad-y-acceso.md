# 11 – Seguridad y acceso: guía para el equipo y sus agentes

Punto de partida para quien vaya a tocar algo relacionado con seguridad, roles, API Keys o el gateway. Si usas un agente, pásale este archivo junto con la sección que te corresponde.

Detalle formal: decisiones en `03-arquitectura.md` (D45 a D51), contrato en `04-contrato-api.md` ("Parte de Santiago"), tablas en `05-modelo-datos.md` §17 y despliegue en `09-despliegue.md` §11.

## 1. Cómo entra una petición

Todo pasa por `api-gateway` (8080). Los servicios escuchan solo en `127.0.0.1`.

| Quién llega | Ruta | Se identifica con | Qué valida el gateway |
|---|---|---|---|
| Personal interno (`web-interna`) | `/api/{cientifico,equipos,reportes}/**` | JWT de Keycloak | Token válido y uno de los 5 roles |
| Visitante (`web-publica`) | `GET /api/publica/**` | Nada | 120 peticiones por minuto por IP |
| Universidad u ONG | `GET /api/v1/externa/fichas/**` | Cabecera `X-API-Key` | Key válida y 60 peticiones por minuto por consumidor |
| Administrador | `/api/gateway/{consumidores,keys,usuarios}/**` | JWT | Rol `administrador` |

Lo que no aparece en esa tabla se deniega. El gateway hace el control grueso. **El control fino es de cada servicio**: qué rol puede usar cada endpoint.

## 2. Matriz de roles por capacidad

Las rutas con `*` son propuestas: el dueño las confirma y actualiza esta tabla.

| Capacidad | Ruta | `investigador_campo` | `auditor_cientifico` | `cientifico_alto_rango` | `analista_ambiental` | `administrador` | Dueño |
|---|---|---|---|---|---|---|---|
| Buscar hallazgos sin coordenadas | `GET /api/cientifico/ubicacion/hallazgos` | Sí | Sí | Sí | Sí | Sí | Juanita (hecho) |
| Ver mapa y coordenadas | `GET /api/cientifico/ubicacion/mapa` | No | No | Sí | No | No | Juanita (hecho) |
| Re-cifrar | `POST /api/cientifico/ubicacion/recifrado` | No | No | No | No | Sí | Juanita (hecho) |
| Registrar hallazgo y ficha | `/api/cientifico/hallazgos*`, `/fichas*` | Sí | No | Sí | No | No | Hernando |
| Reclasificar | `/api/cientifico/hallazgos/{id}/clasificacion*` | Sí | Sí | Sí | No | No | Hernando |
| Ver historial | `/api/cientifico/hallazgos/{id}/historial*` | No | Sí | Sí | No | No | Hernando |
| Registrar y asignar equipos | `/api/equipos/***` | No | No | No | No | Sí | Nicolás |
| Ver y generar reportes | `/api/reportes/***` | No | No | No | Sí | No | Nicolás |
| API Keys y límites | `/api/gateway/consumidores/**`, `/keys/**` | No | No | No | No | Sí | Santiago (hecho) |
| Usuarios y roles | `/api/gateway/usuarios/**` | No | No | No | No | Sí | Santiago (hecho) |

`administrador` nunca recibe coordenadas (RF12) ni edita datos científicos.

## 3. Qué debe hacer cada integrante

### Todos, una vez, al traer estos cambios

1. Agrega al `.env` de la raíz las variables nuevas de `.env.example`. Sin `KEYCLOAK_GATEWAY_CLIENT_SECRET`, `docker compose up` responde `Falta en .env`. Generar el valor en bash: `echo "KEYCLOAK_GATEWAY_CLIENT_SECRET=$(openssl rand -hex 24)" >> .env`.
2. Reimporta el realm (borra los datos locales de Keycloak, que son solo de desarrollo):
   ```bash
   docker compose rm -sf keycloak && docker volume rm biohub_keycloak-data && docker compose up -d keycloak
   ```
3. Si reinicias `api-gateway`, su arranque aplica la migración nueva. Contra Aiven solo debe hacerlo el dueño del gateway.

### Hernando (`registro`)

- [ ] Publica en la matriz las rutas reales de registro, fichas, reclasificación e historial.
- [ ] Confirma qué roles reclasifican: HU-03 y RF9 no lo dejan cerrado.
- [ ] Protege cada endpoint con `@PreAuthorize` y agrega una prueba de 403 por rol.
- [ ] Guarda el autor con el `sub` del JWT (SEC-17, HU-11 c.3), no con `preferred_username`.
- [ ] Confirma el tamaño máximo de adjuntos: el gateway acepta 10 MB y carga el cuerpo en memoria. Si necesitas más, avisa a Santiago.
- [ ] Coordina con Juanita el `denyAll` de `servicio-cientifico` (`config/` es compartido).

### Nicolás (`equipos` y `reportes`)

- [ ] Publica en la matriz las rutas reales y confirma los roles.
- [ ] Cambia `.anyRequest().authenticated()` por `.anyRequest().denyAll()` en los dos servicios.
- [ ] Protege cada endpoint con `@PreAuthorize` y agrega una prueba de 403 por rol.
- [ ] HU-04 exige un custodio activo, y los usuarios viven en Keycloak. Propuesta: `servicio-equipos` llama a `GET {gateway}/api/gateway/usuarios/{id}` reenviando el JWT del administrador. Confírmalo con Santiago.
- [ ] Informa el tamaño de los PDF y CSV: el proxy carga la respuesta en memoria y corta a los 30 segundos.

### Juanita (`ubicacion`, `consulta-publica`, `web-publica`)

- [ ] Confirma que `/api/publica/fichas` y `/api/publica/fichas/{id}` no cambian: de ahí depende la URL externa.
- [ ] Confirma si 120 por minuto por IP alcanza. Un campus entero puede compartir IP.
- [ ] Cambia a `denyAll` en `servicio-consulta-publica` y, con Hernando, en `servicio-cientifico`.
- [ ] `web-publica` debe mostrar el aviso de 429 usando `Retry-After`: el gateway ya lo envía y lo expone por CORS.

### Plantilla de `SecurityConfig` para un servicio

```java
.authorizeHttpRequests(auth -> auth
        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
        .requestMatchers("/api/equipos/**").hasAnyRole("investigador_campo", "auditor_cientifico",
                "cientifico_alto_rango", "analista_ambiental", "administrador")
        .anyRequest().denyAll())
```

```java
@PreAuthorize("hasRole('administrador')")
@PostMapping("/api/equipos/activos")
public ActivoResponse registrar(...) { ... }
```

## 4. Usuarios RabbitMQ por servicio (SEC-04)

La tarea `rabbitmq-usuarios` del compose puede crear `cientifico_mq`, `consulta_mq` y `reportes_mq`. Es opcional hasta que cada servicio migre, y sirve para cerrar SEC-04 y SEC-D-03. Para migrar un servicio, sin tocar su código:

1. En el `.env` de la raíz, pon contraseñas en `RABBITMQ_<SERVICIO>_PASSWORD` (`CIENTIFICO`, `CONSULTA`, `REPORTES`).
2. Corre `docker compose up -d rabbitmq-usuarios`.
3. En el `.env` de la **carpeta del servicio**, pon `RABBITMQ_USER=<usuario>` y `RABBITMQ_PASSWORD=<su contraseña>`.
4. En `servicio-consulta-publica/.env`, agrega `BIOHUB_EVENTOS_PUBLICADOR_CIENTIFICO=cientifico_mq`.

## 5. Reglas para agentes y personas

- No cambies las URL de `/api/v1/externa/**`: son un contrato con terceros (ASR9).
- No agregues un endpoint nuevo sin decidir su rol y sin prueba de 403.
- No guardes la API Key en claro ni la escribas en logs. Usa su `prefijo`.
- No uses `X-Forwarded-For` para identificar clientes: el cliente puede inventarlo.
- No apuntes pruebas a Aiven ni a un broker compartido (D30).
- Nunca edites una migración ya aplicada. Las nuevas siguen `VAAAAMMDD_HHMM__descripcion.sql` y deben pasar `deploy/aiven/verificar-migraciones.sh`.
- Avisa antes de tocar zonas compartidas: `pom.xml` raíz, `docker-compose.yml`, `infra/` y el realm.
- No leas, muestres ni subas `.env`, `certs/`, `*.pem` ni `*.p12`.

## 6. Pendiente de la parte de acceso

Las pantallas de `web-interna` van al final, cuando existan los endpoints de negocio: router y menú por rol, montaje de `src/ubicacion/`, y pantallas de API Keys y usuarios. El backend de esas pantallas ya está listo. Contrato: `04-contrato-api.md`.
