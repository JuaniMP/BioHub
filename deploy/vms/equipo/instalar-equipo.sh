#!/bin/bash
# Prepara en ESTA VM el acceso de solo lectura del equipo (SEC-D-01). Ejecutar como root; es idempotente.
#   sudo bash instalar-equipo.sh [directorio-con-biohub-estado]
# Lo invoca deploy/desplegar.sh en cada despliegue (copia el directorio a la VM).
#
# Resultado:
#   - usuario Linux `equipo`: sin contraseña (bloqueado), sin sudo general, fuera de los grupos sudo y docker;
#     /home/biohub es 750, asi que tampoco ve ~biohub ni los .env (600).
#   - /usr/local/bin/biohub-estado (root:root 755) y /etc/sudoers.d/biohub-equipo: unica elevacion permitida,
#     con ruta y argumentos exactos (ps y logs de una lista blanca de servicios).
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo "Ejecutar como root (sudo)" >&2; exit 1; }
ORIGEN="${1:-$(cd "$(dirname "$0")" && pwd)}"
SCRIPT="$ORIGEN/biohub-estado"
[[ -f "$SCRIPT" ]] || { echo "No existe $SCRIPT" >&2; exit 1; }

if ! id equipo >/dev/null 2>&1; then
  useradd --create-home --shell /bin/bash --comment "Equipo BioHub (solo lectura)" equipo
fi
passwd -l equipo >/dev/null            # sin contraseña: solo entra por Tailscale SSH (ACL + check)
for g in sudo docker adm lxd; do       # por si cloud-init o alguien lo agregó
  if id -nG equipo | tr ' ' '\n' | grep -qx "$g"; then gpasswd -d equipo "$g" >/dev/null; fi
done
chmod 750 /home/biohub                 # ni ~biohub ni los .env son legibles por equipo
chmod 700 /home/equipo

install -m 0755 -o root -g root "$SCRIPT" /usr/local/bin/biohub-estado

# Lista blanca -> una linea de sudoers por comando exacto
SERVICIOS=$(grep -E '^SERVICIOS_(BACKEND|DATOS)=' "$SCRIPT" | sed -E 's/^[A-Z_]+="([^"]*)"$/\1/' | tr '\n' ' ')
[[ -n "${SERVICIOS// /}" ]] || { echo "No se pudo leer la lista blanca" >&2; exit 1; }
tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
{
  echo "# Gestionado por deploy/vms/equipo/instalar-equipo.sh. No editar a mano."
  echo "# Solo lectura: estado y logs. Ruta y argumentos EXACTOS (sin comodines)."
  cmds="/usr/local/bin/biohub-estado ps"
  for s in $SERVICIOS; do
    [[ "$s" =~ ^[a-z0-9-]+$ ]] || { echo "servicio invalido: $s" >&2; exit 1; }
    cmds+=", /usr/local/bin/biohub-estado logs $s"
  done
  echo "Cmnd_Alias BIOHUB_ESTADO = $cmds"
  echo "equipo ALL=(root) NOPASSWD: BIOHUB_ESTADO"
} > "$tmp"
visudo -cf "$tmp" >/dev/null
install -m 0440 -o root -g root "$tmp" /etc/sudoers.d/biohub-equipo
echo "[equipo] usuario 'equipo' y /usr/local/bin/biohub-estado listos en $(hostname)"
