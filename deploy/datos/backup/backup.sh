#!/usr/bin/env bash
# Copia de seguridad de las 4 instancias MySQL (mysqldump logico, consistente: --single-transaction).
# Guarda $BACKUP_DIR/<instancia>/<instancia>-AAAAMMDD-HHMMSS.sql.gz y borra las de mas de
# BACKUP_RETENCION_DIAS dias. La contraseña de root se lee DENTRO del contenedor (no sale por argumentos).
# Ejecutar en la VM biohub-datos (usuario del grupo docker). Lee ../.env.
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"
ENV="$AQUI/../.env"
if [[ -f "$ENV" ]]; then
  BACKUP_DIR="${BACKUP_DIR:-$(sed -n 's/^BACKUP_DIR=//p' "$ENV")}"
  BACKUP_RETENCION_DIAS="${BACKUP_RETENCION_DIAS:-$(sed -n 's/^BACKUP_RETENCION_DIAS=//p' "$ENV")}"
fi
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups-biohub}"
RETENCION="${BACKUP_RETENCION_DIAS:-14}"
FECHA="$(date +%Y%m%d-%H%M%S)"
umask 077
fallos=0
for inst in transaccional lectura analitica gateway; do
  dir="$BACKUP_DIR/$inst"; mkdir -p "$dir"
  destino="$dir/$inst-$FECHA.sql.gz"
  if docker exec "biohub-mysql-$inst" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot -h127.0.0.1 --single-transaction --routines --events --triggers --all-databases' \
      | gzip -9 > "$destino.tmp" && [[ -s "$destino.tmp" ]]; then
    mv "$destino.tmp" "$destino"; echo "[ok] $destino"
  else
    rm -f "$destino.tmp"; echo "[FALLO] $inst" >&2; fallos=1
  fi
  find "$dir" -name '*.sql.gz' -mtime +"$RETENCION" -delete
done
exit $fallos
