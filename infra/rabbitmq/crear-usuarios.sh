#!/bin/sh
# Crea un usuario de RabbitMQ por servicio con permisos mínimos (SEC-04, SEC-D-03).
# Corre una vez en cada "docker compose up" (servicio rabbitmq-usuarios). Es idempotente: repetirlo solo
# actualiza contraseñas y permisos. Un usuario sin contraseña en el .env se omite, así que nada se rompe
# para quien todavía usa RABBITMQ_USER.
#
# Permisos de RabbitMQ: cada uno es una expresión regular sobre nombres de exchanges y colas.
#   configure = crear o borrar   write = publicar o enlazar   read = consumir
# Un consumidor NO tiene write sobre biohub.eventos: no puede inventar eventos hallazgo.*.
set -eu

API="http://rabbitmq:15672/api"
CONFIG=/tmp/curl-admin.cfg

# Credenciales en un archivo privado del contenedor: no quedan en los argumentos del proceso (SEC-10).
umask 077
printf 'user = "%s:%s"\n' "$RABBITMQ_ADMIN_USER" "$RABBITMQ_ADMIN_PASSWORD" > "$CONFIG"

poner() {  # poner <ruta> : envía por stdin el JSON a la API de administración
  curl -fsS -K "$CONFIG" -X PUT -H "content-type: application/json" --data-binary @- "$API/$1"
}

crear() {  # crear <usuario> <contraseña> <configure> <write> <read>
  if [ -z "$2" ]; then
    echo "Sin contraseña para $1 en el .env: se omite"
    return 0
  fi
  printf '{"password":"%s","tags":""}' "$2" | poner "users/$1"
  printf '{"configure":"%s","write":"%s","read":"%s"}' "$3" "$4" "$5" | poner "permissions/%2F/$1"
  echo "Usuario $1 listo"
}

# El publicador solo declara el exchange y publica en él.
crear "$RABBITMQ_CIENTIFICO_USER" "$RABBITMQ_CIENTIFICO_PASSWORD" \
  '^biohub\\.eventos$' \
  '^biohub\\.eventos$' \
  '^$'

# Cada consumidor declara los exchanges, su cola y su DLQ, y solo escribe en lo suyo. Necesita write sobre
# biohub.eventos.dlx porque RabbitMQ lo exige para declarar una cola con dead-letter exchange. Lo protegido
# es biohub.eventos: ahí ningún consumidor puede publicar.
crear "$RABBITMQ_CONSULTA_USER" "$RABBITMQ_CONSULTA_PASSWORD" \
  '^(biohub\\.eventos|biohub\\.eventos\\.dlx|consulta-publica\\.eventos(\\.dlq)?)$' \
  '^(biohub\\.eventos\\.dlx|consulta-publica\\.eventos(\\.dlq)?)$' \
  '^(biohub\\.eventos|biohub\\.eventos\\.dlx|consulta-publica\\.eventos(\\.dlq)?)$'

crear "$RABBITMQ_REPORTES_USER" "$RABBITMQ_REPORTES_PASSWORD" \
  '^(biohub\\.eventos|biohub\\.eventos\\.dlx|reportes\\.eventos(\\.dlq)?)$' \
  '^(biohub\\.eventos\\.dlx|reportes\\.eventos(\\.dlq)?)$' \
  '^(biohub\\.eventos|biohub\\.eventos\\.dlx|reportes\\.eventos(\\.dlq)?)$'
