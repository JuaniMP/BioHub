#!/usr/bin/env bash
# Siembra 2000 fichas sintéticas en mysql-lectura (local, Docker). Idempotente: borra primero las de carga.
set -euo pipefail
cd "$(dirname "$0")/../.."
set -a; . ./.env; set +a
# MYSQL_PWD por entorno (no como argumento de docker exec, visible con `ps`; SEC-J-14)
run() { MYSQL_PWD="$MYSQL_LECTURA_ROOT_PASSWORD" docker exec -i -e MYSQL_PWD biohub-mysql-lectura mysql -uroot --default-character-set=utf8mb4 consulta_publica "$@"; }
LIMPIAR_RABBIT=0 pruebas-carga/datos/limpiar.sh >/dev/null   # sin tocar la cola de las réplicas ya levantadas
run < pruebas-carga/datos/semilla-fichas.sql
