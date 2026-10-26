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
#   BIOHUB_HTTPS                  keep (por defecto) | 1 | 0. HTTPS con `tailscale serve` en el backend:
#                                   keep = mantiene el modo con el que quedo el ultimo despliegue (HTTP si es el primero);
#                                   1 = activa HTTPS (falla si la tailnet no tiene certificados). ANTES hay que pegar la ACL
#                                       nueva (puertos 443 y 8443), o nadie del equipo llegara al backend;
#                                   0 = vuelve a HTTP provisional (y quita serve). Es el rollback de HTTPS.
#   TS_FQDN                       nombre MagicDNS completo del backend (por defecto el que informa tailscale)
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
  local ip
  ip="$(virsh -c qemu:///system domifaddr "$1" --source lease 2>/dev/null | awk '/ipv4/{sub("/.*","",$4);print $4}' | head -1)"
  # Si libvirt no tiene el lease (dnsmasq reiniciado), preguntar al agente de la VM (interfaz enp*, red NAT 192.168.122.x)
  [[ -n "$ip" ]] || ip="$(virsh -c qemu:///system domifaddr "$1" --source agent 2>/dev/null | awk '/ipv4/ && $4 ~ /^192\.168\.122\./{sub("/.*","",$4);print $4}' | head -1)"
  printf '%s' "$ip"
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
    if grep -q "^$k=" "$tmp"; then sed -i "s|^$k=.*|$kv|" "$tmp"; else printf '%s\n' "$kv" >> "$tmp"; fi
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
  # Usuario MySQL de solo lectura (SEC-D-01): idempotente; los init solo corren con el volumen vacio
  echo "[datos] usuario de solo lectura lectura_equipo en las 4 instancias"
  rsh "$VM_DATOS_HOST" 'cd ~/biohub-datos && ./aplicar-lectura-equipo.sh'
}

# Usuario Linux `equipo` (sin sudo ni docker) + /usr/local/bin/biohub-estado + sudoers acotado (SEC-D-01)
instalar_equipo() { # host
  echo "[$1] usuario equipo + biohub-estado"
  tar -C "$DEPLOY/vms/equipo" -c . | rsh "$1" 'd="$(mktemp -d)" && tar -x -C "$d" && sudo bash "$d/instalar-equipo.sh" "$d"; rc=$?; rm -rf "$d"; exit $rc'
}

# --- HTTPS con tailscale serve (backend) ---
fqdn_backend() {
  if [[ -n "${TS_FQDN:-}" ]]; then printf '%s' "$TS_FQDN"; return; fi
  rsh "$VM_BACKEND_HOST" "tailscale status --json | jq -r '.Self.DNSName' | sed 's/\\.\$//'"
}
certificados_activos() { # fqdn
  rsh "$VM_BACKEND_HOST" "tailscale status --json | jq -e --arg f '$1' '(.CertDomains // []) | index(\$f) != null' >/dev/null"
}
decidir_https() { # fija HTTPS=0|1 y FQDN
  local modo="${BIOHUB_HTTPS:-keep}"
  FQDN="$(fqdn_backend)"
  if [[ "$modo" == keep ]]; then
    modo="$(rsh "$VM_BACKEND_HOST" 'grep -m1 "^TS_HTTPS=" ~/biohub-backend/.env 2>/dev/null | cut -d= -f2' || true)"
    [[ "$modo" == 1 ]] || modo=0
    echo "[backend] BIOHUB_HTTPS=keep -> modo $([[ $modo == 1 ]] && echo HTTPS || echo HTTP)"
  fi
  [[ "$FQDN" =~ ^[a-z0-9-]+\.[a-z0-9-]+\.ts\.net$ ]] || { echo "FQDN invalido: '$FQDN'" >&2; exit 1; }
  case "$modo" in
    0) HTTPS=0 ;;
    1) certificados_activos "$FQDN" || { echo "BIOHUB_HTTPS=1 pero la tailnet no tiene certificados HTTPS (CertDomains vacio). Activalos en https://login.tailscale.com/admin/dns ('HTTPS Certificates')." >&2; exit 1; }
       HTTPS=1 ;;
    *) echo "BIOHUB_HTTPS debe ser keep, 1 o 0" >&2; exit 1 ;;
  esac
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
  decidir_https
  local env_extra=()
  if [[ $HTTPS -eq 1 ]]; then
    KC_URL="https://$FQDN:8443"
    env_extra=("TS_HTTPS=1" "TS_FQDN=$FQDN" "HTTP_BIND_IP=127.0.0.1" "KC_PUBLIC_URL=$KC_URL" "KEYCLOAK_ISSUER_URI=$KC_URL/realms/biohub")
  else
    KC_URL="http://biohub-backend:8180"
    env_extra=("TS_HTTPS=0" "TS_FQDN=$FQDN" "HTTP_BIND_IP=" "KC_PUBLIC_URL=$KC_URL" "KEYCLOAK_ISSUER_URI=$KC_URL/realms/biohub")
  fi
  [[ $HTTPS -eq 1 ]] || echo "[backend] AVISO: modo HTTP provisional (D13). Para HTTPS: ver docs/equipo/09-despliegue.md seccion 4.1." >&2
  echo "[backend] modo: $([[ $HTTPS -eq 1 ]] && echo "HTTPS ($KC_URL)" || echo "HTTP provisional ($KC_URL)")"
  construir_y_cargar
  echo "[backend] generando definitions de RabbitMQ y copiando el realm"
  "$DEPLOY/backend/rabbitmq/generar-definitions.sh" "$DEPLOY/backend/.env"
  mkdir -p "$DEPLOY/backend/keycloak"; cp "$RAIZ/infra/keycloak/"*.json "$DEPLOY/backend/keycloak/"
  rsh "$VM_BACKEND_HOST" 'mkdir -p ~/biohub-backend'
  rsync -a --delete -e "ssh ${SSH_OPTS[*]}" --exclude '.env' --exclude '.env.example' "$DEPLOY/backend/" "biohub@$VM_BACKEND_HOST:biohub-backend/"
  subir_env "$DEPLOY/backend" "$VM_BACKEND_HOST" '~/biohub-backend' "BIOHUB_TAG=$TAG" "${env_extra[@]}"
  # script de serve en la VM (se ejecuta con sudo; no queda en el stack)
  rsh "$VM_BACKEND_HOST" 'cat > ~/tailscale-https.sh && chmod 700 ~/tailscale-https.sh' < "$DEPLOY/vms/tailscale-https.sh"
  # Volver a HTTP (BIOHUB_HTTPS=0): quitar serve antes de que los puertos vuelvan a la IP Tailscale
  [[ $HTTPS -eq 1 ]] || rsh "$VM_BACKEND_HOST" 'sudo ~/tailscale-https.sh quitar' >/dev/null
  echo "[backend] docker compose up (etiqueta $TAG)"
  rsh "$VM_BACKEND_HOST" 'cd ~/biohub-backend && docker compose up -d --wait --wait-timeout 400 --remove-orphans && docker compose ps'
  # Keycloak: sslRequired segun el modo. HTTPS -> external (D13 retirada: el cliente 100.x llega por https via serve).
  # HTTP provisional (sin certificados todavia) -> NONE en master y biohub, como exige D13 (solo tailnet, WireGuard).
  # Ademas, proteccion contra fuerza bruta en master (SEC-D-02).
  local ssl; ssl="$([[ $HTTPS -eq 1 ]] && echo external || echo NONE)"
  echo "[backend] Keycloak: sslRequired=$ssl en master y biohub"
  rsh "$VM_BACKEND_HOST" 'docker exec biohub-keycloak sh -c '"'"'K=/opt/keycloak/bin/kcadm.sh; C=/tmp/kcadm.config; export KC_CLI_PASSWORD="$KC_BOOTSTRAP_ADMIN_PASSWORD"; $K config credentials --config $C --server http://localhost:8180 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" >/dev/null && for r in master biohub; do $K update realms/$r --config $C -s sslRequired='"$ssl"'; done; $K update realms/master --config $C -s bruteForceProtected=true -s failureFactor=5 -s permanentLockout=false -s maxFailureWaitSeconds=900; rm -f $C'"'"''
  if [[ $HTTPS -eq 1 ]]; then
    echo "[backend] tailscale serve (HTTPS 443 -> gateway, 8443 -> Keycloak)"
    rsh "$VM_BACKEND_HOST" 'sudo ~/tailscale-https.sh aplicar'
  fi
  verificar_backend
}

# Comprobaciones basicas desde la VM: el issuer que publica Keycloak debe ser el que validan los servicios
verificar_backend() {
  local base issuer esperado="$KC_URL/realms/biohub"
  if [[ $HTTPS -eq 1 ]]; then base="https://$FQDN:8443"; else base="http://127.0.0.1:8180"; [[ "$TS_IP_BACKEND" ]] && base="http://$TS_IP_BACKEND:8180"; fi
  issuer="$(rsh "$VM_BACKEND_HOST" "curl -fsS --max-time 20 $base/realms/biohub/.well-known/openid-configuration | jq -r .issuer" 2>/dev/null || true)"
  if [[ "$issuer" == "$esperado" ]]; then echo "[ok] issuer de Keycloak = $issuer"
  else echo "[!] issuer inesperado: '$issuer' (esperado '$esperado')" >&2; exit 1; fi
  if [[ $HTTPS -eq 1 ]]; then
    local c; c="$(rsh "$VM_BACKEND_HOST" "curl -s -o /dev/null -w '%{http_code}' --max-time 20 https://$FQDN/api/cientifico/ping" || true)"
    echo "[ok] gateway por HTTPS responde $c sin token (se espera 401)"
  fi
}

if [[ "$QUE" == "datos" || "$QUE" == "todo" ]]; then desplegar_datos; instalar_equipo "$VM_DATOS_HOST"; fi
if [[ "$QUE" == "backend" || "$QUE" == "todo" ]]; then desplegar_backend; instalar_equipo "$VM_BACKEND_HOST"; fi
if [[ "${HTTPS:-0}" -eq 1 ]]; then
  echo "[ok] Despliegue terminado. Gateway: https://$FQDN  Keycloak: https://$FQDN:8443  RabbitMQ: http://$TS_IP_BACKEND:15672"
elif [[ "$QUE" != "datos" ]]; then
  echo "[ok] Despliegue terminado (HTTP provisional). Gateway: http://$TS_IP_BACKEND:8080  Keycloak: http://$TS_IP_BACKEND:8180  RabbitMQ: http://$TS_IP_BACKEND:15672"
else
  echo "[ok] Despliegue de datos terminado."
fi
echo "     Rollback: ssh a la VM, editar BIOHUB_TAG en ~/biohub-backend/.env a una etiqueta anterior (docker images) y 'docker compose up -d'."
