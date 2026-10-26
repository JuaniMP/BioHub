#!/usr/bin/env bash
# Despliega los stacks de BioHub en las VMs (por SSH sobre la red NAT del anfitrion).
#
#   deploy/desplegar.sh [datos|backend|todo] [--sin-build]
#
# Requisitos previos (fase 2): las dos VMs unidas a Tailscale (`tailscale up`) y
# deploy/generar-env.sh ejecutado. Orden con `todo`: primero datos, luego backend.
#
# Variables opcionales:
#   TS_IP_BACKEND, TS_IP_DATOS   IPs 100.x de Tailscale. Si no se dan, se leen de cada VM (`tailscale ip -4`).
#   VM_BACKEND_HOST, VM_DATOS_HOST   IP/nombre SSH de las VMs (por defecto la IP DHCP que da libvirt).
#   SSH_KEY                       llave privada (por defecto ~/.ssh/biohub_vms)
#   BIOHUB_TAG                    etiqueta de las imagenes (por defecto fecha-hora; sirve para rollback)
set -euo pipefail
export LANG=C LC_ALL=C

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
DEPLOY="$RAIZ/deploy"
QUE="${1:-todo}"; BUILD=1; [[ "${2:-}" == "--sin-build" ]] && BUILD=0
SSH_KEY="${SSH_KEY:-$HOME/.ssh/biohub_vms}"
SSH_OPTS=(-i "$SSH_KEY" -o StrictHostKeyChecking=accept-new -o BatchMode=yes -o ConnectTimeout=10)
SERVICIOS=(api-gateway servicio-cientifico servicio-equipos servicio-consulta-publica servicio-reportes)
TAG="${BIOHUB_TAG:-$(date +%Y%m%d%H%M%S)}"
[[ "${2:-}" == "--sin-build" && -z "${BIOHUB_TAG:-}" ]] && TAG=latest

[[ "$QUE" =~ ^(datos|backend|todo)$ ]] || { sed -n '2,15p' "$0"; exit 1; }
for d in backend datos; do
  [[ -f "$DEPLOY/$d/.env" ]] || { echo "Falta deploy/$d/.env: ejecuta deploy/generar-env.sh" >&2; exit 1; }
done

ip_vm() { # nombre -> IP NAT
  virsh -c qemu:///system domifaddr "$1" --source lease 2>/dev/null | awk '/ipv4/{sub("/.*","",$4);print $4}' | head -1
}
VM_BACKEND_HOST="${VM_BACKEND_HOST:-$(ip_vm biohub-backend)}"
VM_DATOS_HOST="${VM_DATOS_HOST:-$(ip_vm biohub-datos)}"
[[ -n "$VM_BACKEND_HOST" && -n "$VM_DATOS_HOST" ]] || { echo "No se pudo obtener la IP de las VMs" >&2; exit 1; }
rsh() { local h=$1; shift; ssh "${SSH_OPTS[@]}" "biohub@$h" "$@"; }

ts_ip() { # host
  rsh "$1" 'tailscale ip -4 2>/dev/null | head -1' || true
}
TS_IP_BACKEND="${TS_IP_BACKEND:-$(ts_ip "$VM_BACKEND_HOST")}"
TS_IP_DATOS="${TS_IP_DATOS:-$(ts_ip "$VM_DATOS_HOST")}"
for v in TS_IP_BACKEND TS_IP_DATOS; do
  [[ "${!v}" =~ ^100\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "$v invalida o vacia (¿VM unida a Tailscale?): '${!v}'" >&2; exit 1; }
done
echo "[i] backend: NAT $VM_BACKEND_HOST / tailscale $TS_IP_BACKEND   datos: NAT $VM_DATOS_HOST / tailscale $TS_IP_DATOS"

# Escribe el .env con las IPs (y la etiqueta) en la VM, con permisos 600
subir_env() { # dir_local host dir_remoto [clave=valor ...]
  local dir=$1 host=$2 remoto=$3; shift 3
  local tmp; tmp="$(mktemp)"
  cp "$dir/.env" "$tmp"
  local kv
  for kv in "TS_IP_BACKEND=$TS_IP_BACKEND" "TS_IP_DATOS=$TS_IP_DATOS" "$@"; do
    local k="${kv%%=*}"
    if grep -q "^$k=" "$tmp"; then sed -i "s|^$k=.*|$kv|" "$tmp"; fi
  done
  rsh "$host" "umask 077; cat > $remoto/.env" < "$tmp" || { rm -f "$tmp"; return 1; }
  rm -f "$tmp"
}

desplegar_datos() {
  echo "[datos] sincronizando archivos"
  rsh "$VM_DATOS_HOST" 'mkdir -p ~/biohub-datos'
  rsync -a --delete -e "ssh ${SSH_OPTS[*]}" --exclude '.env' --exclude '.env.example' "$DEPLOY/datos/" "biohub@$VM_DATOS_HOST:biohub-datos/"
  subir_env "$DEPLOY/datos" "$VM_DATOS_HOST" '~/biohub-datos'
  echo "[datos] docker compose up"
  rsh "$VM_DATOS_HOST" 'cd ~/biohub-datos && docker compose up -d --wait --wait-timeout 300 --remove-orphans && docker compose ps'
}

construir_y_cargar() {
  if [[ $BUILD -eq 1 ]]; then
    echo "[backend] mvn package (sin tests; los tests corren en CI)"
    (cd "$RAIZ" && mvn -B -q -DskipTests package)
    for s in "${SERVICIOS[@]}"; do
      echo "[backend] docker build biohub/$s:$TAG"
      docker build -q -f "$RAIZ/$s/Dockerfile" -t "biohub/$s:$TAG" -t "biohub/$s:latest" "$RAIZ" >/dev/null
    done
  fi
  echo "[backend] enviando imagenes a la VM (docker save | docker load)"
  local imgs=(); for s in "${SERVICIOS[@]}"; do imgs+=("biohub/$s:$TAG"); done
  docker save "${imgs[@]}" | gzip | rsh "$VM_BACKEND_HOST" 'gunzip | docker load'
}

desplegar_backend() {
  construir_y_cargar
  echo "[backend] generando definitions de RabbitMQ y copiando el realm"
  "$DEPLOY/backend/rabbitmq/generar-definitions.sh" "$DEPLOY/backend/.env"
  mkdir -p "$DEPLOY/backend/keycloak"; cp "$RAIZ/infra/keycloak/"*.json "$DEPLOY/backend/keycloak/"
  rsh "$VM_BACKEND_HOST" 'mkdir -p ~/biohub-backend'
  rsync -a --delete -e "ssh ${SSH_OPTS[*]}" --exclude '.env' --exclude '.env.example' "$DEPLOY/backend/" "biohub@$VM_BACKEND_HOST:biohub-backend/"
  subir_env "$DEPLOY/backend" "$VM_BACKEND_HOST" '~/biohub-backend' "BIOHUB_TAG=$TAG"
  echo "[backend] docker compose up (etiqueta $TAG)"
  rsh "$VM_BACKEND_HOST" 'cd ~/biohub-backend && docker compose up -d --wait --wait-timeout 400 --remove-orphans && docker compose ps'
  # Decisión D13 (aprobada por la dueña del repo, alcance 4a): Keycloak solo es accesible por la tailnet
  # (ACL + WireGuard), sin HTTPS propio. Con sslRequired=external rechaza a los clientes 100.x ("HTTPS required").
  # Se quita la exigencia en master y biohub. Al pasar a HTTPS (tailscale serve / Funnel) volver a "external".
  echo "[backend] Keycloak: sslRequired=NONE en master y biohub (solo tailnet; ver D13)"
  rsh "$VM_BACKEND_HOST" 'docker exec biohub-keycloak sh -c '"'"'K=/opt/keycloak/bin/kcadm.sh; C=/tmp/kcadm.config; export KC_CLI_PASSWORD="$KC_BOOTSTRAP_ADMIN_PASSWORD"; $K config credentials --config $C --server http://localhost:8180 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" >/dev/null && for r in master biohub; do $K update realms/$r --config $C -s sslRequired=NONE; done; rm -f $C'"'"''
}

[[ "$QUE" == "datos" || "$QUE" == "todo" ]] && desplegar_datos
[[ "$QUE" == "backend" || "$QUE" == "todo" ]] && desplegar_backend
echo "[ok] Despliegue terminado. Gateway: http://$TS_IP_BACKEND:8080  Keycloak: http://$TS_IP_BACKEND:8180  RabbitMQ: http://$TS_IP_BACKEND:15672"
echo "     Rollback: ssh a la VM, editar BIOHUB_TAG en ~/biohub-backend/.env a una etiqueta anterior (docker images) y 'docker compose up -d'."
