#!/usr/bin/env bash
# Genera rabbitmq/definitions.json a partir de deploy/backend/.env (usuarios por servicio, SEC-04).
# Las contraseñas se guardan como hash SHA-256 con sal (formato de RabbitMQ), nunca en claro.
# Uso: deploy/backend/rabbitmq/generar-definitions.sh [ruta/.env]
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"
ENV="${1:-$AQUI/../.env}"
[[ -f "$ENV" ]] || { echo "No existe $ENV (ejecuta deploy/generar-env.sh)" >&2; exit 1; }
python3 -I - "$ENV" "$AQUI/definitions.json" <<'PY'
import base64, hashlib, json, os, re, sys

env = {}
for linea in open(sys.argv[1], encoding="utf-8"):
    m = re.match(r"^([A-Z0-9_]+)=([^#\s]*)", linea)
    if m:
        env[m.group(1)] = m.group(2)

def hash_rmq(pw: str) -> str:
    sal = os.urandom(4)
    return base64.b64encode(sal + hashlib.sha256(sal + pw.encode()).digest()).decode()

def requerido(k):
    v = env.get(k, "")
    if not re.fullmatch(r"[A-Za-z0-9_]{8,}", v):
        sys.exit(f"{k} falta o no es [A-Za-z0-9_]{{8,}}")
    return v

admin = env.get("RABBITMQ_ADMIN_USER", "")
if not re.fullmatch(r"[A-Za-z0-9_]+", admin):
    sys.exit("RABBITMQ_ADMIN_USER falta o no es [A-Za-z0-9_]+")
# Toda la topologia (exchange biohub.eventos, DLX biohub.eventos.dlx, colas y .dlq) la declara Spring.
EV = r"^biohub\.eventos$"
usuarios = [
    # nombre, clave_env, tags, (configure, write, read)
    (admin, "RABBITMQ_ADMIN_PASSWORD", "administrator", (".*", ".*", ".*")),
    # Solo publica: puede declarar y escribir en el exchange; no lee ninguna cola.
    ("cientifico_mq", "RABBITMQ_CIENTIFICO_PASSWORD", "", (EV, EV, "^$")),
    ("consulta_mq", "RABBITMQ_CONSULTA_PASSWORD", "",
     (r"^(biohub\.eventos|biohub\.eventos\.dlx|consulta-publica\.eventos(\.dlq)?)$",) * 3),
    ("reportes_mq", "RABBITMQ_REPORTES_PASSWORD", "",
     (r"^(biohub\.eventos|biohub\.eventos\.dlx|reportes\.eventos(\.dlq)?)$",) * 3),
]
defs = {
    "rabbit_version": "4.1",
    "users": [{"name": n, "password_hash": hash_rmq(requerido(k)),
               "hashing_algorithm": "rabbit_password_hashing_sha256", "tags": t}
              for n, k, t, _ in usuarios],
    "vhosts": [{"name": "/"}],
    "permissions": [{"user": n, "vhost": "/", "configure": p[0], "write": p[1], "read": p[2]}
                    for n, _, _, p in usuarios],
    "topic_permissions": [], "parameters": [], "global_parameters": [], "policies": [],
    "queues": [], "exchanges": [], "bindings": [],
}
fd = os.open(sys.argv[2], os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o644)  # 644: lo lee el usuario rabbitmq del contenedor
with os.fdopen(fd, "w") as f:
    json.dump(defs, f, indent=2)
print(f"definitions.json generado ({len(usuarios)} usuarios)")
PY
