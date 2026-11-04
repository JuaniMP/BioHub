#!/usr/bin/env bash
# Construye biohub/servicio-consulta-publica:carga a partir del jar ya compilado (mvn -DskipTests package), sin tocar el
# Dockerfile del servicio ni la carpeta target/ (copia el jar a un contexto temporal).
set -euo pipefail
cd "$(dirname "$0")/.."
jar=$(ls servicio-consulta-publica/target/servicio-consulta-publica-*.jar | grep -v original | head -1)
ctx=$(mktemp -d); trap 'rm -rf "$ctx"' EXIT
mkdir -p "$ctx/servicio-consulta-publica/target"; cp "$jar" "$ctx/servicio-consulta-publica/target/"
docker build -q -f servicio-consulta-publica/Dockerfile -t biohub/servicio-consulta-publica:carga "$ctx"
