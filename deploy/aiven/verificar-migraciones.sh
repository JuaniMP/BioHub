#!/usr/bin/env bash
# Revisa estaticamente las migraciones Flyway contra las restricciones de Aiven MySQL 8.
# Uso:  deploy/aiven/verificar-migraciones.sh [directorio ...]
#       (sin argumentos revisa todos los src/main/resources/db/migration del repo)
# Sale con 1 si encuentra algo. Es una red de seguridad barata, NO reemplaza la prueba real con
# Testcontainers (MigracionesUbicacionTest / MigracionesIntegracionTest): revisa lo que Aiven rechaza y
# que un MySQL local sin restricciones aceptaria.
set -euo pipefail
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
if [[ $# -gt 0 ]]; then DIRS=("$@"); else
  mapfile -t DIRS < <(find "$RAIZ" -path '*/node_modules' -prune -o -path '*/target' -prune -o \
                      -type d -path '*/src/main/resources/db/migration' -print | sort)
fi
exec python3 -I - "${DIRS[@]}" <<'PY'
import re, sys, pathlib

REGLAS = [  # (regex, mensaje)
    (r"\bCREATE\s+(?:OR\s+REPLACE\s+)?(?:DEFINER\s*=\s*\S+\s+)?TRIGGER\b", "TRIGGER: requiere privilegio SUPER con binlog activo (Aiven no lo da)"),
    (r"\bCREATE\s+(?:DEFINER\s*=\s*\S+\s+)?(?:FUNCTION|PROCEDURE)\b", "FUNCTION/PROCEDURE: exige log_bin_trust_function_creators (SET GLOBAL, sin SUPER)"),
    (r"\bCREATE\s+(?:DEFINER\s*=\s*\S+\s+)?EVENT\b", "EVENT: el planificador de eventos no se administra sin SUPER"),
    (r"\bDEFINER\s*=", "DEFINER=: el usuario de migracion no puede asignar otro definer"),
    (r"\bSET\s+(?:GLOBAL|PERSIST|PERSIST_ONLY)\b", "SET GLOBAL/PERSIST: requiere SUPER o SYSTEM_VARIABLES_ADMIN"),
    (r"\bCREATE\s+(?:DATABASE|SCHEMA)\b", "CREATE DATABASE: las bases las crea 01-bases-y-usuarios.sql con avnadmin"),
    (r"\b(?:CREATE|ALTER|DROP)\s+USER\b", "CREATE/ALTER/DROP USER: solo en el aprovisionamiento con avnadmin"),
    (r"\bGRANT\b|\bREVOKE\b", "GRANT/REVOKE: solo en el aprovisionamiento con avnadmin"),
    (r"\bLOAD\s+DATA\b|\bINTO\s+(?:OUTFILE|DUMPFILE)\b", "LOAD DATA / OUTFILE: requiere FILE, no disponible"),
    (r"\bCREATE\s+TABLE\b[^;]*?\)\s*(?:ENGINE[^;]*?)?\bSELECT\b|\bCREATE\s+TABLE\b[^;(]*\bSELECT\b", "CREATE TABLE ... SELECT: no admitido con sql_require_primary_key"),
    (r"\bENGINE\s*=\s*(?!InnoDB\b)\w+", "motor distinto de InnoDB"),
    (r"\|\|", "'||' concatena con sql_mode ANSI (PIPES_AS_CONCAT) y es OR sin el: usa CONCAT()"),
    (r"\bUSE\s+`?\w+`?\s*;", "USE <base>: la base la fija la URL de conexion"),
    (r"\b(?:cientifico|consulta_publica|equipos|reportes|gateway)\s*\.\s*`?\w+`?\s*(?:\(|\bSET\b|\bVALUES\b|\bSELECT\b)", "tabla con prefijo de base: usa nombres sin calificar"),
]

def limpiar(sql: str) -> str:
    sql = re.sub(r"/\*.*?\*/", lambda m: "\n" * m.group(0).count("\n"), sql, flags=re.S)
    return "\n".join(re.sub(r"--.*$|#.*$", "", l) if not re.search(r"'[^']*(--|#)[^']*'", l) else re.sub(r"\s--\s.*$", "", l)
                     for l in sql.splitlines())

def sin_literales(sql: str) -> str:
    return re.sub(r"'(?:[^'\\]|\\.|'')*'", "''", sql)

problemas = 0
revisados = 0
for d in map(pathlib.Path, sys.argv[1:]):
    for f in sorted(d.glob("*.sql")):
        revisados += 1
        crudo = f.read_text(encoding="utf-8")
        texto = limpiar(crudo)
        solo_codigo = sin_literales(texto)
        def linea(pos): return texto.count("\n", 0, pos) + 1
        for patron, msg in REGLAS:
            for m in re.finditer(patron, solo_codigo, flags=re.I | re.S):
                print(f"{f}:{linea(m.start())}: {msg}"); problemas += 1
        if re.search(r'"', solo_codigo):
            n = solo_codigo.index('"'); print(f"{f}:{linea(n)}: comillas dobles (con ANSI_QUOTES son identificadores, no texto): usa comilla simple"); problemas += 1
        # Toda CREATE TABLE debe declarar PRIMARY KEY (sql_require_primary_key=ON)
        for m in re.finditer(r"\bCREATE\s+(?:TEMPORARY\s+)?TABLE\b(.*?);", solo_codigo, flags=re.I | re.S):
            if not re.search(r"\bPRIMARY\s+KEY\b", m.group(1), flags=re.I):
                print(f"{f}:{linea(m.start())}: CREATE TABLE sin PRIMARY KEY (Aiven: sql_require_primary_key=ON)"); problemas += 1
            if not re.search(r"\butf8mb4\b", m.group(1), flags=re.I):
                print(f"{f}:{linea(m.start())}: aviso: CREATE TABLE sin CHARSET utf8mb4 explicito"); problemas += 1
        # ALTER ... DROP PRIMARY KEY dejaria la tabla sin PK
        for m in re.finditer(r"\bDROP\s+PRIMARY\s+KEY\b(?![^;]*\bADD\s+PRIMARY\s+KEY\b)", solo_codigo, flags=re.I):
            print(f"{f}:{linea(m.start())}: DROP PRIMARY KEY sin ADD PRIMARY KEY en la misma sentencia"); problemas += 1
if revisados == 0:
    print("No se encontraron migraciones .sql"); sys.exit(1)
print(f"{revisados} archivo(s) revisado(s), {problemas} problema(s).")
sys.exit(1 if problemas else 0)
PY
