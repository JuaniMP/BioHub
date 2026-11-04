# api-gateway

API Gateway (enruta `/api/<prefijo>/**`, valida JWT; su base guarda API Keys y límites). Base propia: `gateway` (Flyway en `src/main/resources/db/migration/`, convención `VAAAAMMDD_HHMM__descripcion.sql`, `out-of-order`). Ejecución y Aiven: [`docs/equipo/09-despliegue.md`](../docs/equipo/09-despliegue.md).

## Base de datos: perfiles `local` y `aiven`

Se elige con `SPRING_PROFILES_ACTIVE`. **Sin perfil el servicio no arranca**: no hay URL ni credenciales por defecto (D36). Plantilla de variables: [`.env.example`](.env.example) (`cp .env.example .env`; el `.env` real nunca se sube al repo).

| Perfil | Base | Variables |
|---|---|---|
| `local` | MySQL del `docker-compose.yml` de la raíz (`localhost:3310`) | `MYSQL_GATEWAY_HOST/PORT`, `MYSQL_GATEWAY_USER/PASSWORD` del `.env` de la raíz |
| `aiven` | MySQL en Aiven, SSL con `VERIFY_CA` (truststore PKCS12) | `GATEWAY_DB_HOST/PORT/NAME/USER/PASSWORD`, `GATEWAY_MIGR_USER/PASSWORD`, `AIVEN_TRUSTSTORE_PATH/PASSWORD` |

```bash
cd api-gateway
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run    # MySQL del docker-compose
SPRING_PROFILES_ACTIVE=aiven mvn spring-boot:run    # MySQL en Aiven (ver 09-despliegue.md §4)
```

## Aiven (D39)

Esta base vive en el servicio de Aiven de **lectura**. Los usuarios (`gateway_app`, solo DML, y `gateway_migr`, DDL para Flyway) se crean con `deploy/aiven/02-lectura-bases-y-usuarios.sql.example`; cada usuario solo ve su base y exige SSL. Solo necesitas las credenciales de `gateway`. Guía paso a paso (cuenta, servicio, CA, truststore, `.env`): `docs/equipo/09-despliegue.md` §4.

**Usuario de migración**: la aplicación usa `gateway_app` (solo DML); Flyway necesita DDL, así que define `GATEWAY_MIGR_USER` y `GATEWAY_MIGR_PASSWORD` (`gateway_migr`), que `application.yml` pasa a `spring.flyway.user/password`. Sin ellos Flyway usa el usuario de la aplicación y falla por falta de `CREATE`.

**Comprobar que conectó**: en el log aparece el mensaje de Flyway `Successfully applied N migration(s)` o `Schema ... is up to date` y el servicio queda `UP` en `/actuator/health`. Con `mysql` y el usuario `gateway_app`: `SELECT installed_rank, version, description, success FROM flyway_schema_history;` (debe haber `1 base` con `success = 1`).

**Pool (Hikari, `application-aiven.yml`)**: `maximum-pool-size` 5, `connection-timeout` 3 s, `max-lifetime` 10 min, `keepalive-time` 2 min (`docs/equipo/09-despliegue.md` §4.4).

**Migraciones compatibles con Aiven MySQL 8**: toda tabla con `PRIMARY KEY`, sin `DEFINER`, triggers, funciones ni `SET GLOBAL`, utf8mb4. Antes de subir una migración: `deploy/aiven/verificar-migraciones.sh`.
