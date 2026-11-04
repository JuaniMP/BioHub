# 09 - Despliegue y ejecución (MySQL en Aiven + infraestructura local en Docker)

Iteración 15: se retiró la red privada anterior y toda la infraestructura de VMs KVM que dependía de ella (`deploy/`, guía de acceso). Las contraseñas **nunca** se suben al repositorio.

## 1. Topología

```
 Tu máquina (cada integrante)                          Aiven (console.aiven.io), D39
 +----------------------------------------+            +-------------------------------------+
 | Servicios Spring Boot y webs           |            | Servicio MySQL de ESCRITURA         |
 | (fuera de Docker)                      |            |   bases: cientifico, equipos        |
 |   servicio-cientifico  --- SSL ------------------->|   (cuenta de A)                     |
 |   servicio-equipos     --- SSL ------------------->|                                     |
 |                                        |            +-------------------------------------+
 |   servicio-consulta-publica -- SSL ------------+    | Servicio MySQL de LECTURA           |
 |   servicio-reportes    --- SSL ----------------+--->|   bases: consulta_publica,          |
 |   api-gateway          --- SSL ----------------+    |   reportes, gateway (cuenta de B)   |
 |                                        |            +-------------------------------------+
 | Docker Compose (docker-compose.yml)    |
 |   RabbitMQ, Redis, Keycloak            |            Cada servicio apunta al suyo con
 |   (+ 4 MySQL locales, solo perfil      |            <SERVICIO>_DB_HOST / <SERVICIO>_DB_PORT
 |    "local")                            |
 +----------------------------------------+
```

| Componente | Dónde | Notas |
|---|---|---|
| MySQL de `servicio-cientifico` (`cientifico`) y `servicio-equipos` (`equipos`) | Aiven, servicio de **escritura** (perfil `aiven`) o Docker (perfil `local`) | D39: una base y dos usuarios (`*_app`, `*_migr`) por servicio |
| MySQL de `servicio-consulta-publica` (`consulta_publica`), `servicio-reportes` (`reportes`) y `api-gateway` (`gateway`) | Aiven, servicio de **lectura** (perfil `aiven`) o Docker (perfil `local`) | El gateway tiene `datasource` y Flyway configurados (API Keys y límites) y su `V1__base.sql` vacía: se aprovisiona su base y queda en lectura, como dice D39 (patrón de lectura: tráfico de consulta pública, sin escrituras pesadas) |
| RabbitMQ, Redis, Keycloak | Docker local (`docker-compose.yml`) | D-I15-3. Keycloak sigue en `start-dev` con H2 (no es producción) |

Ya no hay VMs ni red privada: la app solo está disponible mientras corras los servicios en tu máquina. Que sean dos servicios de Aiven es solo configuración: el código no cambia (solo `*_DB_HOST/PORT`).

## 2. Perfiles Spring

Se elige con `SPRING_PROFILES_ACTIVE`. **Sin perfil el servicio no arranca**: no hay URL ni credenciales por defecto. Los cinco servicios con base de datos (`servicio-cientifico`, `servicio-equipos`, `servicio-consulta-publica`, `servicio-reportes`, `api-gateway`) tienen el mismo patrón.

| Perfil | Base de datos | Archivo |
|---|---|---|
| `local` | MySQL del `docker-compose.yml` (puertos 3307 científico y equipos, 3308 consulta pública, 3309 reportes, 3310 gateway), credenciales del `.env` de la raíz | `application-local.yml` |
| `aiven` | MySQL en Aiven, SSL obligatorio con verificación del CA | `application-aiven.yml` |
| (sin perfil) | Hay que definir `<SERVICIO>_DB_URL` completa, más `<SERVICIO>_DB_USER` y `<SERVICIO>_DB_PASSWORD` | `application.yml` |

Los perfiles se pueden combinar con `dev` (p. ej. `local,dev` para la llave de ubicaciones efímera).

### Variables por servicio

Prefijos: `CIENTIFICO`, `EQUIPOS` (servicio de escritura) y `CONSULTA`, `REPORTES`, `GATEWAY` (servicio de lectura). Para el prefijo `<P>`:

| Variable (perfil `aiven`) | Significado |
|---|---|
| `<P>_DB_HOST`, `<P>_DB_PORT` | Host y puerto **del servicio de Aiven de esa base** (Overview -> Connection information; el puerto no es 3306) |
| `<P>_DB_NAME` | Opcional; por defecto el nombre de la base (`cientifico`, `equipos`, `consulta_publica`, `reportes`, `gateway`) |
| `<P>_DB_USER`, `<P>_DB_PASSWORD` | Usuario `*_app` (solo DML) |
| `<P>_MIGR_USER`, `<P>_MIGR_PASSWORD` | Usuario `*_migr` que usa Flyway (DDL, SEC-11). Si faltan, Flyway usa el `*_app` y falla por falta de `CREATE` |
| `AIVEN_TRUSTSTORE_PATH`, `AIVEN_TRUSTSTORE_PASSWORD`, `AIVEN_SSL_MODE` (opcional) | Truststore PKCS12 con el CA (4.5) y modo SSL (`VERIFY_CA` o `VERIFY_IDENTITY`) |
| `<P>_DB_CONNECT_TIMEOUT_MS`, `<P>_DB_SOCKET_TIMEOUT_MS` | Opcionales (3000 / 30000) |

Perfil `local` (del `.env` de la raíz): `MYSQL_TRANSACCIONAL_HOST/PORT` + `MYSQL_CIENTIFICO_USER/PASSWORD` (científico) o `MYSQL_EQUIPOS_USER/PASSWORD` (equipos); `MYSQL_LECTURA_*` (consulta pública); `MYSQL_ANALITICA_*` (reportes); `MYSQL_GATEWAY_*` (gateway).

Además, los de siempre: `RABBITMQ_*`, `REDIS_PASSWORD`, `KEYCLOAK_*`, `UBICACION_CLAVES`, `UBICACION_CLAVE_ACTIVA` (ver `.env.example` de la raíz y de cada servicio). Cada servicio lee `../.env` (raíz) y luego `./.env` (propio) cuando se ejecuta desde su carpeta; el último gana. Plantillas: `.env.example` en cada carpeta de servicio. Sin comentarios al final de línea (se leen como `.properties`).

## 3. Ejecución local (perfil `local`)

```bash
cp .env.example .env                  # y cambia las contraseñas
docker compose up -d                  # 4 MySQL, Redis, RabbitMQ, Keycloak (espera "healthy")
cd servicio-cientifico
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
# en otra terminal (igual para servicio-equipos, servicio-consulta-publica, servicio-reportes y api-gateway)
cd servicio-consulta-publica
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Flyway migra al arrancar (`flyway_schema_history` en cada base). Si cambias contraseñas de MySQL en `.env` con volúmenes ya creados, ver `README.md` sección 11.

## 4. Aiven (perfil `aiven`)

Guía para quien nunca usó Aiven. Hay **dos servicios MySQL** (D39) porque el plan gratuito da un MySQL por organización: el de **escritura** (`cientifico`, `equipos`) y el de **lectura** (`consulta_publica`, `reportes`, `gateway`). El segundo vive en la cuenta de otro integrante.

### 4.1 Quién crea qué

| Qué | Quién | Script | Credenciales que entrega | Quién las recibe |
|---|---|---|---|---|
| Servicio de **escritura** (`cientifico`, `equipos`) | **MARCADOR: Juanita confirma si crea este o el de lectura** | `deploy/aiven/01-escritura-bases-y-usuarios.sql.example` | `cientifico_app/_migr` | Juanita (`servicio-cientifico` ubicación) y Hernando (registro, misma base) |
| | | | `equipos_app/_migr` | Nicolás (`servicio-equipos`) |
| Servicio de **lectura** (`consulta_publica`, `reportes`, `gateway`) | **MARCADOR: el otro integrante (cuenta distinta), por definir** | `deploy/aiven/02-lectura-bases-y-usuarios.sql.example` | `consulta_app/_migr` | Juanita (`servicio-consulta-publica`) |
| | | | `reportes_app/_migr` | Nicolás (`servicio-reportes`) |
| | | | `gateway_app/_migr` | Santiago (`api-gateway`) |

Cada integrante solo necesita host, puerto, CA y las credenciales de **su** base. Nadie más recibe la contraseña de `avnadmin`; solo la persona dueña de cada servicio la usa, una vez, para ejecutar su script. Las credenciales se entregan por un canal privado, nunca por el repo ni por un chat de grupo.

### 4.2 Crear el servicio (la persona dueña de cada uno, una sola vez)

1. Entra a https://console.aiven.io y crea una cuenta (correo y verificación). Al registrarte Aiven crea una organización y un proyecto; puedes usar el que viene por defecto o crear uno con **Create project** (por ejemplo `biohub-escritura` o `biohub-lectura`).
2. Dentro del proyecto pulsa **Create service** y elige **MySQL**.
3. **Plan**: *Free* (gratis, sin tarjeta). **Región**: la más cercana a quienes ejecutan los servicios (la latencia pesa en la conexión, ver 4.7). **Nombre**: `biohub-escritura` o `biohub-lectura`.
4. Pulsa **Create service** y espera a que el estado pase a **Running** (unos minutos; el indicador deja de estar amarillo).
5. En **Overview -> Connection information** anota **Host**, **Port** (distinto de 3306), **User** (`avnadmin`) y **Password**, y pulsa **Download** en *CA certificate* para guardar `ca.pem`. Ponlo en `certs/` del repo (`certs/`, `*.pem` y `*.p12` están en `.gitignore`).
6. En **Overview -> Allowed IP addresses**, por defecto Aiven acepta cualquier IP. Restríngelo a las IP públicas de quienes conectan (cada integrante busca la suya, p. ej. con `curl ifconfig.me`, y se la pasa al dueño; se actualiza cuando cambie). Sin VPN, esto y la contraseña son la barrera de red. Si no puedes conectar luego, revisa esta lista primero.

Plan gratuito: Aiven puede apagar el servicio por inactividad o cambiarlo de región sin avisar (D35). Si pasa, vuelve a encenderlo en la consola y repite 4.3 solo si perdió los usuarios.

### 4.3 Crear bases y usuarios (la persona dueña, una vez por servicio)

Necesitas el cliente `mysql` (paquete `mysql-client` o `mariadb-client`).

```bash
cd deploy/aiven
# Escritura:  cp 01-escritura-bases-y-usuarios.sql.example 01-escritura-bases-y-usuarios.sql
# Lectura:    cp 02-lectura-bases-y-usuarios.sql.example   02-lectura-bases-y-usuarios.sql
cp 01-escritura-bases-y-usuarios.sql.example 01-escritura-bases-y-usuarios.sql

# 1) Cambia cada CAMBIAR_... por una contraseña distinta (solo [A-Za-z0-9]): openssl rand -hex 24
# 2) Comprueba que no quedó ninguno (debe salir vacío):
grep -n CAMBIAR_ 01-escritura-bases-y-usuarios.sql

# 3) Ejecuta como avnadmin con el CA verificado (la contraseña de avnadmin se escribe en el prompt)
mysql --ssl-mode=VERIFY_CA --ssl-ca=../../certs/ca.pem -h <HOST> -P <PUERTO> -u avnadmin -p < 01-escritura-bases-y-usuarios.sql

# 4) Guarda las contraseñas para entregarlas a cada integrante y borra la copia con claves
rm 01-escritura-bases-y-usuarios.sql
```

La copia con claves está en `deploy/aiven/.gitignore`; la plantilla (`.example`) es lo único que se sube. El script es idempotente: repetirlo rota contraseñas y recalcula permisos. Termina con `SHOW GRANTS` de cada usuario: deben verse solo `SELECT, INSERT, UPDATE, DELETE` (`*_app`) o esos más `CREATE, ALTER, DROP, INDEX, REFERENCES` (`*_migr`), sobre su propia base, sin `ALL PRIVILEGES` ni `WITH GRANT OPTION`. `avnadmin` nunca se usa en una aplicación.

Aiven MySQL exige clave primaria en todas las tablas (`sql_require_primary_key`) y no admite `CREATE TABLE ... SELECT`, triggers ni `SET GLOBAL`: las migraciones deben pasar `deploy/aiven/verificar-migraciones.sh`.

### 4.4 Probar la conexión (opcional, rápido)

```bash
mysql --ssl-mode=VERIFY_CA --ssl-ca=certs/ca.pem -h <HOST> -P <PUERTO> -u cientifico_app -p cientifico -e "SELECT 1"
```

Con el usuario de otra base debe fallar con `Access denied`; ese es el aislamiento esperado.

### 4.5 Truststore PKCS12 con keytool (cada integrante, una vez)

MySQL Connector/J 9.7 (el de Spring Boot 3.5) **no lee PEM** para `VERIFY_CA`/`VERIFY_IDENTITY`: necesita un truststore Java. Cada proyecto de Aiven tiene su propio CA, así que quien use bases de **los dos** servicios (están en cuentas distintas) importa **ambos** `ca.pem` con alias distintos en un solo `.p12`:

```bash
mkdir -p certs                         # certs/ y *.pem / *.p12 están en .gitignore
# ca.pem del servicio de escritura y ca-lectura.pem del de lectura (renómbralos al descargarlos)
keytool -importcert -noprompt -alias aiven-escritura -file certs/ca-escritura.pem \
        -keystore certs/aiven-ca.p12 -storetype PKCS12 -storepass <PASSWORD_TRUSTSTORE>
keytool -importcert -noprompt -alias aiven-lectura   -file certs/ca-lectura.pem \
        -keystore certs/aiven-ca.p12 -storetype PKCS12 -storepass <PASSWORD_TRUSTSTORE>
keytool -list -keystore certs/aiven-ca.p12 -storepass <PASSWORD_TRUSTSTORE>   # 1 entrada por servicio que uses
```

Si solo usas un servicio, importa un único CA. `<PASSWORD_TRUSTSTORE>` (mínimo 6 caracteres, solo letras y números porque va dentro de la URL JDBC) es `AIVEN_TRUSTSTORE_PASSWORD`; no es secreto (protege certificados públicos). `AIVEN_TRUSTSTORE_PATH` es la ruta **absoluta** de `aiven-ca.p12`. Si Aiven rota un CA, vuelve a descargarlo y repite.

### 4.6 Llenar el `.env` y ejecutar con el perfil `aiven`

Cada integrante copia el `.env.example` de **su** servicio y completa los `CAMBIAR` con host, puerto y credenciales de su base:

```bash
cp servicio-equipos/.env.example servicio-equipos/.env        # ejemplo: Nicolás
# edita: EQUIPOS_DB_HOST/PORT (servicio de escritura), EQUIPOS_DB_USER/PASSWORD, EQUIPOS_MIGR_USER/PASSWORD,
#        AIVEN_TRUSTSTORE_PATH, AIVEN_TRUSTSTORE_PASSWORD
docker compose up -d rabbitmq redis keycloak                  # no hace falta levantar los MySQL locales
cd servicio-equipos
SPRING_PROFILES_ACTIVE=aiven mvn spring-boot:run
```

El `.env` de la carpeta del servicio gana sobre el de la raíz. La raíz (`.env.example`, sección Aiven) lista los pares `*_DB_HOST/PORT` agrupados por servicio de Aiven. Con `AIVEN_SSL_MODE` sin definir se usa `VERIFY_IDENTITY` (valida además el nombre del host); `VERIFY_CA` es el mínimo aceptado. Nunca `PREFERRED`/`REQUIRED` (cifran sin verificar al servidor).

URL JDBC que arma el perfil (sin secretos; `<...>` son variables):

```
jdbc:mysql://<P>_DB_HOST:<P>_DB_PORT/<DB_NAME>?sslMode=VERIFY_IDENTITY
   &trustCertificateKeyStoreUrl=file:<AIVEN_TRUSTSTORE_PATH>&trustCertificateKeyStoreType=PKCS12
   &trustCertificateKeyStorePassword=<AIVEN_TRUSTSTORE_PASSWORD>&connectTimeout=3000&socketTimeout=30000
   &connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&rewriteBatchedStatements=true
```

**Cómo comprobar que conectó**

1. En el log de arranque, Flyway dice `Successfully applied N migration(s) to schema "<base>"` (primera vez) o `Schema "<base>" is up to date. No migration necessary.` y no hay `Communications link failure`, `Access denied` ni `PKIX path building failed`.
2. `curl http://127.0.0.1:<puerto>/actuator/health` devuelve `{"status":"UP"}`.
3. Con `mysql` (4.4) y el usuario `*_app` de tu base:
   ```sql
   SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;
   ```
   Debe haber al menos `1 | base | 1`, todas con `success = 1`.

Errores frecuentes: `Access denied` (usuario/contraseña de otra base o IP no permitida), `PKIX path building failed` (el truststore no tiene el CA de **ese** servicio), `CREATE command denied` (faltan `*_MIGR_USER/PASSWORD`), `Unknown database` (no se ejecutó el script de ese servicio).

### 4.7 Pool de conexiones (Hikari) en `aiven`

| Parámetro | `local` / común | `aiven` | Motivo |
|---|---|---|---|
| `connection-timeout` científico, equipos, reportes | 30 s (defecto) | 10 s | Handshake TCP+TLS por internet |
| `connection-timeout` api-gateway | 30 s (defecto) | 3 s | Tráfico público: falla rápido con 503, con 1 s más por el handshake TLS |
| `connection-timeout` consulta pública | 2 s | **3 s** | Sigue fallando rápido con 503 (J10, RNF5); se sube 1 s solo por el handshake TLS. Si el 503 por latencia es frecuente, cambiar de región en Aiven antes de subir más este valor |
| `maximum-pool-size` | 10 (defecto) | 5 | Los planes de Aiven limitan conexiones (`max_connections` 76 por servicio) y las comparten los servicios |
| `MAX_USER_CONNECTIONS` (script SQL) | | 20 `cientifico_app`, `equipos_app`, `consulta_app`; 10 `reportes_app`, `gateway_app`; 3 cada `*_migr` | Suma 46 en escritura y 49 en lectura (< 76) |
| `max-lifetime` / `keepalive-time` | defecto | 10 min / 2 min | Reciclar conexiones antes de que un firewall/proxy las corte |

## 5. Pruebas

```bash
mvn -q -B -pl servicio-cientifico,servicio-consulta-publica -am test    # usan Testcontainers (MySQL, Redis, RabbitMQ): requiere Docker
(cd web-publica && npm ci && npm test)
(cd web-interna && npm ci && npm test)
```

Las pruebas Java definen su propia base (`spring.datasource.*` por `@DynamicPropertySource`) y no usan Aiven ni el MySQL del compose.

## 6. Operación y rollback

- **Rollback de código:** volver al commit anterior y reiniciar el servicio. Las migraciones Flyway ya aplicadas **no se revierten solas**: si una fue destructiva, restaurar desde los backups de Aiven (pestaña *Backups*; Aiven hace copias automáticas, también se puede crear un *fork* del servicio desde una copia). Nunca editar una migración ya aplicada: crear una nueva.
- **Volver al MySQL local:** `SPRING_PROFILES_ACTIVE=local` (los datos locales y los de Aiven son independientes).
- **Rotar credenciales:** cambiar la contraseña del usuario en Aiven (*Users*), actualizar el `.env` de cada integrante y reiniciar.
- **Rotar el CA:** repetir 4.2.
- **Datos locales:** los volúmenes de Docker sobreviven a `docker compose down`; `down -v` los borra.

## 7. Servicios `servicio-equipos`, `servicio-reportes` y `api-gateway`

Ya tienen el mismo patrón que `servicio-cientifico` y `servicio-consulta-publica` (solo configuración, sin tocar Java ni migraciones): `application.yml` toma `<P>_DB_URL/USER/PASSWORD` y `<P>_MIGR_USER/PASSWORD`, `application-local.yml` arma la URL del MySQL del compose (con las variables `MYSQL_*` de siempre) y `application-aiven.yml` la del Aiven. Cambio de comportamiento: **ahora hay que elegir perfil** (`SPRING_PROFILES_ACTIVE=local` o `aiven`); sin perfil no arrancan. Los tests (`@WebMvcTest` y los de reportes con propiedades propias) no dependen de la base.

Pendiente de sus dueños (Nicolás: equipos y reportes; Santiago: gateway): crear sus usuarios con el script del servicio de Aiven que les toca (4.1), probar `SPRING_PROFILES_ACTIVE=aiven` con su `.env` y comprobar sus migraciones con `deploy/aiven/verificar-migraciones.sh`. Mantener la convención de Flyway (`VAAAAMMDD_HHMM__...`, `out-of-order`; D-I15-1).

## 8. Archivos relevantes

| Ruta | Función |
|---|---|
| `docker-compose.yml`, `.env.example` | MySQL locales, Redis, RabbitMQ, Keycloak |
| `deploy/aiven/01-escritura-bases-y-usuarios.sql.example`, `02-lectura-bases-y-usuarios.sql.example` | Bases y usuarios de cada servicio de Aiven (D39) |
| `deploy/aiven/verificar-migraciones.sh` | Revisa que las migraciones sirvan en Aiven |
| `servicio-*/src/main/resources/application{,-local,-aiven}.yml`, `api-gateway/...` | Perfiles de base de datos |
| `<servicio>/.env.example` (5 servicios) y `.env.example` de la raíz | Variables por servicio |
| `<servicio>/Dockerfile` | Imagen por servicio (JRE 21, usuario no root uid 10001); opcional, no se usa en el flujo diario |

## 9. Limitaciones conocidas

- Keycloak en `start-dev` con H2 y usuarios de prueba: no es producción.
- Sin HTTPS entre tus servicios y Keycloak/RabbitMQ locales (todo en `127.0.0.1`).
- El tráfico a Aiven va cifrado y verificado, pero depende de la lista de IP permitidas y de la contraseña del usuario.
- Con dos servicios de Aiven en cuentas distintas, cada integrante necesita el CA de cada servicio que use en su truststore (4.5).
- No hay despliegue de las webs (Vercel) en esta iteración.

## 10. CI (GitHub Actions) y pruebas de API con Bruno

Workflow: `.github/workflows/ci.yml`, en cada `push` y `pull_request` (permisos `contents: read`, cancela ejecuciones viejas de la misma rama). No usa secretos de GitHub: los valores del job `bruno` son ficticios y existen solo en el job.

| Job | Qué hace | Falla si |
|---|---|---|
| `frontend` | `web-publica`: Node 22, `npm ci`, `npm run build`, `npm test` (vitest), `npm audit --omit=dev --audit-level=high` | build, tests o auditoría fallan |
| `backend` (matriz) | `mvn -B -pl <servicio> -am verify` para `servicio-cientifico` y `servicio-consulta-publica`, Java 21 Temurin, caché Maven. Los tests usan Testcontainers: `ubuntu-latest` ya trae Docker. Para sumar servicios, añadirlos a la matriz (hay un comentario en el archivo) | compilación o tests fallan |
| `bruno` | MySQL 8.4, RabbitMQ 4.1 y Redis 7.4 como service containers; Keycloak 26.8.1 con `docker run` e importando `infra/keycloak/realm-biohub.json`; crea las bases con `infra/ci/init-mysql.sql`; arranca los jars con el perfil `local` (Flyway migra); siembra hallazgos con `infra/ci/SembrarHallazgos.java`; espera los healthchecks de actuator; ejecuta `@usebruno/cli@4.3.0 run --env ci` | cualquier test de la colección falla |

El job `bruno` sube el artefacto `bruno-resultados` (JUnit XML, informe HTML sin cuerpos ni cabeceras, y los logs de los dos servicios).

### 10.1 Colección Bruno (`bruno/`)

- `bruno.json`, `collection.bru`, `environments/ci.bru`, `environments/local.bru` (sin secretos: `passwordUsuarios` y `rabbitPassword` son `vars:secret` y se pasan con `--env-var`).
- Carpetas, en este orden: `auth` (token de Keycloak por password grant con el cliente `biohub-pruebas` y los usuarios `cientifico1`, `investigador1`, `admin1`), `setup` (publica eventos `hallazgo.*` en el exchange `biohub.eventos` con la API HTTP de management de RabbitMQ, porque el publicador de registro aún no existe), `consulta-publica` (18 requests) y `ubicacion` (17 requests). Cada `.bru` lleva en `docs` el ID del requisito.
- Los datos de `servicio-cientifico` (no hay otro punto de entrada que el módulo registro) se siembran con `infra/ci/SembrarHallazgos.java`, que cifra las coordenadas igual que el servicio. Es solo para CI/pruebas: no es una migración ni va en los jars.
- Las peticiones van directo a los servicios (8081 y 8083), no por el gateway.

Ejecutarla en local (con `docker compose up -d` y los dos servicios arriba; usa una base descartable, no la de desarrollo):

```bash
# 1) bases descartables y siembra (el MySQL científico ya tiene las migraciones tras arrancar el servicio)
#    arrancar los servicios con CIENTIFICO_DB_URL / CONSULTA_DB_URL a esas bases y UBICACION_CLAVES=v1:<clave de prueba>
java infra/ci/SembrarHallazgos.java <clave-base64-de-prueba> v1 | mysql ... cientifico_bruno
# 2) colección
cd bruno
npx @usebruno/cli@4.3.0 run --env local \
  --env-var passwordUsuarios="$KEYCLOAK_TEST_USERS_PASSWORD" --env-var rabbitPassword="$RABBITMQ_PASSWORD"
```

Los eventos de la carpeta `setup` quedan también en `reportes.eventos` y uno malo en `consulta-publica.eventos.dlq`; usar solo en el RabbitMQ local o efímero, nunca en uno compartido.

### 10.2 Variables del job `bruno`

| Variable | Valor en CI | Uso |
|---|---|---|
| `CI_USUARIOS_PRUEBA_PASSWORD` | ficticio | `KEYCLOAK_TEST_USERS_PASSWORD` del realm y `passwordUsuarios` de Bruno |
| `CI_RABBIT_PASSWORD` | ficticio | usuario `biohub_ci` de RabbitMQ y `rabbitPassword` de Bruno |
| `CI_MYSQL_ROOT_PASSWORD`, `CI_KEYCLOAK_ADMIN_PASSWORD` | ficticios | root de MySQL y admin de Keycloak del job |
| `CI_UBICACION_CLAVE` | clave AES-256 ficticia | `UBICACION_CLAVES` (`v1`) y la siembra |

Los usuarios de MySQL (`cientifico_ci`, `consulta_ci`) y sus contraseñas ficticias están en `infra/ci/init-mysql.sql`. Si algún día el job debe usar un secreto real (por ejemplo un registry privado), definirlo en Settings > Secrets and variables > Actions y leerlo con `secrets.*`; nunca en el repo.

### 4.5 Verificación contra Aiven real (2026-11-04)
- Servicio de escritura creado (MySQL 8.4.11, `sql_require_primary_key=ON`, `sql_mode` con `ANSI`). Conexión con `--ssl-mode=VERIFY_CA` OK.
- `01-escritura-bases-y-usuarios.sql` ejecutado dos veces (idempotente). Corrección: en Aiven `REVOKE ALL PRIVILEGES, GRANT OPTION FROM u` falla con ERROR 3879 (sin permisos sobre `sys`); los scripts ahora revocan solo sobre la base propia con `REVOKE IF EXISTS ... IGNORE UNKNOWN USER`.
- Permisos resultantes: `*_app` solo DML y `*_migr` DML + DDL, cada uno solo en su base; `equipos_app` no ve `cientifico` (ERROR 1044).
- `servicio-cientifico` con `SPRING_PROFILES_ACTIVE=aiven`: Flyway aplicó 3 migraciones con `cientifico_migr` y el servicio arrancó.
- Sin cliente `mysql` instalado se puede usar Docker: `docker run --rm -it -v "$PWD/certs:/certs:ro" mysql:8.4 mysql --ssl-mode=VERIFY_CA --ssl-ca=/certs/ca.pem -h <HOST> -P <PUERTO> -u avnadmin -p`.
- La llave `UBICACION_CLAVES` usada contra Aiven debe ser la misma para todos los que usen la base `cientifico` y no puede perderse (sin ella las coordenadas cifradas son irrecuperables).
- Servicio de lectura creado en otro proyecto de Aiven, distinto al de escritura (el plan Free admite un solo MySQL por proyecto u organización). `02-lectura-bases-y-usuarios.sql` ejecutado dos veces; `reportes_app` no ve `consulta_publica` (ERROR 1044). `servicio-consulta-publica` con perfil `aiven`: Flyway aplicó 3 migraciones, `GET /api/publica/fichas` responde 200.
- Cada servicio de Aiven tiene **su propio CA** (`ca.pem` distinto por proyecto): los servicios que van al de escritura usan un truststore hecho con el CA de escritura y los de lectura con el de lectura.
