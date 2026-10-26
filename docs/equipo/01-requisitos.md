# 01 – Requisitos: parte de Juanita Mejía (ubicación, consulta pública y web-publica)

Fuente oficial: `docs/equipo/fuentes/srs-biohub.docx` (copia en `srs-biohub.md`). Arquitectura: `03-arquitectura.md` (no se cambia).
Rama: `feature/ubicacion-consulta-publica`. Fecha: 2026-10-26. Estado: borrador para validación de Juanita.

Convenciones: los RF/RNF/ASR/HU se citan con la numeración del SRS. Las historias que parto llevan sufijo (HU-06.1, HU-07.2…). Los umbrales marcados como "(supuesto)" no están en el SRS y se explican en la sección 7.

---

## 1. Alcance de Juanita

### 1.1 Visión de su parte
BioHub debe divulgar el inventario de especies sin entregar la ubicación exacta de las especies en peligro. Juanita construye las dos mitades de esa promesa: (a) el módulo **ubicación**, que guarda cifradas esas coordenadas y solo las entrega al personal científico de alto rango; y (b) la **consulta pública**, un modelo de lectura alimentado por eventos y con caché, que responde en menos de 2 s aunque el tráfico se multiplique por 60 y que nunca recibe esas coordenadas.

### 1.2 Dentro del alcance
| Componente | Qué construye Juanita |
|---|---|
| `servicio-cientifico`, módulo `ubicacion` | Cifrado AES-256 de coordenadas de especies en peligro; gestión de llaves versionadas (fuera de la BD); servicio público de módulo que recibe la ubicación desde `registro`; índice de búsqueda interno; endpoint de mapa con coordenadas exactas; endpoint de búsqueda y filtrado interno |
| `servicio-consulta-publica` | Consumidor de `hallazgo.registrado` y `hallazgo.reclasificado`; modelo de lectura en `mysql-lectura`; caché Redis; endpoints `GET /api/publica/**` de búsqueda y ficha; modo degradado |
| `web-publica` | Buscador, resultados, ficha de especie con ubicación protegida y mensaje que explica por qué no hay coordenadas |
| UI interna de mapa y búsqueda (ver pregunta P3) | Pantallas de mapa (HU-07) y búsqueda (HU-13), entregadas como carpeta autocontenida; la navegación y el login siguen siendo de Santiago |
| Pruebas | Unitarias, de integración (eventos simulados) y de carga x60 de su parte |

### 1.3 Fuera del alcance (no lo hace Juanita)
| Qué | De quién | Relación con Juanita |
|---|---|---|
| Registro de hallazgos, fichas taxonómicas, historial, reclasificación (HU-01, 02, 03, 05) y decidir quién marca "En Peligro" | Hernando | Contrato (sección 4.1) |
| Publicar los eventos `hallazgo.*` | Hernando | Juanita solo los consume |
| Gateway: API Keys, rate limit, 401/429 de la API externa (HU-08, HU-12, RF14 en su parte de control; RNF11, RNF12, ASR8, ASR9) | Santiago | Límite en 1.4 |
| Keycloak, roles, usuarios, login de web-interna, navegación (HU-10, HU-11, RF17–RF19) | Santiago | Contrato (sección 4.2) |
| Infraestructura compartida, `.env` de despliegue, RabbitMQ, usuarios MQ | Santiago / devops | Pide variables (4.2) |
| Equipos (HU-04) y reportes (HU-09) | Nicolás | Solo comparten el evento (4.3) |

### 1.4 Límite sobre HU-08 / RF14 (API pública para externos)
- **Santiago (gateway):** emitir/revocar/validar API Keys, 401 sin key válida, rate limit de 60/min configurable, 429, URLs estables (RNF11, RNF12, ASR8, ASR9, HU-08 criterios 1–3, HU-12).
- **Juanita (consulta pública):** los **datos y recursos** que hay detrás de `/api/publica/**`: contenido, formato JSON estable, paginación, orden, y la garantía de que **nunca** incluyen coordenadas exactas de especies en peligro. Un consumidor externo ve exactamente lo mismo que el portal.
- Juanita no implementa autenticación por API Key ni limitación de peticiones; no ofrece endpoints "solo para externos" distintos de los públicos salvo que Santiago lo pida (ver P2).

### 1.5 Trazabilidad HU ↔ RF/RNF/ASR ↔ componente
| HU (SRS / refinada) | RF | RNF / ASR | Componente | Prioridad |
|---|---|---|---|---|
| HU-UBI-01 Cifrado de coordenadas (técnica, deriva de HU-07 y RNF9) | RF11, RF12 | RNF9, ASR6 | cientifico/ubicacion | Must |
| HU-UBI-02 Gestión y rotación de llaves (técnica) | – | RNF9, ASR6 | cientifico/ubicacion | Must (rotación: Should) |
| HU-UBI-03 Recepción de ubicación desde registro (técnica) | RF11 | RNF9 | cientifico/ubicacion | Must |
| HU-06.1 Modelo de lectura por eventos | RF10, RF11 | RNF1, RNF5, ASR1, ASR4 | consulta-publica | Must |
| HU-06.2 Buscar especies | RF10 | RNF5, RNF13 | consulta-publica, web-publica | Must |
| HU-06.3 Ficha con ubicación protegida | RF10, RF11 | RNF7 | consulta-publica, web-publica | Must |
| HU-06.4 Rendimiento, caché y resiliencia | RF10 | RNF1, RNF5, ASR1, ASR4 | consulta-publica | Must (modo degradado: Should) |
| HU-06.5 Reclasificación en el portal | RF11 | RNF9 | consulta-publica | Must |
| HU-07.1 Mapa: endpoint con permisos | RF12, RF13 | RNF9, ASR6 | cientifico/ubicacion | Must |
| HU-07.2 Mapa: pantalla | RF12 | RNF13 | UI interna | Should |
| HU-07.3 Registro de accesos a coordenadas | RF13 | – | cientifico/ubicacion | Could |
| HU-13.1 Búsqueda interna con filtros | RF12, RF13 | – | cientifico/ubicacion | Must |
| HU-13.2 Rendimiento de la búsqueda | – | (criterio HU-13: < 3 s) | cientifico/ubicacion | Must |
| HU-13.3 Pantalla de búsqueda | – | RNF13 | UI interna | Should |
| HU-08.J Contrato de datos de `/api/publica/**` | RF14 (datos) | RNF11/12 (Santiago) | consulta-publica | Should |

---

## 2. Roles que intervienen
| Rol | Qué puede hacer en la parte de Juanita |
|---|---|
| Visitante (sin sesión) | Buscar y ver fichas en el portal público; ve departamento/región, nunca coordenadas de especies en peligro |
| `investigador_campo`, `auditor_cientifico`, `analista_ambiental`, `administrador` | Buscar y filtrar hallazgos internamente; **no** ven coordenadas exactas de especies en peligro (el administrador tampoco: RF12 "única y exclusivamente") |
| `cientifico_alto_rango` | Lo anterior + mapa con coordenadas exactas + coordenadas exactas en los resultados de búsqueda |
| Consumidor externo | Mismos datos que el visitante, vía API con API Key (Santiago) |

---

## 3. Historias refinadas y criterios de aceptación

Convenciones de error: errores internos en `application/problem+json`; 400 parámetro inválido; 401 sin token/token inválido; 403 rol insuficiente; 404 recurso inexistente; 503 solo si una dependencia imprescindible falla.

### 3.1 Módulo ubicación

#### HU-UBI-01 – Cifrado de coordenadas de especies en peligro (RNF9, ASR6)
Como **sistema** quiero cifrar con AES-256-GCM las coordenadas de especies en peligro antes de guardarlas para que nunca existan en texto plano en la BD.

```gherkin
Escenario: Se cifra una ubicación de especie en peligro
  Dado un hallazgo con estado "EN_PELIGRO" y coordenadas (4.6097, -74.0817)
  Cuando el módulo ubicación guarda la ubicación
  Entonces la fila guarda las coordenadas solo en la columna cifrada (VARBINARY) con su versión de llave
  Y las columnas de latitud y longitud en claro quedan en NULL
  Y una consulta SQL directa por "4.6097" o "-74.0817" sobre el esquema cientifico no devuelve filas

Escenario: Cada cifrado usa un nonce distinto
  Dado dos hallazgos "EN_PELIGRO" con las mismas coordenadas
  Cuando se guardan
  Entonces los dos valores cifrados son distintos entre sí

Escenario: El texto cifrado queda ligado a su hallazgo
  Dado una ubicación cifrada del hallazgo A
  Cuando alguien copia su valor cifrado a la fila del hallazgo B e intenta leerla
  Entonces el descifrado falla por autenticación (AAD = id del hallazgo) y no devuelve coordenadas

Escenario: Texto cifrado alterado
  Dado una ubicación cifrada con un byte modificado
  Cuando se intenta descifrar
  Entonces se produce un error de integridad, no se devuelven coordenadas y se registra un error sin datos sensibles

Escenario: Especie no en peligro
  Dado un hallazgo con estado distinto de "EN_PELIGRO"
  Cuando se guarda la ubicación
  Entonces las coordenadas se guardan en las columnas en claro y la columna cifrada queda en NULL

Escenario: Coordenadas inválidas
  Dado una latitud 95 o una longitud -200 o un valor no numérico
  Cuando se intenta guardar la ubicación
  Entonces se rechaza con error de validación y no se guarda nada

Escenario: No hay fuga en logs
  Dado cualquier operación de guardado o lectura de una ubicación en peligro
  Cuando se revisan los logs de la aplicación
  Entonces no aparecen latitud, longitud, llaves ni texto cifrado
```

#### HU-UBI-02 – Gestión y rotación de llaves (ASR6)
Como **administrador del sistema** quiero que las llaves estén versionadas y fuera de la BD para poder rotarlas sin perder acceso a los datos antiguos.

```gherkin
Escenario: Las llaves no están en la BD ni en el repositorio
  Dado el servicio configurado con llaves por variables de entorno o archivo montado fuera del repo
  Cuando se inspeccionan el esquema cientifico, el repositorio git y `.env.example`
  Entonces no hay ninguna llave real; `.env.example` solo trae variables vacías o marcadas como ejemplo

Escenario: Cada registro indica con qué versión se cifró
  Dado la llave activa "v1"
  Cuando se guarda una ubicación en peligro
  Entonces la fila guarda clave_version = "v1"

Escenario: Rotación sin pérdida de datos
  Dado registros cifrados con "v1" y la llave activa cambia a "v2" con "v1" aún configurada como disponible
  Cuando se leen los registros antiguos y se guarda uno nuevo
  Entonces los antiguos se descifran con "v1" y el nuevo se cifra con "v2"

Escenario: Llave de una versión retirada
  Dado un registro con clave_version = "v1" y "v1" ya no está configurada
  Cuando se intenta descifrar
  Entonces se omite ese registro, se registra un error con el id del hallazgo (sin datos sensibles) y el servicio sigue respondiendo

Escenario: Falta la llave activa
  Dado el servicio arrancando con perfil de despliegue sin llave activa configurada
  Cuando se inicia
  Entonces el servicio no arranca y el mensaje indica la variable faltante (sin imprimir valores)

Escenario: Llave de longitud inválida
  Dado una llave que no decodifica a exactamente 32 bytes
  Cuando se inicia el servicio
  Entonces el servicio no arranca

Escenario (Should): Re-cifrado masivo
  Dado registros en "v1" y activa "v2"
  Cuando el administrador ejecuta el re-cifrado
  Entonces todos los registros quedan en "v2", el conteo coincide y las coordenadas descifradas son idénticas a las originales
```

#### HU-UBI-03 – Recepción de la ubicación desde registro (contrato interno)
Como **módulo registro** quiero entregar la ubicación de un hallazgo a ubicación para que sea cifrada y quede disponible para mapa y búsqueda.
Interfaz: una clase pública de `ubicacion.service` (nombre sugerido `UbicacionService`); `registro` no importa nada más de `ubicacion`.

```gherkin
Escenario: Alta de ubicación
  Dado una llamada con hallazgoId, versión, latitud, longitud, departamento, región, estado de conservación, nombre científico, fecha de recolección e investigador
  Cuando ubicación la procesa
  Entonces guarda la ubicación según HU-UBI-01 y deja el hallazgo disponible para HU-13 y HU-07

Escenario: Actualización por reclasificación a "EN_PELIGRO"
  Dado un hallazgo con coordenadas en claro
  Cuando registro informa estado "EN_PELIGRO"
  Entonces en la misma transacción las coordenadas pasan a la columna cifrada y las columnas en claro quedan en NULL

Escenario: Reclasificación a no amenazada
  Dado un hallazgo con coordenadas cifradas
  Cuando registro informa un estado distinto de "EN_PELIGRO"
  Entonces las coordenadas permanecen cifradas (no se pasan a claro)

Escenario: Versión antigua o repetida
  Dado un hallazgo con versión 3 guardada
  Cuando llega una llamada con versión 2 o 3
  Entonces se ignora sin error y no se modifica nada

Escenario: Datos incompletos
  Dado una llamada sin hallazgoId o sin estado de conservación
  Cuando se procesa
  Entonces lanza una excepción de validación y no guarda nada

Escenario: Hallazgo sin coordenadas
  Dado una llamada con departamento pero sin latitud/longitud
  Cuando se procesa
  Entonces se guarda sin coordenadas, el hallazgo aparece en búsqueda y no aparece en el mapa
```

### 3.2 Consulta pública (HU-06 refinada)

Recursos propuestos (ver contrato 4.1 y 4.4): `GET /api/publica/fichas?q=&familia=&departamento=&page=&size=` y `GET /api/publica/fichas/{id}`.

#### HU-06.1 – Modelo de lectura alimentado por eventos (RNF1, ASR1, ASR4)
Como **servicio de consulta pública** quiero construir mi propio modelo de lectura a partir de eventos para servir sin tocar la BD transaccional.

```gherkin
Escenario: Se crea una ficha pública con un evento válido
  Dado un evento "hallazgo.registrado" válido de un hallazgo no amenazado
  Cuando consulta-publica lo consume
  Entonces existe una ficha en mysql-lectura con ese hallazgoId y GET /api/publica/fichas/{id} responde 200

Escenario: Evento de especie en peligro
  Dado un evento "hallazgo.registrado" con estadoConservacion "EN_PELIGRO" y sin coordenadas
  Cuando se consume
  Entonces la ficha se guarda con departamento/región y sin ninguna columna de coordenadas con valor

Escenario: Evento de especie en peligro que trae coordenadas (defensa en profundidad)
  Dado un evento con estado "EN_PELIGRO" y datos con latitud/longitud/coordenadas
  Cuando se consume
  Entonces se rechaza sin reencolar (va a la DLQ), no se guarda nada y se registra una alerta sin los valores de las coordenadas

Escenario: Evento repetido (entrega al menos una vez)
  Dado un evento ya procesado
  Cuando llega otra vez con el mismo id
  Entonces no se duplica la ficha

Escenario: Evento fuera de orden
  Dado una ficha en versión 2
  Cuando llega un evento de versión 1 del mismo hallazgo
  Entonces se ignora y la ficha sigue en versión 2

Escenario: Evento mal formado
  Dado un evento con JSON roto, campos obligatorios faltantes o más de 64 KB
  Cuando se consume
  Entonces va a la DLQ sin reencolar y la cola principal sigue procesando

Escenario: Aislamiento de la BD transaccional
  Dado la configuración del servicio
  Cuando se revisa su datasource y los permisos del usuario de BD
  Entonces solo apunta a mysql-lectura y no tiene acceso al esquema cientifico ni llama a servicio-cientifico

Escenario: No se expone quién originó el evento
  Dado un evento con campo "usuario" (preferred_username)
  Cuando se consume
  Entonces ese valor no se guarda en la ficha ni aparece en ninguna respuesta pública
```

#### HU-06.2 – Buscar especies
Como **visitante** quiero buscar especies por nombre científico, familia o departamento para informarme de la biodiversidad.

```gherkin
Escenario: Búsqueda por nombre científico
  Dado fichas publicadas, una con nombre científico "Atelopus ebenoides"
  Cuando el visitante busca q="atelopus"
  Entonces recibe 200 con esa ficha (búsqueda sin distinguir mayúsculas ni tildes) y total >= 1

Escenario: Búsqueda por familia y departamento combinados
  Dado fichas de la familia "Bufonidae" en "Boyacá" y en "Cundinamarca"
  Cuando busca familia="Bufonidae" y departamento="Boyacá"
  Entonces solo recibe las fichas que cumplen ambos

Escenario: Sin resultados (especie no encontrada)
  Dado ninguna ficha que coincida
  Cuando el visitante busca q="zzzz"
  Entonces recibe 200 con lista vacía y total 0
  Y la web muestra "No se encontraron especies" con sugerencia de revisar la ortografía

Escenario: Sin filtros
  Dado fichas publicadas
  Cuando consulta sin parámetros
  Entonces recibe la primera página (20 por defecto) ordenada por nombre científico

Escenario: Paginación
  Dado 45 fichas
  Cuando pide page=2&size=20
  Entonces recibe 5 fichas y total 45

Escenario: Parámetros inválidos
  Dado size=500, page=-1 o q de más de 100 caracteres
  Cuando consulta
  Entonces recibe 400 con mensaje de validación

Escenario: Intento de inyección
  Dado q="' OR '1'='1"
  Cuando consulta
  Entonces recibe 200 con lista vacía (o solo coincidencias literales) y ningún error de SQL

Escenario: Sin sesión
  Dado un visitante sin token
  Cuando consulta GET /api/publica/fichas
  Entonces no se le pide login (no 401 por falta de JWT)
```

#### HU-06.3 – Ficha con ubicación protegida (RF11, RNF7)
Como **visitante** quiero ver la ficha de una especie y entender por qué no veo la ubicación exacta si está en peligro.

```gherkin
Escenario: Ficha de especie en peligro
  Dado una ficha "EN_PELIGRO" en Boyacá
  Cuando el visitante abre GET /api/publica/fichas/{id}
  Entonces recibe departamento y región, estadoConservacion "EN_PELIGRO" y "ubicacionProtegida": true
  Y el cuerpo no contiene las claves latitud, longitud ni coordenadas, ni los valores numéricos del hallazgo

Escenario: La web explica la restricción
  Dado la ficha anterior abierta en web-publica
  Cuando se muestra la sección de ubicación
  Entonces aparece el departamento y un texto como "La ubicación exacta de esta especie está protegida para evitar su caza ilegal" junto a un icono
  Y en prueba de usabilidad >= 90% de los participantes identifica la razón sin ayuda (RNF7)

Escenario: Ficha de especie no amenazada
  Dado una ficha no amenazada con coordenadas publicadas
  Cuando se abre
  Entonces recibe las coordenadas, "ubicacionProtegida": false (supuesto S1, ver P1)

Escenario: Ficha inexistente
  Dado un id que no existe o un valor no UUID
  Cuando se consulta
  Entonces recibe 404 (id inexistente) o 400 (formato inválido), sin trazas internas

Escenario: Intento de obtener coordenadas con parámetros
  Dado una ficha en peligro
  Cuando se llama con ?incluirCoordenadas=true o con un JWT de cualquier rol
  Entonces la respuesta es idéntica a la del visitante (el endpoint público ignora el rol)
```

#### HU-06.4 – Rendimiento, caché y resiliencia (RNF1, RNF5, ASR1, ASR4)
Como **visitante** quiero respuestas en menos de 2 s incluso en un pico viral para poder ver la ficha.

```gherkin
Escenario: Respuesta rápida con caché
  Dado la ficha ya consultada una vez
  Cuando se vuelve a consultar
  Entonces se sirve desde Redis y el p95 es <= 500 ms (supuesto)

Escenario: Respuesta en frío
  Dado 10.000 fichas en mysql-lectura (supuesto) y caché vacía
  Cuando se hacen 100 búsquedas distintas
  Entonces el p95 es <= 2 s (RNF5)

Escenario: Carga científica simultánea
  Dado un proceso que publica eventos y escribe en el esquema cientifico de forma continua
  Cuando se ejecuta la prueba de consulta pública
  Entonces el p95 sigue <= 2 s

Escenario: Pico x60
  Dado una línea base medida de N peticiones/s y un pico de 60*N sobre una misma ficha durante 2 minutos
  Cuando se ejecuta la prueba de carga
  Entonces >= 95% de las respuestas son 2xx (sin 5xx) y el sistema se estabiliza en < 5 minutos (RNF1)

Escenario: Redis caído
  Dado Redis detenido
  Cuando el visitante consulta una ficha o hace una búsqueda
  Entonces recibe 200 desde mysql-lectura sin error 5xx, se registra una advertencia y, al volver Redis, se usa de nuevo sin reiniciar

Escenario: Caché actualizada por evento
  Dado una ficha en caché
  Cuando llega un evento que la modifica
  Entonces la siguiente consulta devuelve los datos nuevos (la entrada se invalida; TTL máximo 5 min)

Escenario: Caché sin datos sensibles
  Dado cualquier ficha en Redis
  Cuando se inspeccionan sus claves y valores
  Entonces no hay coordenadas de especies en peligro ni datos de usuarios

Escenario (Should): Modo degradado
  Dado el modo degradado activado por configuración
  Cuando el visitante usa filtros avanzados (familia, departamento)
  Entonces la búsqueda por nombre sigue funcionando, los filtros avanzados se ignoran o desactivan con aviso en la respuesta ("modoDegradado": true) y no hay 5xx

Escenario: Lectura de la BD de lectura caída
  Dado mysql-lectura no disponible y la ficha fuera de caché
  Cuando se consulta
  Entonces recibe 503 con problem+json genérico (sin detalles internos); las fichas en caché siguen sirviéndose
```

#### HU-06.5 – Reclasificación en el portal (RF11)
Como **Instituto** quiero que una especie reclasificada como "en peligro" deje de mostrar su ubicación en el portal de inmediato.

```gherkin
Escenario: Pasa a "EN_PELIGRO"
  Dado una ficha pública con coordenadas
  Cuando llega "hallazgo.reclasificado" con estado "EN_PELIGRO" y versión mayor
  Entonces la ficha elimina sus coordenadas de mysql-lectura, se invalida su caché y la consulta posterior no las devuelve

Escenario: Cambia la especie
  Dado una ficha con nombre científico A
  Cuando llega un evento de reclasificación con nombre científico B
  Entonces la búsqueda por B la encuentra y por A ya no (salvo otras fichas)

Escenario: Pasa de "EN_PELIGRO" a no amenazada
  Dado una ficha protegida
  Cuando llega el evento sin coordenadas
  Entonces sigue sin coordenadas (solo departamento)

Escenario: Reclasificación de un hallazgo desconocido
  Dado un evento de reclasificación sin ficha previa
  Cuando se consume
  Entonces se crea la ficha con ese snapshot completo (es idempotente) o, si el snapshot es incompleto, va a la DLQ
```

#### HU-06.6 – Interfaz web-publica (RNF13)
```gherkin
Escenario: Navegadores soportados
  Dado las dos últimas versiones de Chrome, Firefox, Edge y Safari
  Cuando se abre el buscador y una ficha
  Entonces funcionan sin errores de consola

Escenario: Estados de la interfaz
  Dado una búsqueda en curso, sin resultados o con error de red
  Cuando se muestra la pantalla
  Entonces aparece respectivamente indicador de carga, mensaje "No se encontraron especies" y mensaje "No pudimos cargar la información, intenta de nuevo" con botón de reintento

Escenario: Sin credenciales en el cliente
  Dado web-publica en el navegador
  Cuando se inspecciona el código, el almacenamiento y las peticiones
  Entonces no hay tokens, contraseñas ni llaves secretas

Escenario: Idioma
  Dado la interfaz
  Cuando se revisan textos y mensajes
  Entonces están en español
```

### 3.3 Mapa interactivo (HU-07 refinada)

Recurso propuesto: `GET /api/cientifico/ubicacion/mapa?especie=&departamento=&desde=&hasta=` → GeoJSON.

#### HU-07.1 – Endpoint de mapa con coordenadas exactas (RF12, RF13)
Como **cientifico_alto_rango** quiero obtener las ubicaciones exactas de los hallazgos para analizar su distribución geográfica.

```gherkin
Escenario: Alto rango obtiene el mapa
  Dado cientifico1 con token válido y hallazgos con coordenadas (en claro y cifradas)
  Cuando llama GET /api/cientifico/ubicacion/mapa
  Entonces recibe 200 con una feature por hallazgo con coordenadas exactas, incluidas las de especies en peligro (descifradas)
  Y la respuesta lleva Cache-Control: no-store

Escenario: Rol sin permiso
  Dado un token de investigador1, auditor1, analista1 o admin1
  Cuando llama al endpoint de mapa
  Entonces recibe 403 y el cuerpo no contiene coordenadas

Escenario: Sin token
  Dado ninguna cabecera Authorization
  Cuando llama al endpoint (directo al servicio o por el gateway)
  Entonces recibe 401

Escenario: Token vencido o de otro emisor
  Dado un JWT vencido, firmado por otro emisor o sin aud=biohub-api
  Cuando llama
  Entonces recibe 401

Escenario: Validación en cada petición
  Dado cientifico1 con un token válido
  Cuando se le revoca el rol y obtiene un token nuevo, y llama otra vez
  Entonces recibe 403 (el permiso se evalúa en cada petición con las authorities del token)

Escenario: Descifrado solo en memoria
  Dado una petición autorizada
  Cuando se atiende
  Entonces las coordenadas descifradas no se escriben en BD, caché Redis, logs ni archivos, y no se guardan en ningún campo estático o caché del servicio

Escenario: Filtros
  Dado hallazgos de varias especies
  Cuando llama con especie="X" y departamento="Boyacá"
  Entonces solo recibe los que cumplen ambos

Escenario: Vacío
  Dado ningún hallazgo que cumpla los filtros
  Cuando llama
  Entonces recibe 200 con "features": [] y total 0

Escenario: Parámetros inválidos
  Dado desde > hasta o una fecha mal formada
  Cuando llama
  Entonces recibe 400

Escenario: Límite de marcadores
  Dado más de 1000 hallazgos que cumplen los filtros
  Cuando llama
  Entonces recibe como máximo 1000 y "truncado": true con indicación de afinar filtros

Escenario: Un registro no descifrable
  Dado un registro con llave retirada o texto alterado
  Cuando llama
  Entonces ese registro se omite, se cuenta en "omitidos" y el resto responde 200
```

#### HU-07.2 – Pantalla de mapa (Should)
```gherkin
Escenario: Alto rango ve marcadores
  Dado cientifico1 autenticado en la UI interna
  Cuando abre la pantalla de mapa
  Entonces ve un mapa con un marcador por hallazgo, y al pulsarlo ve especie, fecha y coordenadas

Escenario: Rol sin permiso
  Dado investigador1 autenticado
  Cuando intenta abrir la ruta del mapa
  Entonces ve el mensaje "No tienes permiso para ver esta información" y la UI no hace la llamada o recibe 403 sin pintar marcadores

Escenario: Sin resultados
  Dado filtros sin coincidencias
  Cuando se aplican
  Entonces el mapa muestra "No hay hallazgos para los filtros seleccionados"

Escenario: Truncado
  Dado la respuesta con "truncado": true
  Cuando se muestra
  Entonces aparece un aviso para afinar los filtros
```

#### HU-07.3 – Registro de accesos a coordenadas exactas (Could)
```gherkin
Escenario: Se registra el acceso
  Dado cientifico1 que consulta el mapa
  Cuando se atiende la petición
  Entonces se escribe un log de auditoría con usuario, fecha/hora, filtros y cantidad de registros, sin coordenadas

Escenario: Acceso denegado
  Dado investigador1 que llama al mapa
  Cuando recibe 403
  Entonces se registra el intento denegado con usuario y fecha/hora
```

### 3.4 Búsqueda y filtrado interno (HU-13 refinada)

Recurso propuesto: `GET /api/cientifico/ubicacion/hallazgos?especie=&desde=&hasta=&investigador=&departamento=&page=&size=`.

#### HU-13.1 – Filtros combinables y coordenadas según rol (RF12, RF13)
Como **usuario interno** quiero buscar hallazgos por especie, fecha, investigador o ubicación para dar seguimiento a mis registros.

```gherkin
Escenario: Dos filtros combinados
  Dado hallazgos de varias especies y fechas
  Cuando investigador1 busca especie="Atelopus" y desde=2026-01-01&hasta=2026-06-30
  Entonces recibe 200 solo con los que cumplen ambos y total correcto

Escenario: Los cuatro filtros
  Dado hallazgos variados
  Cuando combina especie, rango de fechas, investigador y departamento
  Entonces recibe solo las coincidencias de los cuatro

Escenario: Sin coordenadas para roles sin permiso
  Dado un hallazgo "EN_PELIGRO" con coordenadas cifradas
  Cuando investigador1, auditor1, analista1 o admin1 lo obtienen por búsqueda
  Entonces el resultado trae departamento, región y "ubicacionProtegida": true, sin campos de coordenadas (ni descifradas ni cifradas)

Escenario: Coordenadas para alto rango
  Dado el mismo hallazgo
  Cuando cientifico1 lo busca
  Entonces el resultado incluye latitud y longitud exactas (descifradas en memoria)

Escenario: Hallazgo no amenazado
  Dado un hallazgo no amenazado con coordenadas en claro
  Cuando investigador1 lo busca
  Entonces ve las coordenadas (supuesto S1, ver P1)

Escenario: Filtro por área geográfica solo para alto rango
  Dado un rol distinto de cientifico_alto_rango
  Cuando usa un filtro por rectángulo de coordenadas (bbox)
  Entonces recibe 403, porque filtrar por área permitiría deducir la posición

Escenario: Sin token
  Dado ninguna cabecera Authorization
  Cuando llama
  Entonces recibe 401

Escenario: Visitante o consumidor externo
  Dado una petición sin JWT por /api/publica/**
  Cuando intenta acceder a la búsqueda interna por esa ruta
  Entonces no existe (404) y la ruta interna sigue exigiendo JWT

Escenario: Sin resultados
  Dado filtros sin coincidencias
  Cuando busca
  Entonces recibe 200 con lista vacía y total 0

Escenario: Sin filtros
  Dado hallazgos registrados
  Cuando busca sin parámetros
  Entonces recibe la primera página (20 por defecto) ordenada por fecha de recolección descendente

Escenario: Filtros inválidos
  Dado desde > hasta, fecha mal formada, size > 100 o especie de más de 100 caracteres
  Cuando busca
  Entonces recibe 400 con problem+json
```

#### HU-13.2 – Rendimiento de la búsqueda
```gherkin
Escenario: Volumen alto
  Dado 10.000 hallazgos indexados (supuesto) con 20% en peligro
  Cuando se ejecutan 100 búsquedas con 2 filtros combinados
  Entonces el p95 es < 3 s también para cientifico1, a quien se le descifran las coordenadas de la página

Escenario: El descifrado se limita a la página
  Dado una búsqueda con size=50
  Cuando la atiende alto rango
  Entonces se descifran como máximo 50 registros por petición

Escenario: Índices
  Dado el esquema final
  Cuando se revisan las migraciones
  Entonces existen índices para los campos filtrables (especie, fecha, investigador, departamento)
```

#### HU-13.3 – Pantalla de búsqueda (Should)
```gherkin
Escenario: Resultados según rol
  Dado la pantalla de búsqueda interna
  Cuando investigador1 busca y luego cientifico1 busca lo mismo
  Entonces solo cientifico1 ve la columna de coordenadas exactas

Escenario: Sin resultados
  Dado filtros sin coincidencias
  Cuando se buscan
  Entonces aparece "No hay hallazgos que coincidan con los filtros"

Escenario: Sesión vencida
  Dado un token vencido
  Cuando se busca
  Entonces la UI redirige al login (flujo de Santiago) sin mostrar datos previos
```

### 3.5 HU-08.J – Contrato de datos públicos (Should; el control de acceso es de Santiago)
Como **desarrollador externo** quiero respuestas JSON estables y paginadas para integrarlas en mis modelos.
```gherkin
Escenario: Mismo contenido que el portal
  Dado una ficha "EN_PELIGRO"
  Cuando se consulta por /api/publica/fichas/{id} con o sin API Key válida
  Entonces el cuerpo es el mismo y nunca contiene coordenadas

Escenario: Formato estable
  Dado una consulta de listado
  Cuando se responde
  Entonces incluye "content", "page", "size", "total" y los nombres de campo coinciden con 04-contrato-api.md

Escenario: Control de acceso fuera de este servicio
  Dado una petición sin API Key o por encima del límite
  Cuando llega al gateway
  Entonces el 401/429 lo emite el gateway (Santiago) y consulta-publica no implementa API Keys ni conteo de peticiones
```

---

## 4. Dependencias y contratos con otros integrantes

Todo lo siguiente es propuesta a acordar; cuando se acepte, Hernando/Tech Lead lo pasan a `04-contrato-api.md`.

### 4.1 Con Hernando (registro)

**Qué necesita Juanita**
1. **Origen de los datos:** `registro` es el dueño del hallazgo (nombre científico, familia, tipo de espécimen, fecha de recolección, investigador, departamento/región, coordenadas, estado de conservación).
2. **Quién marca "En Peligro":** `registro` guarda un campo `estadoConservacion` (valores propuestos: `EN_PELIGRO`, `NO_AMENAZADA`, `SIN_EVALUAR`; si Hernando prefiere categorías UICN, debe indicar cuáles equivalen a "En Peligro"). Juanita no decide la clasificación; solo reacciona a ella. Si falta el valor, se trata como `EN_PELIGRO` (falla segura).
3. **Entrega de la ubicación a ubicación**, en la misma transacción que guarda el hallazgo, vía la clase pública `UbicacionService` (HU-UBI-03). No debe guardar coordenadas en sus propias tablas en claro cuando el estado sea `EN_PELIGRO`.
4. **Publicar eventos** después de confirmar la transacción, sin coordenadas de especies en peligro.

**Evento `hallazgo.registrado`** (routing key = tipo, exchange `biohub.eventos`, sobre ya existente: id, tipo, origen, fecha, usuario, datos)

| Campo en `datos` | Tipo | Obligatorio | Nota |
|---|---|---|---|
| `hallazgoId` | UUID | Sí | Llave de la ficha pública |
| `version` | entero >= 1 | Sí | 1 al registrar; sube en cada reclasificación |
| `nombreCientifico` | texto (<=150) | Sí | |
| `nombreComun` | texto | No | |
| `familia` | texto | Sí | Necesaria para buscar por familia |
| `tipoEspecimen` | texto | No | |
| `fechaRecoleccion` | fecha ISO | Sí | |
| `estadoConservacion` | enum | Sí | Ver arriba |
| `departamento` | texto | Sí | |
| `region` | texto | No | |
| `coordenadas` | `{latitud, longitud}` | **Solo si el estado no es `EN_PELIGRO`** (y sujeto a P1) | **Prohibido** en especies en peligro |
| `descripcion` | texto | No | Texto público de la ficha |

No debe incluir: investigador, justificaciones, rutas de adjuntos ni atributos internos. Los atributos flexibles de la ficha (HU-02) quedan fuera del MVP público.

**Evento `hallazgo.reclasificado`:** mismo `datos` completo (snapshot) con `version` incrementada. Al ser snapshot completo, el consumidor es idempotente y no depende de recibir el evento anterior. Lo cubre el binding `hallazgo.#`. La justificación científica y el historial **no** viajan en el evento (son internos, HU-05).

**Qué ofrece Juanita a Hernando**
- `UbicacionService` con firma estable y excepciones de validación claras.
- Consumo idempotente y tolerante a duplicados y desorden; DLQ para eventos inválidos.
- Un validador/ejemplo JSON del evento para que Hernando pruebe su publicación.
- Respuesta rápida si el contrato cambia (un cambio de campos se avisa antes de abrir PR).

**Qué hace Juanita mientras Hernando no entrega**
- Tests de integración con **eventos simulados** publicados desde el propio test (Testcontainers o RabbitMQ embebido/mock) con fixtures en el código de pruebas.
- Tests de `ubicacion` llamando directamente a `UbicacionService` con datos de prueba y transacción revertida o BD efímera.
- **No deja datos** en las BD compartidas, colas ni Redis de las VMs (iteración 14). Cualquier semilla de demostración vive en un perfil local explícito y no se ejecuta en las VMs.
- Si el contrato aún no está firmado, trabaja con el que figura aquí y lo ajusta con un cambio pequeño y localizado.

### 4.2 Con Santiago (gateway, Keycloak, roles, infraestructura)

**Qué necesita Juanita**
1. **Roles/permisos:** confirmar la matriz de la sección 2 (en especial que `administrador` NO ve coordenadas exactas) y la regla `denyAll` por defecto (SEC-02). Juanita protege sus endpoints con `@PreAuthorize`/reglas en su servicio según esa matriz.
2. **Rutas del gateway:** `/api/cientifico/ubicacion/**` con JWT (ya cubierto por el prefijo `/api/cientifico/**`); `GET /api/publica/**` sin JWT para el portal (ya existe); reenvío de `Authorization` y de `Cache-Control`.
3. **CORS** para el origen de web-publica (y el de la UI interna).
4. **API Keys y rate limit:** decisión de cómo accede **web-publica** (P2). Juanita no implementa nada de esto.
5. **Variables de entorno** para las llaves de cifrado (por ejemplo `UBICACION_CLAVE_ACTIVA`, `UBICACION_CLAVES_<version>`): incluirlas en `.env.example` y en el despliegue (`desplegar.sh`, inyección segura a la VM de backend), sin valores reales en el repo. También el usuario MQ del consumidor con permiso de lectura sobre `biohub.eventos` y la DLX (ya descrito en el plan 13b), y la restricción de escritura de eventos (SEC-D-03).
6. **Zonas compartidas:** Juanita avisa antes de tocar `config/` de `servicio-cientifico` (compartida con Hernando) o `docker-compose.yml`.

**Qué ofrece Juanita a Santiago**
- Lista exacta de endpoints y roles requeridos (sección 3) para sus reglas del gateway y pruebas.
- Datos públicos con formato estable para que él los exponga con API Key (4.4).
- Pruebas negativas de acceso (sin token, rol incorrecto) que sirven de regresión para sus cambios de seguridad.
- Un componente/carpeta de UI interna autocontenida (mapa y búsqueda) que él monta en la navegación (P3).

**Qué hace Juanita mientras Santiago no entrega**
- Tests con JWT simulados (como ya hacen los tests de ping) y roles de los usuarios de prueba (`cientifico1`, `investigador1`, etc.).
- Llaves de prueba solo en el perfil de test, nunca reutilizadas en despliegue.
- Mantiene `/api/publica/**` sin API Key (como hoy) y no escribe ningún filtro de API Keys.

### 4.3 Con Nicolás (reportes) – solo informativo
- Nicolás consume `hallazgo.registrado` desde su propia cola. Juanita le informa que **las coordenadas de especies en peligro no viajan en el evento**; si los reportes de deforestación las necesitaran, eso requeriría otra decisión de seguridad (Won't en este alcance).
- Juanita no consume nada de Nicolás ni de equipos.

### 4.4 Contrato público que ofrece Juanita (resumen para 04-contrato-api.md)
- `GET /api/publica/fichas` → `{ content: [ficha resumida], page, size, total, modoDegradado }`
- `GET /api/publica/fichas/{id}` → `{ id, nombreCientifico, nombreComun, familia, tipoEspecimen, fechaRecoleccion, estadoConservacion, departamento, region, descripcion, ubicacionProtegida, coordenadas? }` (`coordenadas` solo cuando `ubicacionProtegida` es false)
- Errores: 400 parámetros, 404 ficha, 503 lectura caída.

---

## 5. MVP priorizado para Juanita (orden de construcción)

| Orden | Entrega | MoSCoW | Depende de |
|---|---|---|---|
| 1 | Acordar contrato del evento y de `UbicacionService` (sección 4) | Must | Hernando |
| 2 | HU-UBI-01 y HU-UBI-02: cifrado AES-256-GCM, llaves versionadas, fallo al arrancar sin llave | Must | variables de entorno (Santiago) |
| 3 | HU-UBI-03: `UbicacionService`, migración Flyway, índice de búsqueda | Must | contrato 1 |
| 4 | HU-06.1: consumidor, `mysql-lectura`, reglas de rechazo de coordenadas en peligro | Must | contrato 1 |
| 5 | HU-06.2 y HU-06.3: endpoints públicos de búsqueda y ficha | Must | 4 |
| 6 | HU-06.4 (caché Redis, Redis caído) y HU-06.5 (reclasificado) | Must | 5 |
| 7 | HU-06.6: web-publica (buscador, resultados, ficha, explicación de ubicación protegida) | Must | 5 |
| 8 | HU-13.1 y HU-13.2: búsqueda interna | Must | 3 |
| 9 | HU-07.1: endpoint de mapa | Must | 2, 3 |
| 10 | Prueba de carga x60 y de aislamiento (HU-06.4) | Must | 6, 7 |
| 11 | HU-07.2 y HU-13.3: pantallas internas de mapa y búsqueda | Should | 8, 9; Santiago para montaje |
| 12 | Modo degradado (HU-06.4), HU-08.J, re-cifrado masivo | Should | 6 |
| 13 | HU-07.3 (registro de accesos), modo degradado automático, filtro por bbox para alto rango, clustering de marcadores | Could | – |

**Won't por ahora:** API Keys y rate limit (Santiago); mapa en el portal público; atributos flexibles de la ficha en el portal; adjuntos (fotos/audio) en el portal; KMS externo real (Vault/AWS KMS) salvo que se pida (P4); traducción a otros idiomas; búsqueda de texto completo avanzada (Elasticsearch).

---

## 6. Requisitos no funcionales aplicables (con umbral medible)
| ID | Requisito | Umbral / verificación |
|---|---|---|
| RNF1 / ASR1 | Pico x60 sobre consulta pública | >= 95% de respuestas sin 5xx, estabilización < 5 min; prueba de carga |
| RNF5 / ASR4 | Respuesta pública | <= 2 s (p95), también con carga científica simultánea |
| RNF7 | Comprensión de la restricción | >= 90% identifica la razón en prueba de usabilidad (mínimo 5 participantes, supuesto) |
| RNF9 / ASR6 | Cifrado | 0 coordenadas de especies en peligro en claro en BD, logs, eventos, caché |
| RNF13 | Navegadores | Dos últimas versiones de Chrome, Firefox, Edge y Safari |
| HU-13 | Búsqueda interna | < 3 s (p95) con 10.000 hallazgos |
| Seguridad | Validación de entrada, SQL parametrizado, errores sin trazas, `Cache-Control: no-store` en mapa | Pruebas negativas de la sección 3 |
| Idioma | Interfaz y mensajes en español | Revisión manual |
| Accesibilidad (Should) | Contraste y navegación por teclado en web-publica | Revisión con lista WCAG AA básica |

## 7. Reglas de negocio
1. Una ubicación de hallazgo `EN_PELIGRO` se guarda **solo** cifrada (AES-256-GCM, nonce aleatorio de 12 bytes por registro, AAD = id del hallazgo, versión de llave junto al dato).
2. Estado desconocido o ausente = `EN_PELIGRO` (falla segura).
3. Latitud en [-90, 90]; longitud en [-180, 180]; ambas o ninguna.
4. Una vez cifrada, una ubicación no vuelve a texto plano aunque la especie deje de estar en peligro.
5. Solo `cientifico_alto_rango` recibe coordenadas exactas de especies en peligro, en mapa y en búsqueda. Ni `administrador` ni el propio investigador que registró el hallazgo las reciben.
6. El evento y el modelo de lectura público nunca llevan coordenadas de especies en peligro; el consumidor rechaza un evento que lo incumpla.
7. Eventos: gana la `version` mayor por `hallazgoId`; los repetidos o antiguos se ignoran.
8. Búsqueda pública: `q` 2–100 caracteres, `size` por defecto 20, máximo 50; búsqueda sin distinguir mayúsculas ni tildes. Búsqueda interna: `size` máximo 100. Mapa: máximo 1000 marcadores.
9. Caché pública: TTL 5 min para fichas y 60 s para listados; se invalida por evento; Redis no es fuente de verdad.
10. Las llaves nunca están en BD, repositorio, logs ni respuestas. El arranque exige llave activa.
11. El mapa y la búsqueda interna evalúan el rol en cada petición con el JWT; no cachean respuestas con coordenadas.

---

## 8. Supuestos y preguntas abiertas

### Supuestos (sin ellos el documento no sería verificable)
- S1. Para especies no amenazadas el portal público muestra coordenadas, siguiendo el SRS (solo se oculta lo "En Peligro"). Se aplica cifrado solo a las de peligro (RNF9).
- S2. "Alto volumen" = 10.000 hallazgos; línea base de tráfico N se mide al inicio de la prueba de carga; el pico es 60N durante 2 minutos.
- S3. El hallazgo, sus datos y el estado de conservación nacen en `registro` (Hernando); ubicación mantiene un índice mínimo propio para buscar sin importar clases de `registro`.
- S4. Todo usuario interno autenticado puede buscar; `investigador_campo` puede buscar hallazgos de otros investigadores.
- S5. Llaves en variables de entorno o archivo montado, detrás de una interfaz `ProveedorDeLlaves` sustituible por un KMS real.

### Preguntas abiertas para Juanita (solo las que cambian el alcance)
- **P1.** ¿El portal público debe mostrar coordenadas exactas de especies que no están en peligro (S1), o solo departamento/región para todas? Con "solo región para todas" el evento ya no necesita coordenadas, el modelo de lectura es más simple y el riesgo baja.
- **P2.** HU-12 pide 401 para cualquier petición a la API pública sin API Key, pero el portal público es sin login. ¿web-publica usa una ruta distinta de la de los externos, una key propia del portal o queda exenta? Hay que acordarlo con Santiago porque cambia lo que envía web-publica y si debe haber rutas separadas.
- **P3.** ¿Dónde viven las pantallas de mapa y búsqueda interna (HU-07, HU-13)? Propuesta: carpeta autocontenida en `web-interna` entregada por Juanita y montada por Santiago en la navegación. Si no se acepta, esas pantallas pasan a Santiago y Juanita entrega solo los endpoints.
- **P4.** ¿Bastan las llaves por variable de entorno con versión y rotación (S5) para "KMS o equivalente en prototipo", o se exige un KMS real (Vault/AWS KMS)? Esto último añade infraestructura compartida.
- **P5.** ¿La reclasificación (`hallazgo.reclasificado`, HU-03 de Hernando) entra en la primera entrega? Si no, hay riesgo de que una especie pase a "en peligro" y el portal siga mostrando sus coordenadas. Se recomienda como Must junto con HU-03.


## 9. Decisiones tomadas tras la revisión (2026-10-26, prevalecen sobre lo anterior)

- **P1 → D14:** el portal público **no muestra coordenadas exactas de ninguna especie**, esté o no en peligro. Solo departamento/región. Cualquier criterio de este documento que mencione coordenadas públicas de especies no amenazadas queda anulado. El evento hacia consulta pública nunca lleva coordenadas (de ninguna especie); si las trae, se rechaza a la DLQ.
- **D15:** se cifran **todas** las coordenadas (no solo las de especies en peligro), AES-256-GCM, llaves versionadas en variables de entorno, con rotación. HU-UBI-01 aplica a todas las ubicaciones.
- **P2 → D16:** web-publica usa `/api/publica/**` sin API Key (solo rate limit). Las API Keys son de la API externa de Santiago. Juanita se lo comunica a Santiago.
- **P3 → D17:** las pantallas de mapa y búsqueda interna las hace Juanita en `web-interna/src/ubicacion/`; Santiago monta la ruta y el enlace.
- **P4 → D15:** llaves por variable de entorno con versión y rotación son el "KMS equivalente" del prototipo.
- **P5:** `hallazgo.reclasificado` se mantiene en el contrato (Must) para que la reclasificación llegue al portal; con D14 el riesgo de coordenadas expuestas desaparece, pero el nombre/estado debe actualizarse.
