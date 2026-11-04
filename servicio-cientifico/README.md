# servicio-cientifico

Servicio de dominio científico. Dos módulos comparten el mismo esquema MySQL `cientifico`:

| Módulo | Dueño | Tablas |
|---|---|---|
| `registro` | Hernando | las suyas (hallazgos, fichas, historial) |
| `ubicacion` | Juanita | prefijo `ubicacion_`: `ubicacion_region`, `ubicacion_departamento`, `ubicacion_hallazgo` |

Modelo de datos completo: [`docs/equipo/05-modelo-datos.md`](../docs/equipo/05-modelo-datos.md). Ejecución local y en Aiven: [`docs/equipo/09-despliegue.md`](../docs/equipo/09-despliegue.md).

## Migraciones con Flyway

El esquema lo crea y versiona Flyway; Hibernate nunca lo toca.

**Dónde:** `src/main/resources/db/migration/` (un único directorio para los dos módulos).

**Nombre de archivo**

| Tipo | Patrón | Ejemplo |
|---|---|---|
| Base | `V1__base.sql` | existe y está vacía (solo deja inicializado Flyway) |
| Cambio | `V<AAAAMMDD>_<HHMM>__<descripcion_en_minusculas>.sql` | `V20261026_1010__ubicacion_hallazgo.sql` |

Flyway convierte el guion bajo de la versión en punto (`20261026_1010` -> `20261026.1010`) y ordena numéricamente. Usa la fecha y hora reales de creación. Dos guiones bajos separan versión y descripción.

**Reglas**

1. **Una migración aplicada nunca se edita.** Flyway guarda el checksum de cada archivo; si cambia, el servicio no arranca (`Validate failed: checksum mismatch`). Cualquier cambio (columna, índice, corrección de un catálogo) es un archivo **nuevo** con una versión posterior.
2. `spring.jpa.hibernate.ddl-auto=validate`: al arrancar, Hibernate compara las entidades con el esquema real. Si una entidad cambia, la migración que la acompaña va en el mismo PR; si no coinciden, el contexto no arranca. Nunca `update`/`create`.
3. **`spring.flyway.out-of-order=true`** (decisión D-I15-1). Dos personas crean migraciones en ramas distintas sobre el mismo esquema; al fusionar, la versión de una puede ser *anterior* a otra ya aplicada. Sin `out-of-order`, Flyway rechazaría la más antigua; con él la aplica igualmente. La versión por fecha evita además que dos personas creen el mismo número. Costo: el orden de aplicación puede diferir entre bases, así que las migraciones de ambos módulos deben ser independientes (por eso el prefijo `ubicacion_` en tablas, índices y constraints: sus nombres son únicos por esquema).
4. Solo `INSERT` de **catálogos de referencia** (regiones y departamentos). Nada de datos de prueba en migraciones.
5. Compatibilidad con MySQL administrado de Aiven (ver sección siguiente). Antes de subir una migración: `deploy/aiven/verificar-migraciones.sh`.
6. MySQL no hace rollback de DDL: si una migración falla a la mitad, quedan objetos creados y una fila `success = 0` en el historial. Se elimina a mano lo que quedó, se ejecuta `flyway repair` y se corrige **en un archivo nuevo si la migración llegó a aplicarse en otra base**, o en sitio si solo existió en la tuya.

**Compatibilidad con Aiven MySQL 8** (reglas para quien escriba migraciones, también Hernando)

- Toda tabla lleva `PRIMARY KEY` (Aiven activa `sql_require_primary_key`). `CREATE TABLE ... SELECT` no se usa.
- Sin `CREATE TRIGGER`, `CREATE FUNCTION/PROCEDURE/EVENT`, `DEFINER=`, `SET GLOBAL`, `GRANT`, `CREATE DATABASE/USER`: no hay privilegio `SUPER` y el binlog está activo.
- Tablas con `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`.
- Strings con comilla simple; nada de `||` ni de comillas dobles para texto (el `sql_mode` de Aiven puede incluir `ANSI`).
- Fechas `DATETIME(3)` en UTC.

**Ver el historial aplicado**

```sql
SELECT installed_rank, version, description, script, checksum, installed_by, installed_on, success
FROM flyway_schema_history ORDER BY installed_rank;
```

Esperado hoy: `1 base`, `20261026.1000 ubicacion catalogo departamentos`, `20261026.1010 ubicacion hallazgo` (más las de registro cuando existan), todas con `success = 1`. Aplica a Docker local y a Aiven por igual. Con el CLI de Flyway: `flyway info`.

**Usuario de migración**: en Aiven el servicio se conecta como `cientifico_app` (solo DML). Las migraciones necesitan DDL, así que define `CIENTIFICO_MIGR_USER` y `CIENTIFICO_MIGR_PASSWORD` (`cientifico_migr`); `application.yml` los pasa a `spring.flyway.user/password`. Sin ellos Flyway usa el usuario de la aplicación y fallará por falta de `CREATE`. Los usuarios se crean con `deploy/aiven/01-escritura-bases-y-usuarios.sql.example`.

**Si la base ya tiene tablas (baseline)**

Una base *nueva y vacía* (caso de Aiven hoy) **no necesita baseline**: Flyway aplica todo desde `V1`. Solo si alguien creó tablas a mano o con otra herramienta (esquema existente pero sin `flyway_schema_history`), Flyway se niega a migrar para no pisar nada: `Found non-empty schema(s) "cientifico" but no schema history table`. En ese caso, **una sola vez y después de comprobar que el esquema coincide exactamente con las migraciones hasta cierta versión**, se marca esa versión como ya aplicada:

```bash
# Ejemplo (NO ejecutar sin revisar): la base ya contiene lo de V20261026_1010
flyway -url="jdbc:mysql://HOST:PUERTO/cientifico?sslMode=VERIFY_CA&..." -user=cientifico_migr -password=... \
       -baselineVersion=20261026.1010 -baselineDescription="esquema preexistente" baseline
```

A partir de esa versión (inclusive) Flyway ignora los archivos anteriores y aplica solo los posteriores. No actives `spring.flyway.baseline-on-migrate=true`: haría ese paso sin avisar en cualquier base no vacía, incluso si le faltan tablas.

## Llaves de cifrado de coordenadas (ubicacion, RNF9, ASR6)

El módulo `ubicacion` cifra con AES-256-GCM todas las coordenadas (D15). Las llaves se leen detrás del puerto `ProveedorDeLlaves`, que tiene dos adaptadores (ARQ-11, SAD §6.3.1). Se elige con `UBICACION_LLAVES_PROVEEDOR` y, si no se define, se usa `entorno`, que no cambia nada de lo que ya funcionaba.

| `UBICACION_LLAVES_PROVEEDOR` | De dónde salen las llaves | Variables |
|---|---|---|
| `entorno` (por defecto) | Variables de entorno | `UBICACION_CLAVES=v1:<base64>[,v2:<base64>]`, `UBICACION_CLAVE_ACTIVA=v1` |
| `keystore` | Un keystore PKCS12 (`.p12`) protegido con contraseña | `UBICACION_KEYSTORE_RUTA`, `UBICACION_KEYSTORE_PASSWORD`, `UBICACION_CLAVE_ACTIVA=<alias>` |

Con `keystore`, cada **alias** es el id de una versión (`v1`, `v2`...) y debe guardar una llave secreta **AES de 256 bits**. `UBICACION_CLAVE_ACTIVA` es el alias que cifra lo nuevo; los demás solo descifran lo ya guardado. La rotación y el re-cifrado (`POST /api/cientifico/ubicacion/recifrado`) funcionan igual con los dos adaptadores. El servicio **no arranca** si el archivo no existe, la contraseña es incorrecta, un alias no es `vN`, una llave no mide 32 bytes o la activa no existe; los mensajes nunca muestran la contraseña ni las llaves.

### Crear el keystore con `keytool`

El `.p12` **nunca se versiona** (`*.p12` está en `.gitignore`): se crea en la máquina donde corre el servicio, fuera del repositorio.

```bash
# 1. Carpeta fuera del repo, solo legible por tu usuario
mkdir -p ~/biohub-llaves && chmod 700 ~/biohub-llaves

# 2. Primera llave (alias = id de versión). keytool pide la contraseña del keystore; usa una larga
keytool -genseckey -alias v1 -keyalg AES -keysize 256 \
        -storetype PKCS12 -keystore ~/biohub-llaves/ubicacion.p12
chmod 600 ~/biohub-llaves/ubicacion.p12

# 3. Revisar (muestra los alias, no las llaves)
keytool -list -storetype PKCS12 -keystore ~/biohub-llaves/ubicacion.p12

# 4. Rotación: agregar la versión siguiente al MISMO archivo (misma contraseña)
keytool -genseckey -alias v2 -keyalg AES -keysize 256 \
        -storetype PKCS12 -keystore ~/biohub-llaves/ubicacion.p12
```

Variables (en el `.env` del servicio, que tampoco se versiona; ver `.env.example`). La contraseña se define aparte (ver más abajo):

```properties
UBICACION_LLAVES_PROVEEDOR=keystore
UBICACION_KEYSTORE_RUTA=/home/tu-usuario/biohub-llaves/ubicacion.p12
UBICACION_CLAVE_ACTIVA=v1
# UBICACION_KEYSTORE_PASSWORD NO va aquí: exportarla en el entorno del proceso o inyectarla como secreto
```

**Permisos y contraseña (SEC-J-12).** El servicio **no arranca** si el `.p12` es accesible por el grupo o por otros (en Linux/macOS exige `chmod 600`; en Windows esta comprobación no aplica). Además, el `.env` suele guardar la ruta y la contraseña juntas: si ambas están en el mismo archivo, el keystore casi no añade protección. Pasa `UBICACION_KEYSTORE_PASSWORD` como **variable del sistema o secreto del orquestador** (Docker/Kubernetes secret, `systemd` `EnvironmentFile` con `chmod 600` distinto del `.env`, gestor de secretos), no en el `.env` junto a `UBICACION_KEYSTORE_RUTA`. El servicio borra de memoria su copia `char[]` de la contraseña tras abrir el keystore; el `String` de la propiedad sigue en el `Environment` de Spring, así que no actives volcados de heap ni expongas `env`/`configprops`/`heapdump` en actuator.

Pasar de `entorno` a `keystore` con datos ya cifrados: el keystore debe contener las mismas llaves (mismos ids y bytes) que `UBICACION_CLAVES`. Si no, crea el keystore con la llave nueva `v2`, mantén también la `v1` y ejecuta el re-cifrado. Si pierdes una llave, sus coordenadas son irrecuperables: guarda una copia **fuera del servidor** y nunca junto a los respaldos de la base.

## Patrones del SAD en `ubicacion` (D40)

| Patrón | Clases |
|---|---|
| Strategy (RF12, RF13) | `EstrategiaVisibilidadUbicacion`, `VisibilidadGeneralizada` (por defecto), `VisibilidadExacta` (la única que recibe `ServicioDescifrado`), elegida por `PoliticaAccesoUbicacion.estrategiaPara(auth)` |
| Decorator (RNF9, ASR6) | `RepositorioUbicaciones` -> `DecoradorAuditoriaAcceso` -> `DecoradorCifradoCoordenadas` -> `RepositorioUbicacionesJpa`, ensamblados en `ConfiguracionUbicacion` |
| Adapter (ASR6) | puerto `ProveedorDeLlaves` con `ProveedorDeLlavesEntorno` y `ProveedorDeLlavesKeyStore` |
| Observer | `ObservadorUbicacion` (`@EventListener` síncrono, misma transacción) escucha `HallazgoRegistrado` y `HallazgoReclasificado` de `cientifico.eventos.domain` y llama a `UbicacionService` (`MANDATORY`) |

Para registro: en lugar de llamar a `UbicacionService`, publica el evento con `ApplicationEventPublisher.publishEvent(new HallazgoRegistrado(...))` dentro de su transacción (ver el javadoc de los eventos).
