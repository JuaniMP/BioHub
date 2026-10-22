#!/bin/bash
# Se ejecuta UNA vez (volumen vacio). Crea la BD APP_DB y su usuario de aplicacion,
# limitado a MYSQL_APP_HOST (IP de Tailscale de biohub-backend), nunca '%'.
set -euo pipefail

for var in MYSQL_APP_HOST APP_DB APP_USER APP_PASSWORD; do
  if [[ ! "${!var}" =~ ^[A-Za-z0-9_.-]+$ ]]; then
    echo "[biohub] ERROR: $var vacio o con caracteres no permitidos" >&2; exit 1
  fi
done
export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"

mysql --protocol=socket -uroot <<SQL
CREATE DATABASE IF NOT EXISTS \`${APP_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${APP_USER}'@'${MYSQL_APP_HOST}' IDENTIFIED BY '${APP_PASSWORD}';
GRANT ALL PRIVILEGES ON \`${APP_DB}\`.* TO '${APP_USER}'@'${MYSQL_APP_HOST}';
FLUSH PRIVILEGES;
SQL
echo "[biohub] BD ${APP_DB} y usuario ${APP_USER} limitado a ${MYSQL_APP_HOST}."
