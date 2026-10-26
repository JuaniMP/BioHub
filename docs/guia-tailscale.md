# Guía de Tailscale y del despliegue en las VMs

Para quién: los 4 integrantes del equipo. Después de leerla podrás conectarte al backend desplegado, probarlo, ver los logs y consultar la base de datos.

Detalles técnicos del despliegue: [docs/equipo/09-despliegue.md](equipo/09-despliegue.md).

## 1. Qué es Tailscale y por qué lo usamos

Tailscale crea una red privada y cifrada (la "tailnet") entre tus dispositivos y las máquinas virtuales (VMs) de BioHub. Así el backend no se publica en internet ni hay que abrir puertos en el router. Solo quien está en la tailnet puede entrar.

La tailnet es la de la cuenta de Juanita (JuaniMP). Su dominio MagicDNS es `tailcfd75e.ts.net`, por eso los nombres `biohub-backend` y `biohub-datos` funcionan desde tu equipo.

## 2. Instalar y entrar a la tailnet

Por ahora solo Juanita está en la tailnet. Cada compañero entra con el enlace o correo de invitación que le manda Juanita (consola > Users > Invite users); luego instala Tailscale así:

### Windows
1. Descarga el instalador desde https://tailscale.com/download/windows e instálalo.
2. Abre Tailscale (ícono junto al reloj) e inicia sesión con la cuenta con la que aceptaste la invitación.

### macOS
1. Instala la app desde https://tailscale.com/download/mac (o la Mac App Store).
2. Ábrela e inicia sesión con la cuenta con la que aceptaste la invitación.

### Linux
```bash
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up
```
`tailscale up` muestra un enlace: ábrelo en el navegador e inicia sesión.

### Aceptar la invitación
Si recibiste el correo de invitación y no lo has aceptado, ábrelo y acepta. Después inicia sesión en Tailscale con esa misma cuenta.

### Comprobar que funciona
```bash
tailscale status
ping biohub-backend
ping biohub-datos
```
- En `tailscale status` debes ver `biohub-backend` y `biohub-datos`.
- Si el `ping` responde, ya estás dentro. En Windows puedes usar PowerShell; `ping` es igual.

## 3. URLs y puertos

| VM | IP Tailscale | Qué | URL / puerto |
|---|---|---|---|
| biohub-backend | 100.76.214.94 | Gateway (API) | **HTTPS (cuando esté activado):** https://biohub-backend.tailcfd75e.ts.net · **HTTP provisional:** http://biohub-backend:8080 |
| biohub-backend | 100.76.214.94 | Keycloak | **HTTPS:** https://biohub-backend.tailcfd75e.ts.net:8443 (consola: `/admin`) · **HTTP provisional:** http://biohub-backend:8180 |
| biohub-backend | 100.76.214.94 | Panel de RabbitMQ | http://biohub-backend:15672 (usuario `biohub`) |
| biohub-backend | 100.76.214.94 | SSH | puerto 22 |
| biohub-datos | 100.94.121.117 | MySQL transaccional (esquemas `cientifico`, `equipos`) | 3307 (solo la admin, ver sección 6) |
| biohub-datos | 100.94.121.117 | MySQL lectura (`consulta_publica`) | 3308 |
| biohub-datos | 100.94.121.117 | MySQL analítica (`reportes`) | 3309 |
| biohub-datos | 100.94.121.117 | MySQL gateway (`gateway`) | 3310 |
| biohub-datos | 100.94.121.117 | SSH | puerto 22 |

Mientras no esté activado HTTPS se usan las URL `http://biohub-backend:8080/8180`; Juanita te avisa cuando cambie. En los ejemplos de abajo, cambia `http://biohub-backend:8180` por `https://biohub-backend.tailcfd75e.ts.net:8443` y `http://biohub-backend:8080` por `https://biohub-backend.tailcfd75e.ts.net` (los puertos 8080/8180 dejan de estar disponibles en modo HTTPS).

Los servicios internos (8081-8084), Redis y el puerto AMQP de RabbitMQ (5672) no son accesibles desde fuera: solo se entra por el gateway.

### Contraseñas
Las contraseñas (usuarios de prueba de Keycloak, consola de Keycloak y panel de RabbitMQ) están en `deploy/backend/.env` de la máquina de Juanita. Pídelas por un canal privado. **Nunca** las pongas en el repositorio ni en el chat del grupo.

### Disponibilidad
Las VMs corren en el portátil de Juanita (KVM). La app solo está en línea cuando ese equipo y las VMs están encendidos (las VMs arrancan solas con el equipo).

## 4. Probar el backend con curl

Necesitas `curl` y `jq`. Reemplaza `LA_CONTRASENA` por la contraseña de prueba que te dio Juanita.

### 4.1 Pedir un token
El cliente `biohub-pruebas` es solo para pruebas. El token dura 5 minutos.

```bash
TOKEN=$(curl -s -X POST "http://biohub-backend:8180/realms/biohub/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=biohub-pruebas \
  -d username=investigador1 --data-urlencode "password=LA_CONTRASENA" \
  | jq -r .access_token)
echo "${TOKEN:0:20}..."
```
Si imprime `null...`, la contraseña o el usuario están mal (ver sección 8).

Usuarios de prueba: `investigador1`, `auditor1`, `cientifico1`, `analista1`, `admin1`.

### 4.2 Llamar al servicio científico
```bash
curl -s -H "Authorization: Bearer $TOKEN" http://biohub-backend:8080/api/cientifico/ping
```
Respuesta esperada (los valores cambian):
```json
{ "servicio": "servicio-cientifico", "usuario": "investigador1",
  "roles": ["investigador_campo"], "fecha": "2026-10-22T04:33:23.726Z" }
```

### 4.3 Probar el evento de RabbitMQ
Este evento solo lo puede publicar el rol `administrador`, así que pide el token con `admin1`:

```bash
TOKEN=$(curl -s -X POST "http://biohub-backend:8180/realms/biohub/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=biohub-pruebas \
  -d username=admin1 --data-urlencode "password=LA_CONTRASENA" \
  | jq -r .access_token)

# Publicar el evento (responde 202 con el evento)
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"mensaje":"hola desde mi portátil"}' \
  http://biohub-backend:8080/api/cientifico/eventos/prueba

# Ver que lo recibieron los dos consumidores
curl -s -H "Authorization: Bearer $TOKEN" http://biohub-backend:8080/api/publica/eventos/recibidos
curl -s -H "Authorization: Bearer $TOKEN" http://biohub-backend:8080/api/reportes/eventos/recibidos
```
Cada consulta debe listar tu evento (`"tipo": "prueba.ping"`). Si solo falla una, el consumidor de ese servicio tiene un problema.

### 4.4 Verlo en el panel de RabbitMQ
1. Abre http://biohub-backend:15672 e inicia sesión con el usuario `biohub` y su contraseña (te la da Juanita).
2. Pestaña **Exchanges**: busca `biohub.eventos` (tipo topic).
3. Pestaña **Queues**: busca `consulta-publica.eventos` y `reportes.eventos`. Sus colas de error son `*.dlq`. Una `.dlq` con mensajes significa que un evento fue rechazado.

## 5. Entrar por SSH a las VMs

Con Tailscale SSH no necesitas llave: Tailscale te autoriza (y cada 12 horas te pide reconfirmar en el navegador).

```bash
ssh equipo@biohub-backend
ssh equipo@biohub-datos
```
El usuario `equipo` **no tiene sudo, no usa Docker y no ve las contraseñas** (`.env`): es a propósito. Solo puede mirar el estado y los logs con un comando acotado. El usuario `biohub` (administrador) es solo de Juanita.

> Juanita además puede entrar con su llave: `ssh -i ~/.ssh/biohub_vms biohub@<IP NAT de la VM>` (red local del portátil), o `ssh biohub@biohub-backend` por Tailscale.

### Comandos permitidos al equipo
```bash
sudo biohub-estado ps                       # estado de los contenedores (deben estar healthy)
sudo biohub-estado logs api-gateway         # últimas 200 líneas de log de un servicio
sudo biohub-estado logs servicio-cientifico
```
Servicios de `biohub-backend`: `api-gateway`, `servicio-cientifico`, `servicio-equipos`, `servicio-consulta-publica`, `servicio-reportes`, `redis`, `rabbitmq`, `keycloak`.
Servicios de `biohub-datos`: `mysql-transaccional`, `mysql-lectura`, `mysql-analitica`, `mysql-gateway`.

Para ejecutar un solo comando sin quedarte dentro:
```bash
ssh equipo@biohub-backend 'sudo biohub-estado ps'
```
Reiniciar servicios, ver logs en vivo (`-f`) o entrar a un contenedor lo hace Juanita.

### Rutas en las VMs (solo administración)
| VM | Carpeta del stack |
|---|---|
| biohub-backend | `~biohub/biohub-backend` |
| biohub-datos | `~biohub/biohub-datos` |

**No uses** `docker compose down -v` (solo Juanita administra los stacks): borra los volúmenes (los datos).

## 6. Consultar MySQL

Los usuarios de aplicación (`cientifico_app`, etc.) solo aceptan conexiones desde la VM backend, a propósito. Para consultar datos existe un usuario **de solo lectura**, `lectura_equipo`:

- Permisos: `SELECT` y `SHOW VIEW` únicamente sobre los esquemas de la aplicación; no ve `mysql`/`sys` ni puede escribir. Solo acepta conexiones desde la tailnet (100.64.0.0/10).
- La contraseña está en `deploy/datos/.env` de la máquina de Juanita (`MYSQL_LECTURA_EQUIPO_PASSWORD`); pídela por un canal privado.
- **Acceso por red:** la política de Tailscale (ACL) abre los puertos 3307-3310 solo a Juanita. Si el equipo debe consultar directamente, Juanita puede habilitar la regla opcional comentada en `deploy/tailscale/policy.hujson`. Mientras tanto, pídele a Juanita las consultas.

```bash
mysql -h biohub-datos -P 3307 -ulectura_equipo -p     # 3307 transaccional, 3308 lectura, 3309 analítica, 3310 gateway
```

| Instancia | Puerto | Esquema(s) |
|---|---|---|
| transaccional | 3307 | `cientifico`, `equipos` |
| lectura | 3308 | `consulta_publica` |
| analítica | 3309 | `reportes` |
| gateway | 3310 | `gateway` |

```sql
SHOW DATABASES;
USE cientifico;
SHOW TABLES;
```
No modifiques datos a mano: los cambios se hacen con migraciones Flyway en el código. Con root (solo Juanita): `docker exec -it biohub-mysql-transaccional mysql -uroot -p` dentro de la VM de datos.

## 7. Cómo se redespliega

Solo lo hace Juanita (o quien tenga la máquina anfitriona con KVM, las VMs y los archivos `.env`). Desde la raíz del repo:

```bash
deploy/desplegar.sh backend     # solo el backend
deploy/desplegar.sh datos       # solo los MySQL
deploy/desplegar.sh todo        # datos primero, luego backend
deploy/desplegar.sh backend --sin-build   # reutiliza las imágenes ya construidas
```
Antes de desplegar, corre las pruebas (`mvn -B verify`): el script compila con `-DskipTests`.

Si algo sale mal, el rollback está descrito en [09-despliegue.md](equipo/09-despliegue.md) (sección 8).

Para activar HTTPS: pegar antes la política de `deploy/tailscale/` y luego `BIOHUB_HTTPS=1 deploy/desplegar.sh backend --sin-build` (detalles en 09-despliegue.md, sección 4.1). Para volver a HTTP: `BIOHUB_HTTPS=0`.

## 8. Problemas frecuentes

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| `biohub-backend` no resuelve ("could not resolve host", "no se conoce el host") | Tailscale apagado o sin MagicDNS | Revisa que Tailscale esté conectado (`tailscale status`). En Linux: `sudo tailscale up`. Activa "Use Tailscale DNS settings" en la app. Como alternativa, usa la IP: `100.76.214.94` (pero Keycloak necesita el nombre `biohub-backend`; ver nota abajo) |
| Timeout o "connection timed out" | Portátil de Juanita o las VMs apagadas/en arranque; o tú desconectado de Tailscale | Pregunta a Juanita si el equipo y las VMs están encendidos. Espera unos minutos tras un arranque. Prueba `ping biohub-backend` |
| `401 Unauthorized` | Token vencido (dura 5 min), ausente o con mala contraseña | Pide un token nuevo (sección 4.1) |
| `403 Forbidden` | Tu usuario no tiene el rol | El evento de prueba exige `admin1` |
| Keycloak responde "HTTPS required" | Usas la URL HTTP cuando el backend ya está en modo HTTPS | Usa `https://biohub-backend.tailcfd75e.ts.net:8443` |
| Timeout en `https://biohub-backend...` | La política ACL nueva aún no está pegada, o tu correo no está en `group:biohub-equipo` | Avisa a Juanita (README de `deploy/tailscale/`) |
| `Access denied` al conectar a MySQL con un usuario de aplicación | Es lo esperado | Usa `lectura_equipo` (sección 6) |
| `ssh` pide contraseña o llave | Tailscale SSH no se autorizó | Usa `ssh equipo@biohub-backend` (usuario `equipo`) y verifica que estás en la tailnet y en el grupo del equipo |
| `sudo: a password is required` | Intentaste un comando fuera de `biohub-estado ps/logs` | Es lo esperado; solo están permitidos esos |

Nota sobre el nombre: el token lleva como emisor la URL pública de Keycloak (`https://biohub-backend.tailcfd75e.ts.net:8443/realms/biohub` en modo HTTPS; `http://biohub-backend:8180/realms/biohub` en modo HTTP). Los servicios solo aceptan ese emisor: pide el token siempre con el nombre (no con la IP).

### HTTPS y la decisión D13
En modo HTTPS (`tailscale serve`, certificados de Tailscale) Keycloak exige HTTPS (`sslRequired=external`) y D13 deja de aplicar. Solo mientras el despliegue esté en el modo HTTP provisional, Keycloak va con `sslRequired=NONE` (la tailnet ya cifra con WireGuard); por eso **no** debes exponer esos puertos fuera de Tailscale. Funnel no se usa. Vercel está pendiente.

### Qué NO hacer
- No compartas contraseñas por el repo ni por el chat del grupo.
- No publiques puertos de las VMs fuera de Tailscale (ni en `0.0.0.0`, ni en el router).
- No uses Tailscale Funnel sin acordarlo con el equipo.
- No uses `docker compose down -v` en las VMs.
- No subas ningún `.env` a git.
- Recuerda que Keycloak está en modo desarrollo (H2, usuarios de prueba): no pongas datos reales.
