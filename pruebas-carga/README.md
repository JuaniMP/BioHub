# pruebas-carga

Carpeta reservada para las **pruebas de carga** de BioHub. Hoy está vacía: no hay scripts implementados.

## Propósito

Medir cómo responde el sistema bajo muchas peticiones simultáneas (tiempos de respuesta, errores y comportamiento del rate limiting).

## Recomendación

- Herramientas: [k6](https://k6.io) o [Apache JMeter](https://jmeter.apache.org).
- Objetivo: el **API Gateway** (`http://localhost:8080`), no los servicios directamente, para probar el recorrido completo.
- Para rutas con JWT, obtén un token con el cliente `biohub-pruebas` (ver README raíz, sección 4f). El token dura 5 minutos: renuévalo en el script.
- `/api/publica/**` no exige JWT; más adelante usará `X-API-Key` y rate limiting.

## Reglas

- Ejecuta las pruebas solo contra el entorno local o de pruebas.
- No guardes contraseñas ni tokens en los scripts: léelos de variables de entorno.
