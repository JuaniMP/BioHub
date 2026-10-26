#!/bin/bash
# Usuario MySQL de SOLO LECTURA para consultas del equipo (SEC-D-01 punto 3). IDEMPOTENTE:
#  - instalaciones nuevas: lo ejecuta docker-entrypoint-initdb.d (solo con volumen vacio);
#  - instancias existentes: lo aplica deploy/datos/aplicar-lectura-equipo.sh (docker exec), las veces que haga falta.
#
# Entorno requerido: MYSQL_ROOT_PASSWORD, LECTURA_EQUIPO_USER, LECTURA_EQUIPO_PASSWORD,
#                    LECTURA_EQUIPO_DBS (esquemas de aplicacion de ESTA instancia, separados por espacios).
# Opcional: LECTURA_EQUIPO_HOST (por defecto 100.64.0.0/255.192.0.0 = 100.64.0.0/10, el rango CGNAT de Tailscale).
#
# Privilegios: SELECT + SHOW VIEW solo en los esquemas de aplicacion. Nada en mysql/sys/performance_schema,
# sin PROCESS, sin FILE, sin GRANT OPTION, maximo 5 conexiones simultaneas. Cada ejecucion
# vuelve a fijar la contraseña y recalcula los permisos (REVOKE + GRANT), asi que corrige desviaciones.
set -euo pipefail

: "${LECTURA_EQUIPO_HOST:=100.64.0.0/255.192.0.0}"
for var in LECTURA_EQUIPO_USER LECTURA_EQUIPO_PASSWORD; do
  [[ "${!var:-}" =~ ^[A-Za-z0-9_]+$ ]] || { echo "[biohub] ERROR: $var vacio o con caracteres no permitidos" >&2; exit 1; }
done
[[ "$LECTURA_EQUIPO_HOST" =~ ^[0-9./]+$ ]] || { echo "[biohub] ERROR: LECTURA_EQUIPO_HOST invalido" >&2; exit 1; }
[[ -n "${LECTURA_EQUIPO_DBS:-}" ]] || { echo "[biohub] ERROR: LECTURA_EQUIPO_DBS vacio" >&2; exit 1; }
for db in $LECTURA_EQUIPO_DBS; do
  [[ "$db" =~ ^[A-Za-z0-9_]+$ ]] || { echo "[biohub] ERROR: esquema invalido: $db" >&2; exit 1; }
  case "$db" in mysql|sys|information_schema|performance_schema)
    echo "[biohub] ERROR: no se concede lectura sobre $db" >&2; exit 1 ;; esac
done

U="'${LECTURA_EQUIPO_USER}'@'${LECTURA_EQUIPO_HOST}'"
SQL="CREATE USER IF NOT EXISTS ${U} IDENTIFIED BY '${LECTURA_EQUIPO_PASSWORD}';
ALTER USER ${U} IDENTIFIED BY '${LECTURA_EQUIPO_PASSWORD}' WITH MAX_USER_CONNECTIONS 5;
REVOKE ALL PRIVILEGES, GRANT OPTION FROM ${U};"
for db in $LECTURA_EQUIPO_DBS; do
  SQL+=$'\n'"GRANT SELECT, SHOW VIEW ON \`${db}\`.* TO ${U};"
done
SQL+=$'\n'"FLUSH PRIVILEGES;"

# Contraseña de root por MYSQL_PWD (no por argumento); solo por socket local
MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot <<<"$SQL"
echo "[biohub] ${LECTURA_EQUIPO_USER}@${LECTURA_EQUIPO_HOST}: SELECT, SHOW VIEW en: ${LECTURA_EQUIPO_DBS}"
