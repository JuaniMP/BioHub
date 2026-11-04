// PENDIENTE - NO MEDIBLE: depende de HU-01 (módulo registro de Hernando, hoy solo package-info.java; ARQ-01).
// RNF2 / RNF4 / ASR2: registro de hallazgo con archivos adjuntos <= 5 s (p95), también durante el pico x60 público.
// Este esqueleto queda listo: cuando exista POST /api/cientifico/hallazgos, ajustar RUTA y el cuerpo según 04-contrato-api.md,
// y combinarlo con el escenario `pico` de consulta-publica-pico.js para RNF2 (registro durante el pico).
// Ejecutar: export TOKEN=<JWT de investigador>; ./correr-k6.sh k6/registro-hallazgo.js -e BASE_URL=http://127.0.0.1:8080 (TOKEN se pasa por entorno, ver README)
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const RUTA = __ENV.RUTA || '/api/cientifico/hallazgos';          // PENDIENTE: no existe (HU-01)
const ADJUNTO = open('./lib/adjunto-ejemplo.bin', 'b');             // PENDIENTE: archivo de prueba (p. ej. 1-2 MB)

export const options = {
  scenarios: { registro: { executor: 'constant-arrival-rate', rate: 2, timeUnit: '1s', duration: '60s', preAllocatedVUs: 10, maxVUs: 50 } },
  thresholds: {
    // PENDIENTE de HU-01: este umbral NO se ha evaluado nunca contra un registro real.
    http_req_duration: ['p(95)<5000'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const datos = {
    nombreCientifico: 'Pristimantis carga', familia: 'Strabomantidae', fechaRecoleccion: '2026-03-14',
    estadoConservacion: 'NO_AMENAZADA', departamento: '15',
    archivo: http.file(ADJUNTO, 'adjunto.bin', 'application/octet-stream'),
  };
  const r = http.post(`${BASE_URL}${RUTA}`, datos, { headers: { Authorization: `Bearer ${__ENV.TOKEN}` }, timeout: '15s' });
  check(r, { 'registro creado (201)': (x) => x.status === 201 });
}
