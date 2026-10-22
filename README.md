# BioHub

BioHub es una plataforma de registro y consulta de hallazgos de biodiversidad (proyecto del curso Ingeniería de Software 2, Universidad El Bosque).

> **Estado: esqueleto.** Contiene estructura, infraestructura local, seguridad base (Keycloak + JWT) y una prueba de punta a punta (`/ping`). **No hay lógica de negocio ni modelo de datos todavía**: eso se construye en las ramas de cada persona.

## 1. Arquitectura

Arquitectura basada en servicios + API Gateway propio (Spring Boot, sin Spring Cloud Gateway) + eventos asíncronos en topología broker (RabbitMQ).

```
 web-interna (5173) ──┐  JWT (Keycloak, PKCE)
                      ├──► api-gateway (8080) ──► servicio-cientifico (8081) ──► mysql-transaccional (esquema cientifico)
 web-publica (5174) ──┘                          ├► servicio-equipos (8082) ────► mysql-transaccional (esquema equipos)
                                                 ├► servicio-consulta-publica (8083) ► mysql-lectura + redis (caché)
                                                 └► servicio-reportes (8084) ───► mysql-analitica
          api-gateway ──► mysql-gateway (API Keys y límites)

 cientifico, consulta-publica, reportes ◄──► RabbitMQ (exchange topic "biohub.eventos")
 Keycloak (8180): realm "biohub", emite JWT de 5 min con aud=biohub-api
```

- El gateway valida el JWT y **cada servicio lo valida otra vez**.
- Los servicios y las webs se ejecutan en tu máquina. Docker Compose solo levanta la infraestructura (MySQL, Redis, RabbitMQ, Keycloak).
- Documentos del equipo: [docs/equipo/](docs/equipo/) (plan, arquitectura y contrato de API).

## 2. Requisitos

| Herramienta | Versión |
|---|---|
| Git | cualquiera reciente |
| Java (JDK) | 21 |
| Maven | 3.9 o superior |
| Node.js | 22 |
| Docker + Docker Compose | Compose v2 (`docker compose`, con espacio) |

Verifica con: `git --version`, `java -version`, `mvn -version`, `node -v`, `docker compose version`.

**Ubuntu:** para usar Docker sin `sudo`, ejecuta `sudo usermod -aG docker $USER` y **cierra sesión y vuelve a entrar** (o usa `newgrp docker` en esa terminal).

## 3. Estructura del monorepo

```
biohub/
├── pom.xml                     # Maven agregador (Spring Boot 3.5.16, Java 21, dependencias comunes)
├── docker-compose.yml          # Infraestructura local (proyecto Compose "biohub")
├── .env.example                # Plantilla de variables de entorno (el .env real NO se sube)
├── api-gateway/                # API Gateway: enruta /api/<prefijo>/**, valida JWT, CORS
├── servicio-cientifico/        # Módulos registro y ubicacion
├── servicio-equipos/           # Equipos de trabajo
├── servicio-consulta-publica/  # Lectura pública (modelo de lectura + caché Redis)
├── servicio-reportes/          # Reportes y analítica
├── web-interna/                # React 19 + Vite, login con Keycloak (keycloak-js)
├── web-publica/                # React 19 + Vite, portal público sin login
├── infra/
│   ├── keycloak/               # realm-biohub.json (se importa al arrancar Keycloak)
│   └── mysql/transaccional/    # script de init: esquemas y usuarios de MySQL
├── pruebas-carga/              # Reservada para pruebas de carga futuras
└── docs/equipo/                # Plan, arquitectura y contrato de API
```

### Convención de paquetes

```
co.edu.unbosque.biohub.<servicio>.{controller, service, domain, infrastructure, config}
```

Ejemplos: `...biohub.gateway.controller`, `...biohub.cientifico.config`.

### servicio-cientifico: módulos registro y ubicacion

```
co.edu.unbosque.biohub.cientifico
├── registro.{controller,service,domain,infrastructure}   # hallazgos, fichas, historial
├── ubicacion.{controller,service,domain,infrastructure}  # cifrado, llaves, mapa, búsqueda
├── config/                                               # compartido
└── controller/                                           # compartido (PingController)
```

**Regla:** un módulo **no importa clases internas** del otro (`domain`, `infrastructure`, `controller`). Se comunican por una clase pública de `service` o por eventos.

## 4. Cómo levantar todo

Todos los comandos se ejecutan desde la raíz del repo, salvo que se indique otra carpeta.

### a) Crear el `.env`

```bash
cp .env.example .env
```

Abre `.env` y **cambia todas las contraseñas** (`cambiar_...`). La de `KEYCLOAK_TEST_USERS_PASSWORD` debe tener **12 caracteres o más** (política del realm). Una forma de generarlas:

```bash
openssl rand -hex 16
```

No escribas `${...}` dentro del `.env`. El `.env` está en `.gitignore`: nunca lo subas.

### b) Infraestructura con Docker

```bash
docker compose up -d
docker compose ps
```

Repite `docker compose ps` hasta que todos los contenedores estén en `healthy` (son 7). Keycloak tarda unos 30-60 s.

### c) Compilar los servicios

```bash
mvn package                 # compila y corre las pruebas
mvn -DskipTests package     # solo compila
```

### d) Arrancar cada servicio

Un servicio por terminal, **desde la carpeta del servicio**:

```bash
cd api-gateway
mvn spring-boot:run
```

Repite con `servicio-cientifico`, `servicio-equipos`, `servicio-consulta-publica` y `servicio-reportes`. Para usar el jar:

```bash
cd servicio-cientifico
java -jar target/servicio-cientifico-0.0.1-SNAPSHOT.jar
```

**Importante sobre `../.env`:** cada servicio lee las variables con `spring.config.import: optional:file:../.env`. Esa ruta es relativa a la carpeta desde donde ejecutas. Si arrancas desde otra carpeta, no encuentra el `.env` y falla por falta de usuario/contraseña de base de datos (el import es `optional`, así que no avisa).

### e) Arrancar las webs

```bash
cd web-interna
npm install
npm run dev        # http://localhost:5173
```

```bash
cd web-publica
npm install
npm run dev        # http://localhost:5174
```

Los puertos son fijos (`strictPort`): si están ocupados, Vite falla en vez de cambiar de puerto.

### f) Probar

1. Abre http://localhost:5173 e inicia sesión con un usuario de prueba (sección 6). Debe mostrar la respuesta del ping del Servicio Científico.
2. Abre http://localhost:5174 (portal público, sin login).
3. Por consola, pide un token y llama al gateway:

```bash
set -a; . ./.env; set +a    # carga las variables del .env en esta terminal

TOKEN=$(curl -s -X POST "http://localhost:${KEYCLOAK_PORT}/realms/biohub/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=biohub-pruebas \
  -d username=investigador1 --data-urlencode "password=${KEYCLOAK_TEST_USERS_PASSWORD}" \
  | jq -r .access_token)

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/cientifico/ping
```

Respuesta esperada:

```json
{ "servicio": "servicio-cientifico", "usuario": "investigador1",
  "roles": ["investigador_campo"], "fecha": "2026-10-21T23:14:39.749Z" }
```

Requiere `jq` (`sudo apt install jq`). Sin token la respuesta es `401`. El token dura 5 minutos. Otros pings: `/api/gateway/ping`, `/api/equipos/ping`, `/api/publica/ping` (este no exige token), `/api/reportes/ping`. Contrato completo: [docs/equipo/04-contrato-api.md](docs/equipo/04-contrato-api.md).

## 5. Puertos

Todo se publica **solo en 127.0.0.1**: no es accesible desde otras máquinas de la red.

| Componente | Puerto |
|---|---|
| api-gateway | 8080 |
| servicio-cientifico | 8081 |
| servicio-equipos | 8082 |
| servicio-consulta-publica | 8083 |
| servicio-reportes | 8084 |
| web-interna | 5173 |
| web-publica | 5174 |
| keycloak | 8180 (consola admin: http://localhost:8180/admin) |
| mysql-transaccional | 3307 |
| mysql-lectura | 3308 |
| mysql-analitica | 3309 |
| mysql-gateway | 3310 |
| redis | 6379 |
| rabbitmq | 5672 (panel: http://localhost:15672) |

La consola de Keycloak usa `KEYCLOAK_ADMIN_USER` / `KEYCLOAK_ADMIN_PASSWORD` del `.env`; el panel de RabbitMQ usa `RABBITMQ_USER` / `RABBITMQ_PASSWORD`.

## 6. Usuarios de prueba (realm `biohub`)

| Usuario | Rol |
|---|---|
| investigador1 | investigador_campo |
| auditor1 | auditor_cientifico |
| cientifico1 | cientifico_alto_rango |
| analista1 | analista_ambiental |
| admin1 | administrador |

La contraseña de todos es el valor de `KEYCLOAK_TEST_USERS_PASSWORD` **de tu propio `.env`**.

**El realm se importa solo la primera vez.** Si cambias `realm-biohub.json` o la contraseña de prueba, reimporta (borra los datos de Keycloak, que es solo de desarrollo):

```bash
docker compose rm -sf keycloak && docker volume rm biohub_keycloak-data && docker compose up -d keycloak
```

> **Advertencia:** el cliente `biohub-pruebas` (password grant) es **solo para desarrollo**. Debe eliminarse en cualquier entorno que no sea local. La web usa el cliente `web-interna` (Authorization Code + PKCE).

## 7. Reparto por persona

| Integrante | GitHub | Rama | Responsabilidad | Dificultad |
|---|---|---|---|---|
| Juanita Mejía Pineda | [@JuaniMP](https://github.com/JuaniMP) | `feature/ubicacion-consulta-publica` | `servicio-cientifico` módulo **ubicacion** (cifrado, llaves, mapa, búsqueda) + `servicio-consulta-publica` (BD de lectura y caché Redis) + `web-publica` | Alta |
| Santiago Pinzón Vásquez | [@Chocax](https://github.com/Chocax) | `feature/seguridad-acceso` | `api-gateway` (API Keys, rate limiting en `ApiKeyRateLimitFilter`), Keycloak y roles, `web-interna` (login y navegación). **Coordina la infraestructura compartida** | Alta |
| Hernando Javier García Mogollón | [@Nandoski666](https://github.com/Nandoski666) | `feature/registro-cientifico` | `servicio-cientifico` módulo **registro** (hallazgos, fichas, historial). Publica los eventos de hallazgos | Media |
| Nicolás Espinosa Estrada | [@Gattocho888](https://github.com/Gattocho888) | `feature/reportes-equipos` | `servicio-equipos` (gestión de equipos de trabajo) + `servicio-reportes` (consume eventos y arma reportes) | Media-baja |

### Dependencias entre personas (acordar al inicio)

- **Evento "hallazgo registrado"**: lo publica Hernando (registro) y lo consumen Juanita (consulta pública) y Nicolás (reportes). Definan juntos su formato (campos, routing key en `biohub.eventos`) antes de programarlo, y documéntenlo en `docs/equipo/04-contrato-api.md`.
- **Esquema `cientifico`**: lo comparten Hernando (registro) y Juanita (ubicacion). Usen migraciones con versión por fecha (sección 9) y no modifiquen tablas del otro módulo sin avisar.
- **Roles y permisos**: Santiago define qué rol puede hacer qué; los demás protegen sus endpoints con esos roles.

### Zonas compartidas (coordinar antes de tocar)

Las coordina **Santiago**:

- `pom.xml` raíz
- `docker-compose.yml` e `infra/` (MySQL, Keycloak)
- `infra/keycloak/realm-biohub.json`
- `config/` de `servicio-cientifico` (la comparten Hernando y Juanita)

Cualquier cambio en estas zonas se **avisa al equipo** antes de abrir el PR.

## 8. Flujo de ramas

- `main`: solo entregas.
- `develop`: integración.
- `feature/*`: una por persona.

Cada uno trabaja en su rama `feature/*` (ahí puede hacer push libremente) y, cuando algo está listo, abre un **pull request hacia `develop`** y lo fusiona. `develop` pasa a `main` solo en las entregas (PR `develop` → `main`).

`main` y `develop` están **protegidas** en GitHub:
- No se permite push directo (ni siquiera a la dueña del repo): todo entra por pull request.
- No se exige aprobación de otra persona: cada uno puede fusionar su propio PR. Aun así, se recomienda pedirle a alguien que le eche un ojo a los cambios en zonas compartidas.
- No se permite force push ni borrar estas ramas.

Antes de abrir el PR, actualiza tu rama con `develop` y comprueba que `mvn test` (y `npm test` si tocaste una web) pasen.

```bash
git checkout feature/registro-cientifico     # tu rama
git pull origin develop                      # traer lo nuevo de develop (o: git fetch && git rebase origin/develop)
git add <archivos>
git commit -m "feat: registrar hallazgo"
git push origin feature/registro-cientifico
gh pr create --base develop                  # o abre el PR desde la web del repositorio
```

Commits sugeridos (Conventional Commits en español): `feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`.

## 9. Convenciones

- **Migraciones Flyway:** `src/main/resources/db/migration/V<fecha_hora>__descripcion.sql`, con la versión en formato **`AAAAMMDD_HHMM`**, por ejemplo `V20261022_1430__crear_tabla_hallazgo.sql`. Así dos personas que trabajan en el mismo esquema (registro y ubicación comparten `cientifico`) nunca crean la misma versión. Nunca edites una migración ya aplicada: crea una nueva. `V1__base.sql` está vacía. Hibernate usa `ddl-auto: validate`.
- **Bases de datos:** no hay una persona "de la base de datos": cada quien escribe las tablas de su servicio como migraciones en su propio módulo. Los servidores MySQL son los del `docker-compose.yml` y los comparte todo el equipo.
- **Eventos:** exchange de tipo topic `biohub.eventos` en RabbitMQ.
- **Endpoints:** bajo `/api/<prefijo>/` (`gateway`, `cientifico`, `equipos`, `publica`, `reportes`).
- **Seguridad:** cada servicio valida el JWT (firma, `iss`, `exp` y `aud=biohub-api`); los roles llegan como authorities `ROLE_<rol>`.

## 10. Pruebas

```bash
mvn test            # desde la raíz: todos los servicios
cd web-interna && npm test
cd web-publica && npm test
```

## 10b. Despliegue (decidido, pendiente de implementar)

- **Webs** (web-interna y web-publica): **Vercel**, plan gratuito.
- **Backend on-premise en dos máquinas virtuales** (Ubuntu Server, todo en contenedores Docker):
  - **VM de backend** (`biohub-backend`): gateway, servicios, Keycloak, RabbitMQ y Redis.
  - **VM de datos** (`biohub-datos`): los 4 MySQL (y la base de Keycloak en producción).
- **Tailscale** conecta todo por una red privada cifrada:
  - La VM de backend habla con la VM de datos por Tailscale; **solo** ella puede llegar a los MySQL.
  - Los 4 integrantes entran a las VMs (SSH, consola de Keycloak, panel de RabbitMQ, MySQL) por Tailscale, sin abrir puertos en el router.
  - **Tailscale Funnel** publica en internet, con HTTPS y URL fija, **solo** el gateway y el login de Keycloak.
- Los servicios ya leen el host de cada MySQL de variables (`MYSQL_*_HOST`), así que separar las VMs no requiere cambiar código.
- Topología, reglas de seguridad (ACLs de Tailscale) y recursos: [docs/equipo/03-arquitectura.md](docs/equipo/03-arquitectura.md) (decisión D11).

## 11. Solución de problemas

| Problema | Solución |
|---|---|
| Puerto ocupado | Busca el proceso: `ss -ltnp \| grep :8081`. Ciérralo o cambia el puerto en `.env` (los servicios) |
| `permission denied` de Docker | Cierra sesión y entra de nuevo tras `usermod -aG docker`, o ejecuta `newgrp docker` |
| Keycloak no responde al iniciar | Tarda ~30-60 s. Revisa `docker compose ps` y `docker compose logs keycloak` |
| `401` con un token que antes servía | El token dura 5 min. Pide uno nuevo |
| Cambié el realm y no se aplica | El import es solo la primera vez: reimporta (sección 6) |
| El servicio no arranca y no encuentra el `.env` o falla el acceso a la BD | Ejecútalo desde la carpeta del servicio (lee `../.env`) |
| `docker compose up` dice "Falta en .env" | Falta esa variable en tu `.env`: compárala con `.env.example` |
| No puedo llamar a un servicio (8081-8084) desde otra máquina o contenedor | A propósito: escuchan solo en `127.0.0.1` para obligar a entrar por el gateway. Para contenedores usa `SERVER_ADDRESS=0.0.0.0` dentro de una red interna |
| MySQL init falla con "caracteres no permitidos" | Las contraseñas de `.env` solo admiten letras, números y `_.@#%+=-` |
| Cambié contraseñas de MySQL en `.env` y ya no conecta | Los usuarios se crean solo la primera vez: `docker compose down -v` (borra los datos locales) y `docker compose up -d` |

## 12. Documentación del equipo

- [docs/equipo/00-plan.md](docs/equipo/00-plan.md): plan y resultados de verificación.
- [docs/equipo/03-arquitectura.md](docs/equipo/03-arquitectura.md): arquitectura y decisiones.
- [docs/equipo/04-contrato-api.md](docs/equipo/04-contrato-api.md): contrato de API.
- [docs/equipo/06-plan-pruebas.md](docs/equipo/06-plan-pruebas.md) y [07-reporte-qa.md](docs/equipo/07-reporte-qa.md): pruebas y resultados.
- [docs/equipo/08-reporte-seguridad.md](docs/equipo/08-reporte-seguridad.md): auditoría de seguridad y **requisitos de seguridad para las próximas historias** (léelo antes de empezar tu historia).

## 13. Autores y licencia

- Autores: Juanita Mejía Pineda, Santiago Pinzón Vásquez, Hernando Javier García Mogollón y Nicolás Espinosa Estrada (Ingeniería de Software 2, Universidad El Bosque).
- Licencia: pendiente de definir (el repositorio aún no incluye archivo `LICENSE`).
