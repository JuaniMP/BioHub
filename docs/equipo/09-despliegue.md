# 09 - Despliegue y ejecución (MySQL en Aiven + infraestructura local en Docker)

Iteración 15: se retiró la red privada anterior y toda la infraestructura de VMs KVM que dependía de ella (`deploy/`, guía de acceso). Las contraseñas **nunca** se suben al repositorio.

## 1. Topología

```
 Tu máquina (cada integrante)                                  Aiven (console.aiven.io)
 +------------------------------------------------------+      +--------------------------+
 | Servicios Spring Boot y webs (fuera de Docker)       |      | 1 servicio MySQL         |
 |   servicio-cientifico ----------------- SSL, VERIFY_CA ---> |  base cientifico         |
 |   servicio-consulta-publica ----------- SSL, VERIFY_CA ---> |  base consulta_publica   |
 |                                                      |      |  (un usuario por base)   |
 | Docker Compose (docker-compose.yml de la raíz)       |      +--------------------------+
 |   RabbitMQ, Redis, Keycloak                          |
 |   (+ 4 MySQL locales, solo para el perfil "local")   |
 +------------------------------------------------------+
```

| Componente | Dónde | Notas |
|---|---|---|
| MySQL de `servicio-cientifico` y `servicio-consulta-publica` | Aiven (perfil `aiven`) o Docker (perfil `local`) | D-I15-2: un servicio MySQL, una base y un usuario por servicio; la URL es independiente por servicio, así que se pueden separar en dos servicios de Aiven cambiando solo `HOST`/`PORT` |
| RabbitMQ, Redis, Keycloak | Docker local (`docker-compose.yml`) | D-I15-3. Keycloak sigue en `start-dev` con H2 (no es producción) |
| `api-gateway`, `servicio-equipos`, `servicio-reportes` | Sin cambios (siguen con los MySQL del compose); ver sección 7 | |

Ya no hay VMs ni red privada: la app solo está disponible mientras corras los servicios en tu máquina.

## 2. Perfiles Spring

Se elige con `SPRING_PROFILES_ACTIVE`. **Sin perfil el servicio no arranca**: no hay URL ni credenciales por defecto.

| Perfil | Base de datos | Archivo |
|---|---|---|
| `local` | MySQL del `docker-compose.yml` (`localhost:3307` científico, `localhost:3308` consulta pública), credenciales del `.env` de la raíz | `application-local.yml` |
| `aiven` | MySQL en Aiven, SSL obligatorio con verificación del CA | `application-aiven.yml` |
| (sin perfil) | Hay que definir `CIENTIFICO_DB_URL` / `CONSULTA_DB_URL` completas, más `*_DB_USER` y `*_DB_PASSWORD` | `application.yml` |

Los perfiles se pueden combinar con `dev` (p. ej. `local,dev` para la llave de ubicaciones efímera).

### Variables por servicio

| Servicio | Variables (perfil `aiven`) | Perfil `local` (del `.env` raíz) |
|---|---|---|
| `servicio-cientifico` | `CIENTIFICO_DB_HOST`, `CIENTIFICO_DB_PORT`, `CIENTIFICO_DB_NAME` (por defecto `cientifico`), `CIENTIFICO_DB_USER`, `CIENTIFICO_DB_PASSWORD`, `AIVEN_TRUSTSTORE_PATH`, `AIVEN_TRUSTSTORE_PASSWORD`, `AIVEN_SSL_MODE` (opcional) | `MYSQL_TRANSACCIONAL_HOST/PORT`, `MYSQL_CIENTIFICO_USER/PASSWORD` |
| `servicio-consulta-publica` | `CONSULTA_DB_HOST`, `CONSULTA_DB_PORT`, `CONSULTA_DB_NAME` (por defecto `consulta_publica`), `CONSULTA_DB_USER`, `CONSULTA_DB_PASSWORD`, `AIVEN_TRUSTSTORE_PATH`, `AIVEN_TRUSTSTORE_PASSWORD`, `AIVEN_SSL_MODE` (opcional), `CONSULTA_DB_CONNECT_TIMEOUT_MS`, `CONSULTA_DB_SOCKET_TIMEOUT_MS` (opcionales) | `MYSQL_LECTURA_HOST/PORT`, `MYSQL_LECTURA_USER/PASSWORD` |
| Ambos (opcional, SEC-11) | `CIENTIFICO_MIGR_USER/PASSWORD`, `CONSULTA_MIGR_USER/PASSWORD`: usuario de Flyway distinto del de ejecución. Si no se definen, Flyway usa el del datasource | |

Además, los de siempre: `RABBITMQ_*`, `REDIS_PASSWORD`, `KEYCLOAK_*`, `UBICACION_CLAVES`, `UBICACION_CLAVE_ACTIVA` (ver `.env.example` de la raíz y de cada servicio). Cada servicio lee `../.env` (raíz) y luego `./.env` (propio) cuando se ejecuta desde su carpeta; el último gana. Plantillas: `servicio-cientifico/.env.example`, `servicio-consulta-publica/.env.example`. Sin comentarios al final de línea (se leen como `.properties`).

## 3. Ejecución local (perfil `local`)

```bash
cp .env.example .env                  # y cambia las contraseñas
docker compose up -d                  # 4 MySQL, Redis, RabbitMQ, Keycloak (espera "healthy")
cd servicio-cientifico
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
# en otra terminal
cd servicio-consulta-publica
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Flyway migra al arrancar (`flyway_schema_history` en cada base). Si cambias contraseñas de MySQL en `.env` con volúmenes ya creados, ver `README.md` sección 11.

## 4. Aiven (perfil `aiven`)

### 4.1 Preparar Aiven (una vez, la persona dueña de la cuenta)

1. En https://console.aiven.io: **Create service -> MySQL**, plan y región (la más cercana; la latencia afecta al fail-fast, ver 4.4). Esperar a que esté en estado *Running*.
2. **Databases** (pestaña del servicio): crear `cientifico` y `consulta_publica`. (`defaultdb` es la que trae Aiven; no la usamos.)
3. **Users**: usar `avnadmin` (administrador) **solo** para crear bases y usuarios. Crear un usuario por servicio, p. ej. `cientifico_app` y `consulta_app`, con contraseña generada. Luego dar permisos mínimos conectándote con `avnadmin` (ver `mysql` abajo):
   ```sql
   GRANT ALL PRIVILEGES ON cientifico.*       TO 'cientifico_app'@'%';
   GRANT ALL PRIVILEGES ON consulta_publica.* TO 'consulta_app'@'%';
   ```
   `ALL` sobre su propia base es necesario porque Flyway ejecuta DDL con ese usuario. Para separar migración y ejecución (SEC-11) crea además `cientifico_migr` (ALL) y deja `cientifico_app` con `SELECT, INSERT, UPDATE, DELETE`, y define `CIENTIFICO_MIGR_USER/PASSWORD`. Ningún usuario de servicio debe tener acceso a la base de otro (aislamiento HU-06.1).
   ```bash
   mysql --host=<HOST> --port=<PORT> --user=avnadmin -p --ssl-mode=VERIFY_CA --ssl-ca=certs/ca.pem
   ```
4. **Overview -> Allowed IP addresses**: por defecto Aiven acepta cualquier IP. Restringe a las IP públicas de los integrantes (o de donde corran los servicios) y actualízalas cuando cambien. No hay VPN: esto y la contraseña son la barrera de red.
5. **Overview -> Connection information**: ahí están `Host`, `Port` (distinto de 3306), `User` y `Password`, y el botón **Download** de *CA certificate* (`ca.pem`). Compártelos por un canal privado, nunca por el repo.

Nota para el dba: Aiven MySQL puede exigir clave primaria en todas las tablas (`sql_require_primary_key`) y no admite `CREATE TABLE ... SELECT`; las migraciones deben cumplirlo.

### 4.2 Convertir el CA a truststore PKCS12 (cada integrante, una vez)

MySQL Connector/J 9.7 (el de Spring Boot 3.5) **no lee PEM** para `VERIFY_CA`/`VERIFY_IDENTITY`: necesita un truststore Java.

```bash
mkdir -p certs                         # certs/ y *.pem / *.p12 están en .gitignore
mv ~/Descargas/ca.pem certs/ca.pem
keytool -importcert -noprompt -alias aiven-ca -file certs/ca.pem \
        -keystore certs/aiven-ca.p12 -storetype PKCS12 -storepass <PASSWORD_TRUSTSTORE>
keytool -list -keystore certs/aiven-ca.p12 -storepass <PASSWORD_TRUSTSTORE>   # comprobar: 1 entrada
```

`<PASSWORD_TRUSTSTORE>` (mínimo 6 caracteres, solo letras y números porque va dentro de la URL JDBC) es `AIVEN_TRUSTSTORE_PASSWORD`; no es secreto (protege un certificado público). `AIVEN_TRUSTSTORE_PATH` es la ruta **absoluta** de `aiven-ca.p12`. Si Aiven rota el CA, vuelve a descargarlo y repite.

### 4.3 Ejecutar con el perfil `aiven`

```bash
cp servicio-cientifico/.env.example servicio-cientifico/.env           # completa los CAMBIAR
cd servicio-cientifico
SPRING_PROFILES_ACTIVE=aiven mvn spring-boot:run

cp servicio-consulta-publica/.env.example servicio-consulta-publica/.env
cd ../servicio-consulta-publica
SPRING_PROFILES_ACTIVE=aiven mvn spring-boot:run
```

RabbitMQ, Redis y Keycloak siguen levantados con `docker compose up -d rabbitmq redis keycloak` (no hace falta levantar los MySQL locales).

URL JDBC que arma el perfil (sin secretos; `<...>` son variables):

```
jdbc:mysql://<DB_HOST>:<DB_PORT>/<DB_NAME>?sslMode=VERIFY_CA
   &trustCertificateKeyStoreUrl=file:<AIVEN_TRUSTSTORE_PATH>&trustCertificateKeyStoreType=PKCS12
   &trustCertificateKeyStorePassword=<AIVEN_TRUSTSTORE_PASSWORD>&connectTimeout=3000&socketTimeout=30000
```

`VERIFY_CA` comprueba que el certificado del servidor lo firma el CA de Aiven; `AIVEN_SSL_MODE=VERIFY_IDENTITY` además valida el nombre del host. Nunca uses `PREFERRED`/`REQUIRED` (cifran sin verificar al servidor). Probar la conexión sin arrancar el servicio: el comando `mysql` de 4.1 con `--ssl-mode=VERIFY_CA --ssl-ca=certs/ca.pem`.

### 4.4 Pool de conexiones (Hikari) en `aiven`

| Parámetro | `local` / común | `aiven` | Motivo |
|---|---|---|---|
| `connection-timeout` científico | 30 s (defecto) | 10 s | Handshake TCP+TLS por internet |
| `connection-timeout` consulta pública | 2 s | **3 s** | Sigue fallando rápido con 503 (J10, RNF5); se sube 1 s solo por el handshake TLS. Si el 503 por latencia es frecuente, cambiar de región en Aiven antes de subir más este valor |
| `maximum-pool-size` | 10 (defecto) | 5 | Los planes de Aiven limitan conexiones y las comparten los servicios |
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

## 7. Cómo migrar los demás servicios (`api-gateway`, `servicio-equipos`, `servicio-reportes`)

Estos servicios no se tocaron; siguen con los MySQL del compose. Para llevarlos a Aiven, quien los mantenga:

1. Crear su base y su usuario en Aiven (4.1) y sus variables `<SVC>_DB_HOST/PORT/NAME/USER/PASSWORD` en su `.env.example`.
2. En su `application.yml`: `datasource.url/username/password` desde `${<SVC>_DB_URL}`, `${<SVC>_DB_USER}`, `${<SVC>_DB_PASSWORD}` y `spring.config.import: optional:file:../.env[.properties],optional:file:./.env[.properties]`.
3. Copiar `application-local.yml` y `application-aiven.yml` de `servicio-cientifico` cambiando el prefijo de variables y el nombre de la base; reutilizan `AIVEN_TRUSTSTORE_PATH` y `AIVEN_TRUSTSTORE_PASSWORD`.
4. Ajustar Hikari como en 4.4 y comprobar que los tests siguen pasando (usan Testcontainers).
5. Mantener la misma convención de Flyway (migraciones `VAAAAMMDD_HHMM__...`, `out-of-order`; D-I15-1).

## 8. Archivos relevantes

| Ruta | Función |
|---|---|
| `docker-compose.yml`, `.env.example` | MySQL locales, Redis, RabbitMQ, Keycloak |
| `servicio-*/src/main/resources/application{,-local,-aiven}.yml` | Perfiles de base de datos |
| `servicio-cientifico/.env.example`, `servicio-consulta-publica/.env.example` | Variables por servicio |
| `<servicio>/Dockerfile` | Imagen por servicio (JRE 21, usuario no root uid 10001); opcional, no se usa en el flujo diario |

## 9. Limitaciones conocidas

- Keycloak en `start-dev` con H2 y usuarios de prueba: no es producción.
- Sin HTTPS entre tus servicios y Keycloak/RabbitMQ locales (todo en `127.0.0.1`).
- El tráfico a Aiven va cifrado y verificado, pero depende de la lista de IP permitidas y de la contraseña del usuario.
- No hay despliegue de las webs (Vercel) en esta iteración.
