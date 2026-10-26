# 09 - Despliegue on-premise (2 VMs KVM + Docker + Tailscale)

Alcance acordado (ver `00-plan.md`, D11/D12): backend en dos VMs en esta misma máquina, red privada con Tailscale, sin Funnel, sin Keycloak de producción (sigue `start-dev` con H2), sin Vercel. HTTPS dentro de la tailnet con `tailscale serve` (sección 4.1) y acceso del equipo de mínimo privilegio (sección 4.2). Las contraseñas **nunca** se suben al repositorio.

## 1. Topología

```
 Anfitrión (KVM, red NAT libvirt "default" 192.168.122.0/24)  -- SSH de administración --+
                                                                                          |
 +-------------- VM biohub-backend (4 vCPU, 6 GB, 30 GB) ---------------+                 |
 |  Docker: api-gateway, servicio-cientifico/equipos/consulta/reportes, |<----------------+
 |          keycloak (start-dev), rabbitmq, redis    (red "biohub-interna")|
 |  Modo HTTP: 8080, 8180, 15672 en la IP Tailscale de la VM            |
 |  Modo HTTPS: 443 (gateway) y 8443 (Keycloak) vía tailscale serve;    |
 |  8080/8180 solo en 127.0.0.1; 15672 sigue en la IP Tailscale         |
 +--------------------------------+-------------------------------------+
                                  | Tailscale (cifrado), MySQL 3307-3310
 +--------------------------------v-------------------------------------+
 |  VM biohub-datos (2 vCPU, 4 GB, 40 GB)                               |
 |  Docker: 4 x MySQL 8.4. Publicado SOLO en la IP Tailscale de la VM   |
 +----------------------------------------------------------------------+
```

| VM | Puerto | Quién lo ve | Qué es |
|---|---|---|---|
| backend | 443 (HTTPS) | tailnet, `https://biohub-backend.tailcfd75e.ts.net` | api-gateway (modo HTTPS, `tailscale serve`) |
| backend | 8443 (HTTPS) | tailnet | Keycloak (modo HTTPS, `tailscale serve`) |
| backend | 8080 | tailnet (modo HTTP) / solo 127.0.0.1 (modo HTTPS) | api-gateway |
| backend | 8180 | tailnet (modo HTTP) / solo 127.0.0.1 (modo HTTPS) | Keycloak |
| backend | 15672 | tailnet (HTTP en ambos modos) | panel de administración de RabbitMQ |
| backend | 8081-8084, 5672, 6379 | solo red Docker interna | servicios, RabbitMQ AMQP, Redis (sin `ports:`) |
| datos | 3307 / 3308 / 3309 / 3310 | tailnet, y MySQL solo acepta al usuario desde la IP Tailscale de backend | transaccional / lectura / analítica / gateway |

Firewall (ufw, en ambas VMs): entrante denegado por defecto; SSH (22) solo desde `192.168.122.0/24`; todo permitido en `tailscale0`. Nota: Docker inserta sus propias reglas de iptables para los puertos publicados y se salta ufw; por eso todos los `ports:` van ligados explícitamente a la IP de Tailscale y nunca a `0.0.0.0`.

## 2. Archivos

| Ruta | Función |
|---|---|
| `deploy/vms/crear-vms.sh` | Crea las 2 VMs (idempotente), descarga la cloud image de Ubuntu 24.04 |
| `deploy/vms/user-data.tpl` | cloud-init común: usuario `biohub`, Docker, Tailscale (sin `up`), ufw, zona horaria, actualizaciones automáticas |
| `deploy/generar-env.sh` | Crea `deploy/backend/.env` y `deploy/datos/.env` con contraseñas aleatorias |
| `deploy/desplegar.sh` | Construye imágenes, las envía a la VM y levanta los stacks; aplica HTTPS (`BIOHUB_HTTPS`), `sslRequired` de Keycloak, usuario `equipo` y usuario MySQL de solo lectura |
| `deploy/vms/tailscale-https.sh` | En la VM backend: `estado` / `aplicar` / `quitar` de `tailscale serve` (443 -> gateway, 8443 -> Keycloak) |
| `deploy/vms/equipo/` | `instalar-equipo.sh` (usuario `equipo`, sudoers) y `biohub-estado` (estado/logs de solo lectura) |
| `deploy/datos/init/02-lectura-equipo.sh`, `deploy/datos/aplicar-lectura-equipo.sh` | Usuario MySQL `lectura_equipo` (solo lectura): init para instalaciones nuevas y aplicación idempotente a instancias existentes |
| `deploy/tailscale/policy.hujson`, `deploy/tailscale/README.md` | Política ACL de la tailnet (SEC-D-04) y cómo pegarla / completar los correos |
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

1. (Hecho el 2026-10-22 con una política provisional basada en `autogroup:member`; **sustituida por `deploy/tailscale/policy.hujson`**, que la dueña pega en la consola: ver `deploy/tailscale/README.md`.) Primero la dueña guarda las ACL en la consola de Tailscale (`tagOwners` para `tag:backend` y `tag:datos`, reglas por grupos con nombre y bloque `ssh`). Luego, desde el anfitrión:
   ```bash
   ssh -i ~/.ssh/biohub_vms biohub@<IP NAT backend> 'sudo tailscale up --hostname=biohub-backend --advertise-tags=tag:backend --ssh'
   ssh -i ~/.ssh/biohub_vms biohub@<IP NAT datos>   'sudo tailscale up --hostname=biohub-datos --advertise-tags=tag:datos --ssh'
   ```
   y aprobar cada enlace. `--ssh` activa Tailscale SSH: el equipo entra con `ssh equipo@biohub-backend` y la admin con `ssh biohub@biohub-backend`, sin llave y con reautenticación cada 12 h (modo `check`). El puerto 22 del ufw solo se abre a la red NAT; por `tailscale0` pasa todo y lo filtran las ACL. IPs asignadas: backend 100.76.214.94, datos 100.94.121.117.
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
5. Comprobaciones (modo HTTP): `curl http://biohub-backend:8180/realms/biohub/.well-known/openid-configuration` (modo HTTPS: `https://biohub-backend.tailcfd75e.ts.net:8443/realms/biohub/...`); `docker compose ps` en cada VM; panel RabbitMQ en `http://biohub-backend:15672` (usuario `biohub`). Prueba completa con token y evento: ver `06-plan-pruebas.md` y `docs/guia-tailscale.md`.

**IP de origen en MySQL (resuelto).** Con el `docker-proxy` por defecto, MySQL veía las conexiones desde `172.18.0.1` y rechazaba a los usuarios `app@'100.76.214.94'`. Solución: en la VM datos Docker corre con `"userland-proxy": false` (`/etc/docker/daemon.json`, lo pone cloud-init vía `__USERLAND_PROXY__` en `deploy/vms/user-data.tpl`), así se publica solo con NAT y se conserva la IP real. En la VM backend se deja `true`. Si cambia la IP de Tailscale de backend, renombrar los usuarios: `RENAME USER 'u'@'ip_vieja' TO 'u'@'ip_nueva';` por cada usuario de aplicación (los scripts init solo corren con el volumen vacío).

### 4.1 HTTPS con `tailscale serve` (retira D13)

**Estado (2026-10-26):** los certificados HTTPS de la tailnet ya están activos (`CertDomains` contiene `biohub-backend.tailcfd75e.ts.net`) y el flujo HTTPS se probó de punta a punta desde la VM, pero el despliegue quedó en **modo HTTP provisional** porque la ACL vigente no abre 443/8443: al pegar la política nueva se activa con el comando de abajo.

**Dos modos**, elegidos en cada `desplegar.sh backend` con `BIOHUB_HTTPS` (`keep` por defecto = el modo del último despliegue; HTTP si es el primero):

| | HTTP provisional (`BIOHUB_HTTPS=0`) | HTTPS (`BIOHUB_HTTPS=1`) |
|---|---|---|
| Gateway | `http://biohub-backend:8080` | `https://biohub-backend.tailcfd75e.ts.net` (443) |
| Keycloak | `http://biohub-backend:8180` | `https://biohub-backend.tailcfd75e.ts.net:8443` |
| `iss` del token = `KEYCLOAK_ISSUER_URI` | `http://biohub-backend:8180/realms/biohub` | `https://biohub-backend.tailcfd75e.ts.net:8443/realms/biohub` |
| 8080/8180 publicados en | IP Tailscale | solo `127.0.0.1` (los lee `tailscale serve`) |
| Keycloak `sslRequired` (master y biohub) | `NONE` (D13, temporal) | `external` (D13 retirada) |

**Cómo se activa (una vez):**
1. La dueña activa *HTTPS Certificates* en https://login.tailscale.com/admin/dns (sin esto `CertDomains` está vacío y `desplegar.sh` con `BIOHUB_HTTPS=1` se detiene sin tocar nada).
2. La dueña pega la política `deploy/tailscale/policy.hujson` (abre 443 y 8443). **Antes de activar HTTPS**: si no, nadie del equipo llega al backend.
3. `BIOHUB_HTTPS=1 deploy/desplegar.sh backend --sin-build` (o sin `--sin-build` para reconstruir). Fija en la VM `TS_HTTPS=1`, `HTTP_BIND_IP=127.0.0.1`, `KC_PUBLIC_URL` y `KEYCLOAK_ISSUER_URI`, recrea los contenedores, pone `sslRequired=external` y ejecuta `deploy/vms/tailscale-https.sh aplicar` (que pide el certificado y hace `tailscale serve --bg --https=443 http://127.0.0.1:8080` y `--https=8443 http://127.0.0.1:8180`). `--bg` guarda la configuración en el estado de `tailscaled`: sobrevive a reinicios de la VM.

**Rollback a HTTP:** `BIOHUB_HTTPS=0 deploy/desplegar.sh backend --sin-build` (quita serve, vuelve a la IP Tailscale y `sslRequired=NONE`). El `iss` cambia, así que los tokens emitidos en el otro modo dejan de valer (duran 5 min).

**Issuer y claves (JWKS).** El `iss` que emite Keycloak (`KC_HOSTNAME` = `KC_PUBLIC_URL`) es idéntico al que validan gateway y servicios (`KEYCLOAK_ISSUER_URI`). Para no depender del nombre público ni de TLS dentro de Docker, los contenedores descargan las claves de `http://keycloak:8180/realms/biohub/protocol/openid-connect/certs` mediante `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI` (variable de entorno del compose; no se tocó el código ni el `application.yml`). Con `jwk-set-uri` Spring no hace *discovery* y sigue validando `iss` y `aud`. Keycloak corre con `KC_PROXY_HEADERS=xforwarded` para saber que el cliente llegó por HTTPS (verificado desde la VM el 2026-10-26: token con `iss` HTTPS, ping 200 y evento 202 por `tailscale serve` con `sslRequired=external`).

**HTTP plano 8080/8180 en la IP Tailscale:** se mantiene SOLO en modo HTTP provisional (sin certificados no hay otra forma de usar el backend) y **se retira en modo HTTPS** (pasan a 127.0.0.1): con `sslRequired=external` Keycloak ya rechaza el token por HTTP desde 100.x, y dejar el gateway en claro permitiría usar tokens sin TLS de aplicación y sin necesidad. El panel de RabbitMQ (15672) sigue en HTTP en la IP Tailscale (WireGuard lo cifra; pendiente ponerlo detrás de serve si se quiere HTTPS también ahí).

**Fuerza bruta en `master`:** `desplegar.sh` activa `bruteForceProtected` (5 fallos, espera hasta 15 min) en el realm `master` (SEC-D-02).

### 4.2 Acceso del equipo de mínimo privilegio (SEC-D-01)

En **ambas VMs**:
- Usuario Linux `equipo`: sin contraseña, **sin sudo, sin grupo `docker`**, y `/home/biohub` es `750` (no ve ~biohub ni los `.env`, que son `600`). Entra solo por Tailscale SSH (`ssh equipo@biohub-backend`, `ssh equipo@biohub-datos`) si la política ACL se lo permite.
- Única elevación: `sudo biohub-estado ps` y `sudo biohub-estado logs <servicio>` (últimas 200 líneas, sin seguir). `/usr/local/bin/biohub-estado` (root:root 755) ejecuta `docker compose` de solo lectura sobre el stack de la VM con la ruta fija; `/etc/sudoers.d/biohub-equipo` autoriza **cada comando con ruta y argumentos exactos** (una línea por servicio de la lista blanca; ni comodines ni `exec`, `run`, `up`, `cp`, `inspect`). Lista blanca: backend `api-gateway servicio-cientifico servicio-equipos servicio-consulta-publica servicio-reportes keycloak rabbitmq redis`; datos `mysql-transaccional mysql-lectura mysql-analitica mysql-gateway`.
- Lo instala `desplegar.sh` en cada despliegue (`deploy/vms/equipo/instalar-equipo.sh`, idempotente). Para repetirlo a mano: `tar -C deploy/vms/equipo -c . | ssh ... 'd=$(mktemp -d) && tar -x -C $d && sudo bash $d/instalar-equipo.sh $d'`.
- Aviso: los logs de los servicios pueden contener datos de las peticiones; no se publican contraseñas, pero conviene no loguear secretos.

**MySQL de solo lectura.** Usuario `lectura_equipo` (`SELECT` + `SHOW VIEW` solo en los esquemas de aplicación: transaccional `cientifico`, `equipos`; lectura `consulta_publica`; analítica `reportes`; gateway `gateway`; nunca `mysql`/`sys`, sin `PROCESS`/`FILE`/`GRANT`, máximo 5 conexiones) en las 4 instancias, con host `'100.64.0.0/255.192.0.0'` (= `100.64.0.0/10`, solo la tailnet). Contraseña `MYSQL_LECTURA_EQUIPO_PASSWORD` en `deploy/datos/.env` (la crea `generar-env.sh`). La ACL solo permite 3307-3310 a `group:biohub-admin`; pedir la contraseña a Juanita por un canal privado. Instalaciones nuevas: `deploy/datos/init/02-lectura-equipo.sh`. Instancias existentes: `deploy/datos/aplicar-lectura-equipo.sh` (idempotente; también rota la contraseña si cambió en `.env`; lo ejecuta `desplegar.sh datos`). Uso: `mysql -h biohub-datos -P 3307 -ulectura_equipo -p`.

**`generar-env.sh` es idempotente:** si los `.env` existen, no cambia ninguna variable ni regenera contraseñas; solo añade las variables nuevas del `.env.example` (con contraseña aleatoria si lo es). `--forzar` regenera todo (cambia todas las contraseñas).

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
| `CORS_ORIGENES` | backend | orígenes permitidos por el gateway (sin cambios con HTTPS: las webs aún no existen) |
| `TS_HTTPS`, `TS_FQDN`, `HTTP_BIND_IP`, `KC_PUBLIC_URL`, `KEYCLOAK_ISSUER_URI` | backend | modo HTTP/HTTPS; los fija `desplegar.sh` (no editar a mano; ver 4.1) |
| `MYSQL_LECTURA_EQUIPO_USER/PASSWORD/HOST` | datos | usuario MySQL de solo lectura (`lectura_equipo`, host `100.64.0.0/255.192.0.0`) |
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

- Estado: `ssh -i ~/.ssh/biohub_vms biohub@<ip> 'cd ~/biohub-backend && docker compose ps'` (o `~/biohub-datos`). El equipo: `ssh equipo@biohub-backend 'sudo biohub-estado ps'`.
- Logs: `docker compose logs -f api-gateway`. El equipo: `sudo biohub-estado logs api-gateway`.
- **Backups (VM datos):** `~/biohub-datos/backup/backup.sh` genera `$BACKUP_DIR/<instancia>/<instancia>-AAAAMMDD-HHMMSS.sql.gz` (permisos 600) y borra los de más de `BACKUP_RETENCION_DIAS` (14). Programación sugerida: timer systemd (`backup/biohub-backup.timer`, diario 02:30) o cron `30 2 * * *`; instrucciones y restauración en `deploy/datos/backup/README.txt`. Conviene copiar los backups fuera de la VM.
- **Rollback de HTTPS:** `BIOHUB_HTTPS=0 deploy/desplegar.sh backend --sin-build` (sección 4.1). **Rollback de la ACL:** historial en la consola de Tailscale.
- **Rollback del backend:** cada despliegue deja imágenes con etiqueta de fecha-hora (`docker images 'biohub/*'`). En la VM: editar `BIOHUB_TAG=<etiqueta anterior>` en `~/biohub-backend/.env` y ejecutar `docker compose up -d`. Las migraciones Flyway ya aplicadas no se revierten solas: si una migración fue destructiva, restaurar el backup de MySQL.
- Datos: los volúmenes con nombre sobreviven a `docker compose down`; `down -v` los borra (no usarlo en producción).

## 9. Limitaciones conocidas

- Keycloak en `start-dev` con H2 y usuarios de prueba: no es producción (alcance acordado).
- Modo HTTP provisional: `KC_HOSTNAME=http://biohub-backend:8180`, los clientes resuelven `biohub-backend` por MagicDNS y los contenedores usan el alias de red `biohub-backend`. En modo HTTPS los contenedores solo usan `keycloak:8180` para el JWKS. Se descartó `extra_hosts` hacia la IP de Tailscale porque ufw bloquea el hairpin contenedor -> IP del propio host.
- D13 (Keycloak sin exigir HTTPS) solo aplica en modo HTTP provisional. En modo HTTPS: `sslRequired=external`. Funnel sigue sin usarse (nada fuera de la tailnet).
- `tailscale serve` y las ACL: 443/8443 solo existen tras pegar `deploy/tailscale/policy.hujson`. Si se activa HTTPS antes, el equipo queda sin acceso (rollback: `BIOHUB_HTTPS=0`).
- La app solo está en línea cuando el portátil anfitrión y las dos VMs están encendidos.
- Las webs (Vercel) no forman parte de esta iteración; `CORS_ORIGENES` solo incluye los orígenes locales.
