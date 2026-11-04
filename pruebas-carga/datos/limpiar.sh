#!/usr/bin/env bash
# Borra TODO lo que dejaron las pruebas de carga: fichas con prefijo c0a9a000 (semilla + eventos), claves de caché
# de consulta-publica en Redis (son solo caché) y los objetos temporales de la carga científica en RabbitMQ
# (exchange biohub.eventos.carga y la cola de carga con su DLQ; la cola/DLQ de desarrollo NO se tocan).
# Ejecutar con la carga detenida (docker compose ... down): si las réplicas siguen vivas, perderían su cola.
# LIMPIAR_RABBIT=0 omite RabbitMQ (lo usa sembrar.sh, que corre con las réplicas ya levantadas).
# Sin secretos en la línea de comandos (SEC-J-14): las contraseñas van por variable de entorno o por stdin.
set -euo pipefail
cd "$(dirname "$0")/../.."
set -a; . ./.env; set +a
COLA_CARGA="${COLA_CARGA:-consulta-publica.carga.eventos}"
# `docker exec -e NOMBRE` (sin valor) toma la variable del entorno de este proceso: no aparece en `ps`.
MYSQL_PWD="$MYSQL_LECTURA_ROOT_PASSWORD" docker exec -i -e MYSQL_PWD biohub-mysql-lectura mysql -uroot consulta_publica \
  -e "DELETE FROM ficha_publica WHERE HEX(hallazgo_id) LIKE 'C0A9A000%'; SELECT COUNT(*) AS fichas_de_carga_restantes FROM ficha_publica WHERE HEX(hallazgo_id) LIKE 'C0A9A000%'; SELECT COUNT(*) AS fichas_totales FROM ficha_publica;"
# Caché de Redis: solo claves del servicio (biohub:publica:*), que son caché reconstruible.
REDISCLI_AUTH="$REDIS_PASSWORD" docker exec -e REDISCLI_AUTH biohub-redis sh -c "redis-cli --scan --pattern 'biohub:publica:*' | xargs -r redis-cli del" >/dev/null 2>&1 || true
# RabbitMQ (API de management): las credenciales se entregan a curl por stdin (-K -), no por -u.
rmq() {
  local metodo="$1" ruta="$2" u p
  u=${RABBITMQ_USER//\\/\\\\}; u=${u//\"/\\\"}; p=${RABBITMQ_PASSWORD//\\/\\\\}; p=${p//\"/\\\"}
  printf 'user = "%s:%s"\n' "$u" "$p" | curl -s -o /dev/null -K - -X "$metodo" \
    "http://127.0.0.1:${RABBITMQ_MANAGEMENT_PORT:-15672}/api/$ruta" || true
}
if [ "${LIMPIAR_RABBIT:-1}" = 1 ]; then
  rmq DELETE "exchanges/%2F/biohub.eventos.carga"
  rmq DELETE "queues/%2F/${COLA_CARGA}"
  rmq DELETE "queues/%2F/${COLA_CARGA}.dlq"
fi
