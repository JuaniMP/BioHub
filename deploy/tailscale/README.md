# Politica de acceso de Tailscale (ACL) - BioHub

`policy.hujson` es la politica de la tailnet `tailcfd75e.ts.net`, versionada aqui (SEC-D-04). **No se aplica sola**:
no hay API key de Tailscale, asi que la dueña (Juanita, `JuaniMP@github`) la pega en la consola.

## Que cambia respecto a la politica actual

| Antes | Ahora |
|---|---|
| `autogroup:member` (cualquiera de la tailnet) | grupos con nombre: `group:biohub-admin` (Juanita) y `group:biohub-equipo` |
| miembros -> `tag:backend:22,8080,8180,15672` y `tag:datos:22,3307-3310` | equipo -> `tag:backend:22,443,8443,8080,8180,15672` y `tag:datos:22`; **MySQL 3307-3310 solo `group:biohub-admin`** |
| SSH de todos los miembros como `biohub` (sudo + docker) | SSH en modo `check` (12 h): el equipo entra como `equipo` (sin sudo ni docker); solo la admin como `biohub` |
| (posible) miembro -> miembro `*` | no hay reglas hacia los portatiles: nadie del equipo llega al anfitrion de Juanita |
| `tag:backend -> tag:datos:3307-3310` | igual |
| sin pruebas | secciones `tests` y `sshTests`: la consola rechaza la politica si no se cumplen |

## Orden recomendado (para no perder el acceso a nada)

1. **Activar los certificados HTTPS** (una sola vez): https://login.tailscale.com/admin/dns -> seccion *HTTPS Certificates* -> *Enable HTTPS*. (Publica los nombres `*.tailcfd75e.ts.net` de las maquinas en el registro publico Certificate Transparency; es normal.) Ya esta hecho si `tailscale status --json | jq .CertDomains` en la VM no esta vacio.
2. **Completar los correos** de los compañeros en `policy.hujson` (siguiente seccion).
3. **Pegar la politica**: https://login.tailscale.com/admin/acls/file -> reemplazar TODO el contenido por `policy.hujson` -> *Save*. La consola ejecuta `tests` y `sshTests` al guardar: si algo falla, **no guarda** y te dice cual (entonces no pierdes nada). Antes de pegar, copia la politica vigente a un archivo por si quieres volver.
4. **Comprobar que sigues dentro** desde tu portatil: `tailscale ping biohub-backend`, `curl http://biohub-backend:8080/actuator/health` (modo HTTP) y `ssh biohub@biohub-backend` (Tailscale SSH, pedira confirmar en el navegador por el modo `check`). Tienes ademas la via de emergencia por la red NAT: `ssh -i ~/.ssh/biohub_vms biohub@<IP NAT>`.
5. **Activar HTTPS en el backend** (requiere que el paso 3 ya este hecho, porque 443 y 8443 solo existen en la politica nueva):
   ```bash
   BIOHUB_HTTPS=1 deploy/desplegar.sh backend --sin-build
   ```
6. Cuando HTTPS funcione para todos, **borrar `8080,8180` de la regla del equipo** en `policy.hujson` (comentada en el archivo) y volver a pegarla.

## Completar los correos de los compañeros

En `policy.hujson`, grupo `group:biohub-equipo`, reemplaza los tres marcadores:

```
"COMPANERO_1-PENDIENTE@invalido.example",  ->  "ana@gmail.com",
```
- Usa la identidad **exacta** con la que cada persona inicia sesion en Tailscale: aparece en la columna *User* de https://login.tailscale.com/admin/machines (para cuentas de GitHub sera `usuario@github`; para Google, el correo).
- Mientras un marcador siga sin cambiar, esa persona no tiene acceso a nada (los marcadores usan el dominio reservado `.example`). Quien no este en el grupo no llega a las VMs.
- Cuando esten los 3 correos reales, descomenta las pruebas marcadas "Descomentar tras completar los correos" en `tests` y `sshTests` (poniendo uno de los correos reales) y guarda otra vez.
- Si el correo se escribe mal, la persona simplemente no tendra acceso: la politica sigue siendo valida (no te deja fuera a ti).

## Notas

- `JuaniMP@github` esta en los dos grupos. Si tu identidad en la consola se ve distinta (columna *User* de tu portatil), corrigela en ambos grupos **antes** de guardar; los `tests` de la admin fallaran y la consola no guardara si hay un error.
- La regla opcional (comentada) que da MySQL de solo lectura a todo el equipo queda desactivada a proposito.
- Funnel no esta permitido (no hay `nodeAttrs` de funnel): nada se publica en Internet.
- Los tags `tag:backend` y `tag:datos` ya estan asignados a las VMs; cambiar `tagOwners` no los quita.
- Rollback: en la consola, *Edit file* -> pestaña *History* (o pegar de nuevo la politica anterior guardada).
