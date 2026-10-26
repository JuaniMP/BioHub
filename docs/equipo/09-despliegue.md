# 09 - Despliegue on-premise (2 VMs KVM + Docker + Tailscale)

Alcance acordado (ver `00-plan.md`, D11/D12): backend en dos VMs en esta misma máquina, red privada con Tailscale, sin Funnel, sin Keycloak de producción (sigue `start-dev` con H2), sin Vercel. Las contraseñas **nunca** se suben al repositorio.

## 1. Topología

```
 Anfitrión (KVM, red NAT libvirt "default" 192.168.122.0/24)  -- SSH de administración --+
                                                                                          |
 +-------------- VM biohub-backend (4 vCPU, 6 GB, 30 GB) ---------------+                 |
 |  Docker: api-gateway, servicio-cientifico/equipos/consulta/reportes, |<----------------+
 |          keycloak (start-dev), rabbitmq, redis    (red "biohub-interna")|
 |  Publicado SOLO en la IP Tailscale de la VM: 8080, 8180, 15672       |
 +--------------------------------+-------------------------------------+
                                  | Tailscale (cifrado), MySQL 3307-3310
 +--------------------------------v-------------------------------------+
 |  VM biohub-datos (2 vCPU, 4 GB, 40 GB)                               |
 |  Docker: 4 x MySQL 8.4. Publicado SOLO en la IP Tailscale de la VM   |
 +----------------------------------------------------------------------+
```

| VM | Puerto | Quién lo ve | Qué es |
|---|---|---|---|
| backend | 8080 | tailnet (IP Tailscale de backend) | api-gateway |
| backend | 8180 | tailnet | Keycloak (contenedor 8080) |
| backend | 15672 | tailnet | panel de administración de RabbitMQ |
| backend | 8081-8084, 5672, 6379 | solo red Docker interna | servicios, RabbitMQ AMQP, Redis (sin `ports:`) |
| datos | 3307 / 3308 / 3309 / 3310 | tailnet, y MySQL solo acepta al usuario desde la IP Tailscale de backend | transaccional / lectura / analítica / gateway |

Firewall (ufw, en ambas VMs): entrante denegado por defecto; SSH (22) solo desde `192.168.122.0/24`; todo permitido en `tailscale0`. Nota: Docker inserta sus propias reglas de iptables para los puertos publicados y se salta ufw; por eso todos los `ports:` van ligados explícitamente a la IP de Tailscale y nunca a `0.0.0.0`.

## 2. Archivos

| Ruta | Función |
|---|---|
| `deploy/vms/crear-vms.sh` | Crea las 2 VMs (idempotente), descarga la cloud image de Ubuntu 24.04 |
| `deploy/vms/user-data.tpl` | cloud-init común: usuario `biohub`, Docker, Tailscale (sin `up`), ufw, zona horaria, actualizaciones automáticas |
| `deploy/generar-env.sh` | Crea `deploy/backend/.env` y `deploy/datos/.env` con contraseñas aleatorias |
| `deploy/desplegar.sh` | Construye imágenes, las envía a la VM y levanta los stacks |
| `deploy/backend/docker-compose.yml` | Stack de la VM backend |
| `deploy/backend/rabbitmq/generar-definitions.sh` | Genera `definitions.json` (usuarios con hash + permisos) |
| `deploy/datos/docker-compose.yml`, `deploy/datos/init/*.sh` | Stack MySQL y creación de esquemas/usuarios limitados al host de backend |
| `deploy/datos/backup/` | `backup.sh` (mysqldump con rotación) y unidades systemd `biohub-backup.{service,timer}` |
| `<servicio>/Dockerfile`, `.dockerignore` | Imagen por servicio (eclipse-temurin:21-jre, usuario no root uid 10001, HEALTHCHECK a `/actuator/health`) |

## 3. Fase 1 (hecha): VMs listas

```bash
deploy/vms/crear-vms.sh
```
- Llave SSH dedicada: `~/.ssh/biohub_vms` (ed25519, sin passphrase, fuera del repo). Usuario `biohub` con sudo sin contraseña.
- Acceso: `ssh -i ~/.ssh/biohub_vms biohub@<IP NAT>` (IP con `virsh -c qemu:///system domifaddr biohub-backend`). Las IP NAT las da DHCP y podrían cambiar; `desplegar.sh` las vuelve a consultar cada vez (o fíjalas con `VM_BACKEND_HOST` / `VM_DATOS_HOST`).
- Las VMs tienen autoinicio con el anfitrión.
- Imágenes: se construyen en el anfitrión (`mvn package` + `docker build`) y se envían con `docker save | ssh ... docker load`; la VM no necesita Maven ni acceso al código.

## 4. Fase 2: unir a Tailscale y desplegar

1. (Hecho el 2026-10-22) Primero la dueña guarda las ACL en la consola de Tailscale (`tagOwners` para `tag:backend` y `tag:datos`; `tag:backend` -> `tag:datos:3307-3310`; `autogroup:member` -> `tag:backend:22,8080,8180,15672` y `tag:datos:22,3307-3310`; bloque `ssh` para `autogroup:member` con el usuario `biohub`). Luego, desde el anfitrión:
   ```bash
   ssh -i ~/.ssh/biohub_vms biohub@<IP NAT backend> 'sudo tailscale up --hostname=biohub-backend --advertise-tags=tag:backend --ssh'
   ssh -i ~/.ssh/biohub_vms biohub@<IP NAT datos>   'sudo tailscale up --hostname=biohub-datos --advertise-tags=tag:datos --ssh'
   ```
   y aprobar cada enlace. `--ssh` activa Tailscale SSH: los miembros entran con `ssh biohub@biohub-backend` sin llave (el puerto 22 del ufw solo se abre a la red NAT; por `tailscale0` pasa todo y lo filtran las ACL). IPs asignadas: backend 100.76.214.94, datos 100.94.121.117.
2. En el anfitrión, una vez:
   ```bash
   deploy/generar-env.sh          # crea los dos .env con contraseñas aleatorias (600, ignorados por git)
   ```
3. Desplegar todo (datos primero, luego backend). Las IP de Tailscale se leen de cada VM con `tailscale ip -4`; se pueden forzar con `TS_IP_BACKEND=100.x.y.z TS_IP_DATOS=100.x.y.z`:
   ```bash
   deploy/desplegar.sh todo
   ```
   Variantes: `deploy/desplegar.sh datos`, `deploy/desplegar.sh backend`, `deploy/desplegar.sh backend --sin-build` (reutiliza imágenes `latest` ya construidas).
4. `biohub-backend` se resuelve con MagicDNS (dominio `tailcfd75e.ts.net`) en cualquier equipo del tailnet; es necesario usar ese nombre para que el issuer del token coincida. Solo si MagicDNS está desactivado: `/etc/hosts` con `100.76.214.94 biohub-backend`.
5. Comprobaciones: `curl http://biohub-backend:8180/realms/biohub/.well-known/openid-configuration`; `docker compose ps` en cada VM; panel RabbitMQ en `http://biohub-backend:15672` (usuario `biohub`). Prueba completa con token y evento: ver `06-plan-pruebas.md` y `docs/guia-tailscale.md`.

**IP de origen en MySQL (resuelto).** Con el `docker-proxy` por defecto, MySQL veía las conexiones desde `172.18.0.1` y rechazaba a los usuarios `app@'100.76.214.94'`. Solución: en la VM datos Docker corre con `"userland-proxy": false` (`/etc/docker/daemon.json`, lo pone cloud-init vía `__USERLAND_PROXY__` en `deploy/vms/user-data.tpl`), así se publica solo con NAT y se conserva la IP real. En la VM backend se deja `true`. Si cambia la IP de Tailscale de backend, renombrar los usuarios: `RENAME USER 'u'@'ip_vieja' TO 'u'@'ip_nueva';` por cada usuario de aplicación (los scripts init solo corren con el volumen vacío).

**Keycloak sin exigencia de HTTPS (D13).** Las IPs 100.x de Tailscale no son "privadas" para Keycloak, que con `sslRequired=external` respondía `HTTPS required` al pedir tokens. `desplegar.sh backend` aplica `sslRequired=NONE` en los realms `master` y `biohub` tras levantar el stack. Válido solo mientras Keycloak sea accesible únicamente por la tailnet (ACL + WireGuard). Al pasar a HTTPS (`tailscale serve`/Funnel) volver a `external`.

## 5. Variables

`deploy/backend/.env` y `deploy/datos/.env` (plantillas con comentarios en los `.env.example`). Solo caracteres `[A-Za-z0-9_]`.

| Variable | Dónde | Descripción |
|---|---|---|
| `TS_IP_BACKEND`, `TS_IP_DATOS` | ambos | IP Tailscale (100.x) de cada VM; las rellena `desplegar.sh` |
| `BIOHUB_TAG` | backend | etiqueta de las imágenes `biohub/<servicio>:<tag>` (la fija `desplegar.sh`) |
| `MYSQL_*_USER`, `MYSQL_*_PASSWORD` | ambos | **Deben ser idénticas en las dos VMs** (`generar-env.sh` las escribe iguales) |
| `MYSQL_*_ROOT_PASSWORD` | datos | root, solo uso local en la VM (no existe `root@'%'`) |
| `REDIS_PASSWORD` | backend | contraseña de Redis |
| `RABBITMQ_ADMIN_USER/PASSWORD` | backend | administrador del panel |
| `RABBITMQ_{CIENTIFICO,CONSULTA,REPORTES}_PASSWORD` | backend | usuarios `cientifico_mq`, `consulta_mq`, `reportes_mq` |
| `KEYCLOAK_ADMIN_USER/PASSWORD` | backend | administrador de Keycloak |
| `KEYCLOAK_TEST_USERS_PASSWORD` | backend | contraseña de los usuarios de prueba del realm (>= 12 caracteres) |
| `CORS_ORIGENES` | backend | orígenes permitidos por el gateway |
| `BACKUP_DIR`, `BACKUP_RETENCION_DIAS` | datos | destino y rotación de los backups |

Cada servicio recibe solo sus propias variables (no hay `env_file` compartido; SEC-12).

## 6. RabbitMQ (SEC-04)

Vhost `/`. `rabbitmq/generar-definitions.sh` crea `definitions.json` con hashes SHA-256 con sal (nunca contraseñas en claro) y RabbitMQ lo carga en cada arranque (`load_definitions`), así que es reproducible e idempotente. La topología (exchange `biohub.eventos`, DLX `biohub.eventos.dlx`, colas y `.dlq`) la declara Spring.

| Usuario | configure / write / read | Etiqueta |
|---|---|---|
| `cientifico_mq` | `^biohub\.eventos$` / `^biohub\.eventos$` / nada | - |
| `consulta_mq` | `^(biohub\.eventos\|biohub\.eventos\.dlx\|consulta-publica\.eventos(\.dlq)?)$` en los tres | - |
| `reportes_mq` | igual con `reportes\.eventos` | - |
| `biohub` | `.*` | administrator |

El usuario `guest` no existe. Si cambias contraseñas: editar `.env`, volver a ejecutar `desplegar.sh backend` (regenera y recarga las definiciones al reiniciar el contenedor).

## 7. Pruebas del código (antes de desplegar)

```bash
mvn -B verify                              # tests de los 5 servicios
(cd web-interna && npm ci && npm test)     # frontend
```
`desplegar.sh` compila con `-DskipTests`; los tests deben haberse corrido antes.

## 8. Operación

- Estado: `ssh -i ~/.ssh/biohub_vms biohub@<ip> 'cd ~/biohub-backend && docker compose ps'` (o `~/biohub-datos`).
- Logs: `docker compose logs -f api-gateway`.
- **Backups (VM datos):** `~/biohub-datos/backup/backup.sh` genera `$BACKUP_DIR/<instancia>/<instancia>-AAAAMMDD-HHMMSS.sql.gz` (permisos 600) y borra los de más de `BACKUP_RETENCION_DIAS` (14). Programación sugerida: timer systemd (`backup/biohub-backup.timer`, diario 02:30) o cron `30 2 * * *`; instrucciones y restauración en `deploy/datos/backup/README.txt`. Conviene copiar los backups fuera de la VM.
- **Rollback del backend:** cada despliegue deja imágenes con etiqueta de fecha-hora (`docker images 'biohub/*'`). En la VM: editar `BIOHUB_TAG=<etiqueta anterior>` en `~/biohub-backend/.env` y ejecutar `docker compose up -d`. Las migraciones Flyway ya aplicadas no se revierten solas: si una migración fue destructiva, restaurar el backup de MySQL.
- Datos: los volúmenes con nombre sobreviven a `docker compose down`; `down -v` los borra (no usarlo en producción).

## 9. Limitaciones conocidas

- Keycloak en `start-dev` con H2 y usuarios de prueba: no es producción (alcance acordado).
- `KC_HOSTNAME=http://biohub-backend:8180`: los clientes resuelven `biohub-backend` por MagicDNS; los contenedores, por el alias de red `biohub-backend` del contenedor de Keycloak (que escucha en `KC_HTTP_PORT=8180`). Se descartó `extra_hosts` hacia la IP de Tailscale porque ufw bloquea el hairpin contenedor -> IP del propio host.
- Keycloak sin HTTPS dentro de la tailnet (D13). HTTPS/Funnel pendiente.
- La app solo está en línea cuando el portátil anfitrión y las dos VMs están encendidos.
- Las webs (Vercel) no forman parte de esta iteración; `CORS_ORIGENES` solo incluye los orígenes locales.
