// ASR1 / RNF1: pico >= 60x sobre la línea base en la consulta pública. Mide % sin 5xx, p95 y estabilización.
//
// Perfil (arrival-rate, tasa de peticiones constante e independiente de la latencia: modela usuarios reales):
//   base N req/s (T_BASE) -> rampa a 60N (T_RAMP) -> pico 60N (T_PICO) -> baja a N (T_BAJA) -> N (T_POST)
// Etiquetas: cada petición lleva `ventana` (bloques de 15 s desde el inicio) y `fase` (base|rampa|pico|baja|post).
//
// Ejecutar (desde pruebas-carga/):  ./correr-k6.sh k6/consulta-publica-pico.js -e N=20
// Variables: BASE_URL, N (def. 20), MULT (def. 60), T_BASE (60s), T_RAMP (30s), T_PICO (120s), T_BAJA (10s),
//            T_POST (180s), MAX_VUS (def. 4000), SALIDA (ruta del JSON de resultados).
// Metadatos D42 que guarda en `parametros`: REPLICAS y CPUS (los exporta quien corre), COMMIT y RED_K6 (los pone correr-k6.sh).
import { sleep } from 'k6';
import exec from 'k6/execution';
import { mezcla, ventana, aSegundos, VENTANA_S } from './lib/comun.js';

const N = parseInt(__ENV.N || '20');
const MULT = parseInt(__ENV.MULT || '60');
const T = {
  base: aSegundos(__ENV.T_BASE || '60s'), rampa: aSegundos(__ENV.T_RAMP || '30s'),
  pico: aSegundos(__ENV.T_PICO || '120s'), baja: aSegundos(__ENV.T_BAJA || '10s'), post: aSegundos(__ENV.T_POST || '180s'),
};
const TOTAL = T.base + T.rampa + T.pico + T.baja + T.post;
const FIN_RAMPA = T.base + T.rampa;          // el pico alcanza 60N aquí
const FIN_PICO = FIN_RAMPA + T.pico;
const NVENT = Math.ceil(TOTAL / VENTANA_S);

// Un umbral "vacío" por ventana hace que k6 calcule p(95) por ventana y lo deje en el resumen/JSON.
const umbralesVentana = {};
for (let i = 0; i < NVENT; i++) {
  umbralesVentana[`http_req_duration{ventana:w${String(i).padStart(2, '0')}}`] = ['p(95)<2000'];
}

export const options = {
  scenarios: {
    pico: {
      executor: 'ramping-arrival-rate',
      startRate: N, timeUnit: '1s',
      preAllocatedVUs: Math.min(500, N * 5), maxVUs: parseInt(__ENV.MAX_VUS || '4000'),
      stages: [
        { target: N, duration: `${T.base}s` },
        { target: N * MULT, duration: `${T.rampa}s` },
        { target: N * MULT, duration: `${T.pico}s` },
        { target: N, duration: `${T.baja}s` },
        { target: N, duration: `${T.post}s` },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],       // RNF1: >= 95 % de respuestas sin error (aquí, cualquier no-200)
    errores_5xx: ['rate<0.05'],           // 5xx + timeouts/conexiones rechazadas
    http_req_duration: ['p(95)<2000'],    // RNF5 / HU-06 c.3 sobre toda la corrida
    'http_req_duration{fase:base}': ['p(95)<2000'],
    'http_req_duration{fase:pico}': ['p(95)<2000'],
    'http_req_duration{fase:post}': ['p(95)<2000'],
    ...umbralesVentana,
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  discardResponseBodies: true,
};

function fase(seg) {
  if (seg < T.base) return 'base';
  if (seg < FIN_RAMPA) return 'rampa';
  if (seg < FIN_PICO) return 'pico';
  if (seg < FIN_PICO + T.baja) return 'baja';
  return 'post';
}

export default function () {
  const ms = Date.now() - exec.scenario.startTime;
  mezcla({ ventana: ventana(ms), fase: fase(ms / 1000) });
}

export function handleSummary(data) {
  const m = data.metrics;
  const v = (nombre, stat) => (m[nombre] && m[nombre].values && m[nombre].values[stat] !== undefined ? m[nombre].values[stat] : null);
  const por_fase = {};
  for (const f of ['base', 'pico', 'post']) {
    const k = `http_req_duration{fase:${f}}`;
    por_fase[f] = { p50: v(k, 'med'), p95: v(k, 'p(95)'), p99: v(k, 'p(99)') };
  }
  const ventanas = [];
  for (let i = 0; i < NVENT; i++) {
    const w = 'w' + String(i).padStart(2, '0');
    const k = `http_req_duration{ventana:${w}}`;
    ventanas.push({ ventana: w, desde_s: i * VENTANA_S, p95: v(k, 'p(95)'), p99: v(k, 'p(99)') });
  }
  // Estabilización: desde el fin de la rampa (60N alcanzado) hasta el inicio de la primera ventana a partir de la
  // cual TODAS las siguientes tienen p95 < 2 s. 0 = el p95 nunca superó 2 s tras la rampa.
  const tras = ventanas.filter((x) => x.desde_s + VENTANA_S > FIN_RAMPA && x.p95 !== null);
  let ultimaMala = -1;
  tras.forEach((x, idx) => { if (x.p95 >= 2000) ultimaMala = idx; });
  let estabilizacion_s = 0;
  if (ultimaMala >= 0) {
    estabilizacion_s = ultimaMala + 1 < tras.length ? Math.max(0, tras[ultimaMala + 1].desde_s - FIN_RAMPA) : null; // null = nunca se estabilizó
  }
  const total = v('http_reqs', 'count');
  const resumen = {
    // Metadatos exigidos por D42 (un JSON sin ellos no cuenta como evidencia): réplicas, CPU por réplica, commit y red de k6.
    // correr-k6.sh rellena COMMIT y RED_K6; REPLICAS y CPUS los pasa quien corre la prueba (ver README).
    parametros: { N, MULT, pico_req_s: N * MULT, T, total_s: TOTAL, fin_rampa_s: FIN_RAMPA, fin_pico_s: FIN_PICO,
      replicas: __ENV.REPLICAS || null, cpus_por_replica: __ENV.CPUS || null, commit: __ENV.COMMIT || null, red_k6: __ENV.RED_K6 || null },
    peticiones: total,
    tasa_media_req_s: v('http_reqs', 'rate'),
    iteraciones_descartadas: v('dropped_iterations', 'count') || 0,
    vus_max: v('vus_max', 'max'),
    // D42: éxito = 200 / (peticiones + iteraciones descartadas); la carga que k6 no pudo emitir cuenta como fallo
    exito_d42_pct: v('http_req_failed', 'rate') === null || !(total + (v('dropped_iterations', 'count') || 0)) ? null
      : (1 - v('http_req_failed', 'rate')) * total / (total + (v('dropped_iterations', 'count') || 0)) * 100,
    fallos_no200_pct: v('http_req_failed', 'rate') === null ? null : v('http_req_failed', 'rate') * 100,
    errores_5xx_pct: v('errores_5xx', 'rate') === null ? null : v('errores_5xx', 'rate') * 100,
    latencia_ms: { p50: v('http_req_duration', 'med'), p90: v('http_req_duration', 'p(90)'), p95: v('http_req_duration', 'p(95)'), p99: v('http_req_duration', 'p(99)'), max: v('http_req_duration', 'max') },
    por_fase,
    estabilizacion_s,
    ventanas,
  };
  const salida = __ENV.SALIDA || '/resultados/pico.json';
  return { [salida]: JSON.stringify(resumen, null, 2), stdout: textoResumen(resumen) };
}

function textoResumen(r) {
  const f = (x) => (x === null ? 'n/d' : x.toFixed(1));
  let s = `\n=== ASR1/RNF1 pico x${MULT} (N=${N} req/s, pico=${N * MULT} req/s) ===\n`;
  s += `peticiones=${r.peticiones} media=${f(r.tasa_media_req_s)} req/s descartadas=${r.iteraciones_descartadas} vus_max=${r.vus_max}\n`;
  s += `no-200=${f(r.fallos_no200_pct)}%  5xx/timeouts=${f(r.errores_5xx_pct)}%\n`;
  s += `p50=${f(r.latencia_ms.p50)} p95=${f(r.latencia_ms.p95)} p99=${f(r.latencia_ms.p99)} max=${f(r.latencia_ms.max)} ms\n`;
  for (const k of Object.keys(r.por_fase)) s += `  fase ${k}: p50=${f(r.por_fase[k].p50)} p95=${f(r.por_fase[k].p95)} p99=${f(r.por_fase[k].p99)}\n`;
  s += `estabilizacion=${r.estabilizacion_s === null ? 'NUNCA' : r.estabilizacion_s + ' s'} desde el fin de la rampa\n`;
  s += r.ventanas.map((x) => `${x.ventana}(${x.desde_s}s)=${f(x.p95)}`).join(' ') + '\n';
  return s;
}
