#!/usr/bin/env bash
# Crea deploy/backend/.env y deploy/datos/.env con contraseñas aleatorias ([A-Za-z0-9_]).
# Las contraseñas de MySQL de aplicacion se generan una vez y se escriben IGUALES en ambos.
# No sobrescribe .env existentes salvo con --forzar. Los .env estan en .gitignore.
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"
FORZAR=0; [[ "${1:-}" == "--forzar" ]] && FORZAR=1
for d in backend datos; do
  if [[ -f "$AQUI/$d/.env" && $FORZAR -eq 0 ]]; then
    echo "$d/.env ya existe; usa --forzar para regenerarlo (cambia TODAS las contraseñas)." >&2; exit 1
  fi
done

pw() { # 24 caracteres alfanumericos
  local p=""; while [[ ${#p} -lt 24 ]]; do p+="$(head -c 64 /dev/urandom | tr -dc 'A-Za-z0-9' || true)"; done
  printf '%s' "${p:0:24}"
}
# Sustituye CLAVE=... por CLAVE=valor en un archivo (valor sin caracteres especiales)
poner() { sed -i "s|^$2=.*|$2=$3|" "$1"; }

umask 077
cp "$AQUI/backend/.env.example" "$AQUI/backend/.env"
cp "$AQUI/datos/.env.example"   "$AQUI/datos/.env"
B="$AQUI/backend/.env"; D="$AQUI/datos/.env"
# quitar comentarios al final de linea del ejemplo (para que el valor quede limpio)
sed -i -E 's/[[:space:]]+#.*$//' "$B" "$D"

for k in MYSQL_CIENTIFICO_PASSWORD MYSQL_EQUIPOS_PASSWORD MYSQL_LECTURA_PASSWORD MYSQL_ANALITICA_PASSWORD MYSQL_GATEWAY_PASSWORD; do
  v="$(pw)"; poner "$B" "$k" "$v"; poner "$D" "$k" "$v"
done
for k in MYSQL_TRANSACCIONAL_ROOT_PASSWORD MYSQL_LECTURA_ROOT_PASSWORD MYSQL_ANALITICA_ROOT_PASSWORD MYSQL_GATEWAY_ROOT_PASSWORD; do
  poner "$D" "$k" "$(pw)"
done
for k in REDIS_PASSWORD RABBITMQ_ADMIN_PASSWORD RABBITMQ_CIENTIFICO_PASSWORD RABBITMQ_CONSULTA_PASSWORD \
         RABBITMQ_REPORTES_PASSWORD KEYCLOAK_ADMIN_PASSWORD KEYCLOAK_TEST_USERS_PASSWORD; do
  poner "$B" "$k" "$(pw)"
done
chmod 600 "$B" "$D"
echo "Creados: $B y $D (permisos 600). Rellena TS_IP_* con desplegar.sh."
