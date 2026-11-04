# pruebas-carga

Pruebas de carga de BioHub con [k6](https://k6.io) sobre un **Compose efímero y local** (D30: nunca contra Aiven ni contra
un despliegue). Miden la consulta pública (ASR1/RNF1, pico x60) y su comportamiento mientras el lado científico escribe
(ASR4/RNF5). Los criterios para que una corrida cuente como evidencia están en la decisión **D42**
(`docs/equipo/03-arquitectura.md`); este README resume lo que hay que hacer para cumplirlos.

## Qué hay

| Archivo | Para qué sirve |
|---|---|
| `construir-imagen.sh` | Construye `biohub/servicio-consulta-publica:carga` a partir del jar ya compilado (`mvn -DskipTests package` en `servicio-consulta-publica/`). No toca el Dockerfile ni `target/`. |
| `compose-carga.yml` | Compose **efímero**: N réplicas de `servicio-consulta-publica` (con límite de CPU y memoria) detrás de NGINX (`nginx-carga.conf`, puerto `127.0.0.1:18080`). Reutiliza `mysql-lectura`, `redis` y `rabbitmq` del compose raíz (red `biohub_default`), que deben estar arriba. |
| `datos/sembrar.sh` + `semilla-fichas.sql` | Siembra 2.000 fichas sintéticas en `mysql-lectura` (UUID `c0a9a000-0000-4000-8000-<12 hex>`, sin coordenadas). Idempotente. |
| `datos/limpiar.sh` | Borra lo que dejó la carga: fichas con prefijo `c0a9a000`, claves de caché `biohub:publica:*` en Redis y, en RabbitMQ, el exchange temporal `biohub.eventos.carga` y la cola de carga con su DLQ. No toca la cola ni la DLQ de desarrollo. |
| `correr-k6.sh` | Ejecuta un script k6 con la imagen `grafana/k6` (versión fija en `K6_VERSION`) y deja el JSON en `resultados/`. |
| `k6/consulta-publica-pico.js` | **ASR1/RNF1.** Perfil de tasa constante: base N req/s, rampa, pico N x `MULT` (60 por defecto), bajada y post-pico. Mide % de 200, 5xx/timeouts, p95 por fase y por ventana de 15 s, y la estabilización tras la rampa. Mezcla 60 % fichas (85 % "virales", caché caliente) y 40 % búsquedas. |
| `k6/carga-cientifica-simultanea.js` | **ASR4/RNF5 (CG-04).** Mide la lectura pública con y sin carga científica (`CIENTIFICA=0|1`). La carga son eventos `hallazgo.registrado`/`hallazgo.reclasificado` publicados a RabbitMQ y consumidos por consulta-publica (SELECT ... FOR UPDATE + INSERT/UPDATE e invalidación de caché). Al final comprueba que la **DLQ de carga esté vacía** (umbral `dlq_mensajes`) y reporta eventos publicados, procesados (ack) y pendientes. |
| `k6/registro-hallazgo.js` | **Pendiente** (depende de HU-01, módulo registro). Esqueleto para RNF2/RNF4. |
| `k6/lib/comun.js` | Utilidades compartidas (mezcla de peticiones, ids de la semilla, ventanas). |
| `resultados/` | JSON de cada corrida. Los que empiezan por `descartado-` documentan fallos del banco de pruebas y **no cuentan como evidencia** (D42). |

## Cómo correrlo

Requisitos: Docker, el `.env` de la raíz y la infraestructura del compose raíz arriba (`docker compose up -d`: MySQL de
lectura, Redis, RabbitMQ, Keycloak). Todos los comandos, desde `pruebas-carga/`:

```bash
# 1. Jar e imagen de la consulta pública
(cd ../servicio-consulta-publica && mvn -DskipTests package)
./construir-imagen.sh

# 2. Compose de carga (elige las réplicas) y esperar a que responda
docker compose -p biohub-carga -f compose-carga.yml --env-file ../.env up -d --scale consulta-publica=2
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18080/api/publica/fichas     # 200

# 3. Datos de carga
./datos/sembrar.sh

# 4a. Pico x60 (ASR1). Con N=20 el pico es de 1.200 req/s
REPLICAS=2 CPUS=2 ./correr-k6.sh k6/consulta-publica-pico.js -e N=20 -e SALIDA=/resultados/asr1-2replicas-n20.json

# 4b. Carga científica simultánea (ASR4): el par sin / con carga
./correr-k6.sh k6/carga-cientifica-simultanea.js -e CIENTIFICA=0 -e SALIDA=/resultados/asr4-sin.json
./correr-k6.sh k6/carga-cientifica-simultanea.js -e CIENTIFICA=1 -e SALIDA=/resultados/asr4-con.json
#     humo corto: -e R=10 -e EV=5 -e DUR=15s

# 5. Apagar y limpiar (en este orden: limpiar.sh borra la cola de carga, que las réplicas no deben seguir usando)
docker compose -p biohub-carga -f compose-carga.yml --env-file ../.env down -v
./datos/limpiar.sh
```

Parámetros de `consulta-publica-pico.js`: `BASE_URL` (por defecto `http://127.0.0.1:18080`), `N`, `MULT`, `T_BASE`, `T_RAMP`,
`T_PICO`, `T_BAJA`, `T_POST`, `MAX_VUS`, `POOL`, `SALIDA`. De `carga-cientifica-simultanea.js`: `R` (req/s públicas),
`EV` (eventos/s), `DUR`, `CIENTIFICA`, `COLA`, `RMQ_URL`, `SALIDA`. Variables del compose: `CARGA_CPUS` (CPU por réplica,
2 por defecto) y `COLA_CARGA` (cola de carga, `consulta-publica.carga.eventos`).

### Eventos de la carga científica y la verificación del publicador

consulta-publica verifica el `user_id` AMQP de todo `hallazgo.*` (ARQ-13, SEC-J-01) y **esa verificación no se desactiva
para la carga**. Por eso:

- k6 publica con `user_id` = `RMQ_USER` (el usuario de RabbitMQ con el que se autentica; el broker rechaza un `user_id`
  distinto) y `compose-carga.yml` define `BIOHUB_EVENTOS_PUBLICADOR_CIENTIFICO` = `RABBITMQ_USER`.
- `version` va entre 1 y 1.000.000 (`DatosHallazgo.VERSION_MAXIMA`) y crece 1 cada 4 s por hallazgo, para no disparar la
  alerta de salto anómalo.
- Las réplicas de carga consumen una cola propia (`consulta-publica.carga.eventos`, DLQ `...carga.eventos.dlq`), no la de
  desarrollo. Si la DLQ de carga termina con mensajes, la prueba falla; la causa suele ser un `user_id` distinto, una
  versión fuera de rango o un campo inválido (consulta-publica registra el motivo en su log).

## Secretos

- No hay credenciales en los scripts ni en `resultados/`: todo sale del `.env` en tiempo de ejecución.
- Ninguna contraseña ni JWT viaja como argumento (se vería con `ps`): `correr-k6.sh` pasa `RMQ_USER`/`RMQ_PASS` por
  variable de entorno; para `registro-hallazgo.js` haz `export TOKEN=<jwt>` (no `-e TOKEN=...`). `sembrar.sh` y
  `limpiar.sh` pasan `MYSQL_PWD`, `REDISCLI_AUTH` y las credenciales de RabbitMQ por entorno o por stdin.
- La imagen de k6 está fijada (`grafana/k6:2.3.0`; cámbiala con `K6_VERSION`), no `latest`.
- Solo local: NGINX y las réplicas escuchan en `127.0.0.1` o en la red de Docker.

## Qué guardar con cada corrida (D42)

Una corrida cuenta como evidencia de ASR1/ASR4 solo si su JSON registra, en `parametros`:

| Metadato | Cómo se registra |
|---|---|
| N y multiplicador | `-e N=... -e MULT=...` (el script los guarda como `N`, `MULT`, `pico_req_s`) |
| Réplicas | `REPLICAS=<n>` (el mismo valor de `--scale consulta-publica=<n>`) antes de `./correr-k6.sh` |
| CPU por réplica | `CPUS=<n>` (el mismo valor de `CARGA_CPUS` del compose) |
| Commit | automático (`git rev-parse --short HEAD`; añade `-sin-commit` si el árbol tiene cambios sin commitear) |
| Red de k6 | automático (`RED_K6`, `host` por defecto; `RED_K6=biohub_default` para correr dentro de la red del compose) |

Además:

1. **Éxito** = 200 / (peticiones + iteraciones descartadas). El JSON lo da en `exito_d42_pct`; la carga que k6 no pudo
   emitir cuenta como fallo.
2. **Estabilización** como en `consulta-publica-pico.js`: ventanas de 15 s con p95 < 2 s desde el fin de la rampa
   (`estabilizacion_s`, `ventanas`).
3. Corre siempre contra este Compose efímero, nunca contra Aiven (D30).
4. Si el banco de pruebas falla (NGINX sin descriptores, k6 sin VUs, etc.), renombra el JSON con prefijo `descartado-` y
   documenta el motivo: no cuenta.
5. ASR1 "demostrado" exige además una corrida donde se active la degradación (contando las respuestas `modoDegradado: true`)
   y la comparación de 1 y 2 réplicas en un punto donde una sola no alcance. ASR4 exige el par `CIENTIFICA=0` / `CIENTIFICA=1`
   y, en el segundo, DLQ en 0 y eventos procesados igual a los publicados.

## Reglas

- Ejecuta las pruebas solo contra el entorno local de pruebas.
- No guardes contraseñas ni tokens en los scripts: léelos de variables de entorno.
- Los eventos de carga nunca deben incluir coordenadas ni datos reales (la semilla es sintética).
