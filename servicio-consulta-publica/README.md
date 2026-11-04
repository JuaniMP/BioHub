# servicio-consulta-publica

Búsqueda y ficha pública de especies. Lee de su propio modelo de lectura (MySQL `consulta_publica`), alimentado por los eventos `hallazgo.*` de RabbitMQ, con caché Redis opcional. **No almacena coordenadas** (D14).

Modelo de datos: [`docs/equipo/05-modelo-datos.md`](../docs/equipo/05-modelo-datos.md). Ejecución local y en Aiven: [`docs/equipo/09-despliegue.md`](../docs/equipo/09-despliegue.md).

## Migraciones con Flyway

El esquema lo crea y versiona Flyway; Hibernate nunca lo toca.

**Dónde:** `src/main/resources/db/migration/`.

**Nombre de archivo**

| Tipo | Patrón | Ejemplo |
|---|---|---|
| Base | `V1__base.sql` | existe y está vacía (solo deja inicializado Flyway) |
| Cambio | `V<AAAAMMDD>_<HHMM>__<descripcion_en_minusculas>.sql` | `V20261026_1010__ficha_publica.sql` |

Flyway convierte el guion bajo de la versión en punto (`20261026_1010` -> `20261026.1010`) y ordena numéricamente. Usa la fecha y hora reales de creación.

**Reglas**

1. **Una migración aplicada nunca se edita.** Flyway guarda su checksum; si el archivo cambia, el servicio no arranca (`checksum mismatch`). Todo cambio (columna, índice, corrección de catálogo) es un archivo **nuevo** con versión posterior.
2. `spring.jpa.hibernate.ddl-auto=validate`: las entidades se comparan con el esquema real al arrancar; nunca `update`/`create`. La migración y el cambio de la entidad van en el mismo PR.
3. **`spring.flyway.out-of-order=true`** (decisión D-I15-1): misma convención de versión por fecha que `servicio-cientifico`, cuyo esquema sí lo escriben dos personas. Aquí lo comparte un solo módulo, pero se mantiene igual en todo el repo para que una migración que llega tarde al fusionar ramas se aplique en lugar de bloquear el arranque. La versión por fecha evita choques de número.
4. Solo `INSERT` de catálogos de referencia (`departamento`). Nada de datos de prueba.
5. Compatibilidad con Aiven MySQL 8: toda tabla con `PRIMARY KEY` (Aiven activa `sql_require_primary_key`); sin triggers, funciones, procedimientos, eventos, `DEFINER=`, `SET GLOBAL`, `GRANT`, `CREATE DATABASE/USER` ni `CREATE TABLE ... SELECT`; tablas con `utf8mb4` / `utf8mb4_0900_ai_ci`; `DATETIME(3)` en UTC; texto con comillas simples. Comprueba con `deploy/aiven/verificar-migraciones.sh`.
6. MySQL no revierte DDL: si una migración falla a medias quedan objetos y una fila `success = 0`. Elimina a mano lo que quedó, ejecuta `flyway repair` y corrige en un archivo nuevo si la migración llegó a aplicarse en otra base.

**Ver el historial aplicado**

```sql
SELECT installed_rank, version, description, script, checksum, installed_by, installed_on, success
FROM flyway_schema_history ORDER BY installed_rank;
```

Esperado hoy: `1 base`, `20261026.1000 catalogo departamentos`, `20261026.1010 ficha publica`, todas con `success = 1`. Con el CLI de Flyway: `flyway info`.

**Usuario de migración**: la aplicación usa `consulta_app` (solo DML); Flyway necesita DDL, así que define `CONSULTA_MIGR_USER` y `CONSULTA_MIGR_PASSWORD` (`consulta_migr`), que `application.yml` pasa a `spring.flyway.user/password`. Sin ellos Flyway usa el usuario de la aplicación y falla por falta de `CREATE`. Los usuarios se crean con `deploy/aiven/02-lectura-bases-y-usuarios.sql.example`.

**Si la base ya tiene tablas (baseline)**

Una base *nueva y vacía* (Aiven hoy) **no necesita baseline**. Solo si el esquema existe sin `flyway_schema_history` (creado a mano), Flyway se niega a migrar (`Found non-empty schema(s) ... but no schema history table`). Entonces, una sola vez y tras comprobar que el esquema coincide exactamente con las migraciones hasta cierta versión:

```bash
# Ejemplo (NO ejecutar sin revisar): la base ya contiene lo de V20261026_1010
flyway -url="jdbc:mysql://HOST:PUERTO/consulta_publica?sslMode=VERIFY_CA&..." -user=consulta_migr -password=... \
       -baselineVersion=20261026.1010 -baselineDescription="esquema preexistente" baseline
```

Flyway marca esa versión como aplicada, ignora las anteriores y aplica solo las posteriores. No actives `spring.flyway.baseline-on-migrate=true`: lo haría sin avisar sobre cualquier base no vacía, aunque le falten tablas.
