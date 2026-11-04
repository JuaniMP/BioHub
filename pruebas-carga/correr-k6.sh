#!/usr/bin/env bash
# Ejecuta un script k6 con la imagen grafana/k6 (versión fija) o con k6 local si USAR_K6_LOCAL=1, y deja los
# resultados en pruebas-carga/resultados/.
# Uso: ./correr-k6.sh k6/consulta-publica-pico.js -e N=20 [-e otras=...]
#
# Secretos (SEC-J-14): ninguna contraseña ni JWT viaja como argumento (se verían con `ps`). Las credenciales de
# RabbitMQ (solo carga científica) se leen de ../.env y se pasan al contenedor por VARIABLE DE ENTORNO
# (RMQ_USER / RMQ_PASS; `docker run -e NOMBRE` sin valor toma la del entorno del cliente). El JWT de
# registro-hallazgo.js se pasa igual: `export TOKEN=...` antes de ejecutar (nunca `-e TOKEN=<jwt>`).
# Metadatos D42: COMMIT y RED_K6 se rellenan solos; REPLICAS y CPUS (CARGA_CPUS del compose) se exportan a mano.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p resultados
K6_VERSION="${K6_VERSION:-2.3.0}"   # versión fija: `latest` puede cambiar de imagen sin aviso
script="$1"; shift

# Valor de una clave de ../.env sin cargar el archivo completo en el entorno.
valor_env() { grep -E "^$1=" ../.env | head -1 | cut -d= -f2-; }
if [ -z "${RMQ_USER:-}" ] && [ -f ../.env ]; then RMQ_USER=$(valor_env RABBITMQ_USER); fi
if [ -z "${RMQ_PASS:-}" ] && [ -f ../.env ]; then RMQ_PASS=$(valor_env RABBITMQ_PASSWORD); fi
export RMQ_USER="${RMQ_USER:-}" RMQ_PASS="${RMQ_PASS:-}"
envs=(-e RMQ_USER -e RMQ_PASS)
# Metadatos para el JSON de resultados (D42): commit del árbol de trabajo y red de k6 (no son secretos).
COMMIT="${COMMIT:-$(git rev-parse --short HEAD 2>/dev/null || echo desconocido)$(git diff --quiet HEAD 2>/dev/null || echo -sin-commit)}"
RED_K6="${RED_K6:-host}"
export COMMIT RED_K6
envs+=(-e COMMIT -e RED_K6)
[ -n "${REPLICAS:-}" ] && { export REPLICAS; envs+=(-e REPLICAS); }
[ -n "${CPUS:-}" ] && { export CPUS; envs+=(-e CPUS); }
[ -n "${TOKEN:-}" ] && { export TOKEN; envs+=(-e TOKEN); }

if command -v k6 >/dev/null 2>&1 && [ "${USAR_K6_LOCAL:-0}" = 1 ]; then
  mkdir -p /resultados 2>/dev/null || true
  exec k6 run "$@" "$script"   # k6 local lee RMQ_USER/RMQ_PASS/TOKEN del entorno (__ENV)
fi
red="$RED_K6"   # RED_K6=biohub_default: k6 dentro de la red del compose (evita docker-proxy del puerto publicado)
exec docker run --rm --network "$red" -v "$PWD/k6:/scripts:ro" -v "$PWD/resultados:/resultados" -u "$(id -u):$(id -g)" \
  "${envs[@]}" "grafana/k6:${K6_VERSION}" run --quiet "$@" "/scripts/${script#k6/}"
