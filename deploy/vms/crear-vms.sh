#!/usr/bin/env bash
# Crea las VMs biohub-backend y biohub-datos en libvirt (qemu:///system). Idempotente:
# si una VM ya existe no se recrea. No requiere sudo (usuario en grupos libvirt/kvm).
set -euo pipefail
export LANG=C LC_ALL=C

AQUI="$(cd "$(dirname "$0")" && pwd)"
V=(virsh -c qemu:///system)
POOL=images
RED=default
LLAVE="$HOME/.ssh/biohub_vms"
CACHE="$HOME/.cache/biohub-vms"
IMG_URL="https://cloud-images.ubuntu.com/noble/current/noble-server-cloudimg-amd64.img"
IMG="$CACHE/noble-server-cloudimg-amd64.img"

# nombre vcpus ram_MB disco_GB
VMS=("biohub-backend 4 6144 30" "biohub-datos 2 4096 40")

mkdir -p "$CACHE"
if [[ ! -f "$LLAVE" ]]; then
  ssh-keygen -t ed25519 -N "" -C "biohub-vms" -f "$LLAVE"
fi
PUB="$(cat "$LLAVE.pub")"
[[ -f "$IMG" ]] || curl -fL -o "$IMG" "$IMG_URL"

crear() {
  local nombre=$1 cpus=$2 ram=$3 disco=$4
  if "${V[@]}" dominfo "$nombre" >/dev/null 2>&1; then
    echo "[=] $nombre ya existe, no se recrea"; return
  fi
  echo "[+] Creando $nombre"
  local tmp; tmp="$(mktemp -d)"
  local proxy=true; [[ "$nombre" == "biohub-datos" ]] && proxy=false
  sed -e "s|__HOSTNAME__|$nombre|g" -e "s|__SSH_PUBKEY__|$PUB|g" -e "s|__USERLAND_PROXY__|$proxy|g" "$AQUI/user-data.tpl" > "$tmp/user-data"
  printf 'instance-id: %s-1\nlocal-hostname: %s\n' "$nombre" "$nombre" > "$tmp/meta-data"
  xorriso -as mkisofs -quiet -o "$tmp/cidata.iso" -V cidata -J -r "$tmp/user-data" "$tmp/meta-data"

  # Disco: volumen qcow2 con la cloud image y luego ampliado
  "${V[@]}" vol-delete "$nombre.qcow2" --pool $POOL >/dev/null 2>&1 || true
  "${V[@]}" vol-create-as $POOL "$nombre.qcow2" "$(stat -c %s "$IMG")" --format qcow2
  "${V[@]}" vol-upload "$nombre.qcow2" "$IMG" --pool $POOL
  "${V[@]}" vol-resize "$nombre.qcow2" "${disco}G" --pool $POOL

  # ISO cloud-init como volumen del pool
  "${V[@]}" vol-delete "$nombre-cidata.iso" --pool $POOL >/dev/null 2>&1 || true
  "${V[@]}" vol-create-as $POOL "$nombre-cidata.iso" "$(stat -c %s "$tmp/cidata.iso")" --format raw
  "${V[@]}" vol-upload "$nombre-cidata.iso" "$tmp/cidata.iso" --pool $POOL
  rm -rf "$tmp"

  virt-install --connect qemu:///system --name "$nombre" --vcpus "$cpus" --memory "$ram" \
    --os-variant ubuntu24.04 --import \
    --disk "vol=$POOL/$nombre.qcow2,bus=virtio" \
    --disk "vol=$POOL/$nombre-cidata.iso,device=cdrom" \
    --network "network=$RED,model=virtio" \
    --graphics none --console none --channel unix,target_type=virtio,name=org.qemu.guest_agent.0 \
    --noautoconsole --autostart
}

for fila in "${VMS[@]}"; do read -r n c r d <<<"$fila"; crear "$n" "$c" "$r" "$d"; done

echo "[i] Esperando IPs (DHCP de la red $RED)..."
for fila in "${VMS[@]}"; do
  read -r n _ <<<"$fila"
  for _ in $(seq 60); do
    ip="$("${V[@]}" domifaddr "$n" --source lease 2>/dev/null | awk '/ipv4/{sub("/.*","",$4);print $4}' | head -1)"
    [[ -n "$ip" ]] && break; sleep 3
  done
  echo "$n -> ${ip:-sin IP aun}   ssh -i $LLAVE biohub@${ip:-?}"
done
echo "cloud-init tarda unos minutos (instala Docker y Tailscale y reinicia la VM)."
echo "Comprobar: ssh -i $LLAVE biohub@<ip> 'cloud-init status --wait; test -f /var/lib/biohub-cloudinit-ok && echo OK'"
