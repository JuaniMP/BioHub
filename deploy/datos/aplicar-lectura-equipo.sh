#!/bin/bash
# Crea/actualiza el usuario de solo lectura (lectura_equipo) en las 4 instancias MySQL YA EXISTENTES
# (los scripts de init solo corren con el volumen vacio). Idempotente: se puede repetir (tambien rota la
# contraseña si cambio en .env). No toca datos ni reinicia contenedores.
#   cd ~/biohub-datos && ./aplicar-lectura-equipo.sh      (lo ejecuta deploy/desplegar.sh datos)
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"
ENVF="$AQUI/.env"
[[ -f "$ENVF" ]] || { echo "Falta $ENVF" >&2; exit 1; }
leer() { grep -m1 "^$1=" "$ENVF" | cut -d= -f2- || true; }

LECTURA_EQUIPO_USER="$(leer MYSQL_LECTURA_EQUIPO_USER)"; LECTURA_EQUIPO_USER="${LECTURA_EQUIPO_USER:-lectura_equipo}"
LECTURA_EQUIPO_PASSWORD="$(leer MYSQL_LECTURA_EQUIPO_PASSWORD)"
[[ -n "$LECTURA_EQUIPO_PASSWORD" ]] || { echo "Falta MYSQL_LECTURA_EQUIPO_PASSWORD en $ENVF (deploy/generar-env.sh)" >&2; exit 1; }
export LECTURA_EQUIPO_USER LECTURA_EQUIPO_PASSWORD   # se pasan al contenedor por nombre (no aparecen en `ps`)

aplicar() { # contenedor "esquemas"
  echo "[$1]"
  LECTURA_EQUIPO_DBS="$2" docker exec -i -e LECTURA_EQUIPO_USER -e LECTURA_EQUIPO_PASSWORD -e LECTURA_EQUIPO_DBS \
    "$1" bash -s < "$AQUI/init/02-lectura-equipo.sh"
}
export LECTURA_EQUIPO_DBS=""
aplicar biohub-mysql-transaccional "cientifico equipos"
aplicar biohub-mysql-lectura       "consulta_publica"
aplicar biohub-mysql-analitica     "reportes"
aplicar biohub-mysql-gateway       "gateway"
