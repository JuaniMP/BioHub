#!/bin/bash
# Se ejecuta UNA sola vez, cuando el volumen de mysql-transaccional está vacío.
# Crea los esquemas "cientifico" y "equipos", cada uno con su propio usuario.
# Cada usuario solo tiene permisos sobre SU esquema (aislamiento entre servicios).
set -euo pipefail

# Evita inyección SQL / romper el script: solo caracteres seguros en usuarios y contraseñas.
for var in MYSQL_CIENTIFICO_USER MYSQL_CIENTIFICO_PASSWORD MYSQL_EQUIPOS_USER MYSQL_EQUIPOS_PASSWORD; do
  if [[ ! "${!var}" =~ ^[A-Za-z0-9_.@#%+=-]+$ ]]; then
    echo "[biohub] ERROR: $var contiene caracteres no permitidos (use letras, números y _.@#%+=-)" >&2
    exit 1
  fi
done

# Contraseña de root por variable de entorno, no por argumento.
export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"

mysql --protocol=socket -uroot <<SQL
CREATE DATABASE IF NOT EXISTS cientifico CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS equipos    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS '${MYSQL_CIENTIFICO_USER}'@'%' IDENTIFIED BY '${MYSQL_CIENTIFICO_PASSWORD}';
CREATE USER IF NOT EXISTS '${MYSQL_EQUIPOS_USER}'@'%'    IDENTIFIED BY '${MYSQL_EQUIPOS_PASSWORD}';

GRANT ALL PRIVILEGES ON cientifico.* TO '${MYSQL_CIENTIFICO_USER}'@'%';
GRANT ALL PRIVILEGES ON equipos.*    TO '${MYSQL_EQUIPOS_USER}'@'%';
FLUSH PRIVILEGES;
SQL

echo "[biohub] Esquemas cientifico y equipos creados con sus usuarios."
