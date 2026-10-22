#!/bin/bash
# Se ejecuta UNA vez (volumen vacio). Basado en infra/mysql/transaccional/01-esquemas-y-usuarios.sh,
# pero cada usuario queda limitado al host MYSQL_APP_HOST (IP de Tailscale de biohub-backend)
# en vez de '%'. Cada usuario solo tiene permisos sobre SU esquema.
set -euo pipefail

for var in MYSQL_APP_HOST MYSQL_CIENTIFICO_USER MYSQL_CIENTIFICO_PASSWORD MYSQL_EQUIPOS_USER MYSQL_EQUIPOS_PASSWORD; do
  if [[ ! "${!var}" =~ ^[A-Za-z0-9_.-]+$ ]]; then
    echo "[biohub] ERROR: $var vacio o con caracteres no permitidos" >&2; exit 1
  fi
done
export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"

mysql --protocol=socket -uroot <<SQL
CREATE DATABASE IF NOT EXISTS cientifico CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS equipos    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${MYSQL_CIENTIFICO_USER}'@'${MYSQL_APP_HOST}' IDENTIFIED BY '${MYSQL_CIENTIFICO_PASSWORD}';
CREATE USER IF NOT EXISTS '${MYSQL_EQUIPOS_USER}'@'${MYSQL_APP_HOST}'    IDENTIFIED BY '${MYSQL_EQUIPOS_PASSWORD}';
GRANT ALL PRIVILEGES ON cientifico.* TO '${MYSQL_CIENTIFICO_USER}'@'${MYSQL_APP_HOST}';
GRANT ALL PRIVILEGES ON equipos.*    TO '${MYSQL_EQUIPOS_USER}'@'${MYSQL_APP_HOST}';
FLUSH PRIVILEGES;
SQL
echo "[biohub] Esquemas cientifico y equipos creados; usuarios limitados a ${MYSQL_APP_HOST}."
