#!/usr/bin/env bash
# Publica el gateway y Keycloak por HTTPS DENTRO de la tailnet con `tailscale serve` (SEC-D-02, retira D13).
# Se ejecuta en la VM biohub-backend como root (lo invoca deploy/desplegar.sh; tambien a mano).
#
#   sudo ./tailscale-https.sh estado     ¿hay certificados HTTPS? ¿que esta publicado? (codigo 0 = certificados disponibles)
#   sudo ./tailscale-https.sh aplicar    publica 443 -> gateway y 8443 -> Keycloak (persistente: --bg)
#   sudo ./tailscale-https.sh quitar     elimina la configuracion de serve (vuelve al modo HTTP)
#
# Requisito unico (lo hace la dueña, una vez, en la consola): https://login.tailscale.com/admin/dns
# -> "HTTPS Certificates" -> Enable. Mientras no este activo, `aplicar` sale con codigo 3 sin cambiar nada.
#
#   https://<FQDN>        (443)  -> http://127.0.0.1:8080   api-gateway
#   https://<FQDN>:8443          -> http://127.0.0.1:8180   Keycloak
# Solo serve (visible en la tailnet). NUNCA funnel (Internet publico).
# La configuracion de serve la guarda tailscaled en su estado: sobrevive reinicios de la VM y de Docker.
set -euo pipefail
export LANG=C LC_ALL=C
ACCION="${1:-estado}"
GATEWAY_LOCAL="${GATEWAY_LOCAL:-http://127.0.0.1:8080}"
KEYCLOAK_LOCAL="${KEYCLOAK_LOCAL:-http://127.0.0.1:8180}"
[[ $EUID -eq 0 ]] || { echo "Ejecutar como root (sudo)" >&2; exit 1; }

FQDN="${TS_FQDN:-$(tailscale status --json | jq -r '.Self.DNSName' | sed 's/\.$//')}"
[[ "$FQDN" =~ ^[a-z0-9-]+\.[a-z0-9-]+\.ts\.net$ ]] || { echo "FQDN invalido: '$FQDN'" >&2; exit 1; }

certificados_activos() {
  tailscale status --json | jq -e --arg f "$FQDN" '(.CertDomains // []) | index($f) != null' >/dev/null
}
sin_certificados() {
  cat >&2 <<MSG
[https] La tailnet aun no tiene certificados HTTPS para $FQDN (CertDomains vacio).
        Activalos una vez en https://login.tailscale.com/admin/dns -> "HTTPS Certificates" -> Enable
        (publica el nombre de las maquinas en el registro publico de Certificate Transparency) y repite.
MSG
}
funnel_activo() {
  tailscale serve status --json 2>/dev/null | jq -e '(.AllowFunnel // {}) | to_entries | map(select(.value)) | length > 0' >/dev/null
}

case "$ACCION" in
  estado)
    echo "FQDN: $FQDN"
    if certificados_activos; then echo "Certificados HTTPS: ACTIVOS"; rc=0; else echo "Certificados HTTPS: NO activados (CertDomains vacio)"; rc=3; fi
    echo "--- tailscale serve status ---"; tailscale serve status || true
    funnel_activo && echo "AVISO: hay Funnel activo (Internet publico)" >&2
    exit $rc ;;
  aplicar)
    certificados_activos || { sin_certificados; exit 3; }
    # Pide el certificado ya (Let's Encrypt tarda unos segundos la primera vez) para fallar aqui y no en el primer cliente
    tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
    timeout 180 tailscale cert --cert-file="$tmp/c.crt" --key-file="$tmp/c.key" "$FQDN" >/dev/null
    tailscale serve --bg --yes --https=443  "$GATEWAY_LOCAL"
    tailscale serve --bg --yes --https=8443 "$KEYCLOAK_LOCAL"
    if funnel_activo; then echo "ERROR: Funnel esta activo; desactivalo con 'tailscale funnel reset'" >&2; exit 1; fi
    tailscale serve status
    echo "[https] listo: https://$FQDN (gateway) y https://$FQDN:8443 (Keycloak)" ;;
  quitar)
    tailscale serve reset
    echo "[https] serve eliminado" ;;
  *) sed -n '2,12p' "$0" >&2; exit 2 ;;
esac
