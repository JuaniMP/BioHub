#!/usr/bin/env bash
# Crea/completa deploy/backend/.env y deploy/datos/.env con contraseñas aleatorias ([A-Za-z0-9_]).
# Las contraseñas de MySQL de aplicacion se generan una vez y se escriben IGUALES en ambos.
#
# IDEMPOTENTE: si un .env ya existe NO se toca ninguna variable existente (ni se regeneran contraseñas);
# solo se AÑADEN las variables nuevas que aparezcan en el .env.example (con contraseña aleatoria si es una
# contraseña). Asi se puede volver a ejecutar tras actualizar el repo. Con --forzar se regeneran los dos
# archivos desde cero (cambia TODAS las contraseñas; hay que redesplegar y recrear los usuarios de MySQL).
# Los .env estan en .gitignore.
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"
FORZAR=0; [[ "${1:-}" == "--forzar" ]] && FORZAR=1

pw() { # 24 caracteres alfanumericos
  local p=""; while [[ ${#p} -lt 24 ]]; do p+="$(head -c 64 /dev/urandom | tr -dc 'A-Za-z0-9' || true)"; done
  printf '%s' "${p:0:24}"
}
# Sustituye CLAVE=... por CLAVE=valor en un archivo (valor sin caracteres especiales)
poner() { sed -i "s|^$2=.*|$2=$3|" "$1"; }
tiene() { grep -q "^$2=" "$1"; }
valor() { grep -m1 "^$2=" "$1" | cut -d= -f2- || true; }
# Claves que son contraseñas (no las MYSQL_*_PASSWORD de aplicacion: esas se comparten entre ambos .env)
es_clave_propia() { [[ "$1" =~ ^(MYSQL_[A-Z_]+_ROOT_PASSWORD|REDIS_PASSWORD|RABBITMQ_[A-Z]+_PASSWORD|KEYCLOAK_ADMIN_PASSWORD|KEYCLOAK_TEST_USERS_PASSWORD|MYSQL_LECTURA_EQUIPO_PASSWORD)$ ]]; }
es_clave_compartida() { [[ "$1" =~ ^MYSQL_(CIENTIFICO|EQUIPOS|LECTURA|ANALITICA|GATEWAY)_PASSWORD$ ]]; }

umask 077
B="$AQUI/backend/.env"; D="$AQUI/datos/.env"
if [[ $FORZAR -eq 1 || ! -f $B ]]; then NUEVO_B=1; else NUEVO_B=0; fi
if [[ $FORZAR -eq 1 || ! -f $D ]]; then NUEVO_D=1; else NUEVO_D=0; fi

# Contraseñas compartidas ya definidas en alguno de los dos .env (para no divergir)
declare -A COMPARTIDAS=()
for k in MYSQL_CIENTIFICO_PASSWORD MYSQL_EQUIPOS_PASSWORD MYSQL_LECTURA_PASSWORD MYSQL_ANALITICA_PASSWORD MYSQL_GATEWAY_PASSWORD; do
  for f in "$B" "$D"; do
    if [[ -f $f ]] && { [[ $FORZAR -eq 0 ]] || false; } && tiene "$f" "$k" && [[ -n "$(valor "$f" "$k")" ]]; then COMPARTIDAS[$k]="$(valor "$f" "$k")"; fi
  done
  [[ -n "${COMPARTIDAS[$k]:-}" ]] || COMPARTIDAS[$k]="$(pw)"
done

completar() { # .env  .env.example  : crea o añade solo lo que falta
  local env=$1 ej=$2 nuevo=$3 anadidas=0 linea k v
  if [[ $nuevo -eq 1 ]]; then : > "$env"; fi
  while IFS= read -r linea; do
    [[ "$linea" =~ ^[A-Z][A-Z0-9_]*= ]] || { [[ $nuevo -eq 1 ]] && printf '%s\n' "$linea" >> "$env"; continue; }
    k="${linea%%=*}"; v="${linea#*=}"
    v="$(printf '%s' "$v" | sed -E 's/[[:space:]]+#.*$//')"   # sin comentarios al final de linea
    if tiene "$env" "$k"; then continue; fi
    if es_clave_compartida "$k"; then v="${COMPARTIDAS[$k]}"
    elif es_clave_propia "$k"; then v="$(pw)"; fi
    printf '%s=%s\n' "$k" "$v" >> "$env"; anadidas=$((anadidas+1))
  done < "$ej"
  chmod 600 "$env"
  if [[ $nuevo -eq 1 ]]; then echo "Creado: $env (600)"; else echo "Existente: $env; variables nuevas añadidas: $anadidas (las existentes no se tocaron)"; fi
}
completar "$B" "$AQUI/backend/.env.example" $NUEVO_B
completar "$D" "$AQUI/datos/.env.example"   $NUEVO_D
echo "Rellena TS_IP_* y el modo HTTPS con desplegar.sh."
