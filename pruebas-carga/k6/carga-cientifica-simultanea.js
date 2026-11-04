// ASR4 / RNF5: la consulta pública responde <= 2 s (p95) MIENTRAS el lado científico escribe.
//
// Qué representa la "carga científica": ráfagas de eventos hallazgo.registrado / hallazgo.reclasificado publicados al
// broker (exchange topic compartido en estructura con biohub.eventos) y consumidos por consulta-publica, que por cada
// evento hace SELECT ... FOR UPDATE + INSERT/UPDATE en ficha_publica (+ invalidación de la ficha y de TODAS las búsquedas
// en Redis). Es exactamente el efecto que el registro de Hernando tendrá sobre la consulta pública (CQRS, SAD §4.7).
// Qué NO representa: las escrituras en la BD `cientifico` (cifrado AES-GCM, historial, adjuntos), ni la carga HTTP de
// servicio-cientifico, ni el publicador real (no existe: ARQ-01/ARQ-07). Los eventos se publican por la API HTTP de
// management de RabbitMQ a un exchange temporal `biohub.eventos.carga` (topic) enlazado con `hallazgo.#` a la cola PROPIA
// de la carga (consulta-publica.carga.eventos, la que consumen las réplicas de compose-carga.yml), para no ensuciar la
// cola `reportes.eventos` ni la cola/DLQ de desarrollo. El exchange se borra en teardown().
//
// Contrato del evento (04-contrato-api.md §7, ARQ-13): consulta-publica verifica el `user_id` AMQP. Aquí se publica con
// user_id = RMQ_USER (RabbitMQ solo acepta un user_id igual al usuario autenticado) y compose-carga.yml fija
// BIOHUB_EVENTOS_PUBLICADOR_CIENTIFICO = RABBITMQ_USER, SIN desactivar la verificación. `version` va entre 1 y
// VERSION_MAXIMA (1.000.000) y crece despacio por hallazgo (salto < 100) para no disparar las alertas de salto anómalo.
// Al terminar se comprueba que la DLQ de carga esté vacía (umbral dlq_mensajes < 1): si no, la prueba falla.
//
// Ejecutar (desde pruebas-carga/), dos veces para comparar:
//   ./correr-k6.sh k6/carga-cientifica-simultanea.js -e CIENTIFICA=0 -e SALIDA=/resultados/asr4-sin.json
//   ./correr-k6.sh k6/carga-cientifica-simultanea.js -e CIENTIFICA=1 -e SALIDA=/resultados/asr4-con.json
// Variables: BASE_URL, R (req/s públicas, def. 300), EV (eventos/s, def. 30), DUR (def. 120s), CIENTIFICA (0|1),
//            RMQ_URL (def. http://127.0.0.1:15672), COLA (def. consulta-publica.carga.eventos),
//            RMQ_USER, RMQ_PASS (obligatorias si CIENTIFICA=1; correr-k6.sh las pasa por entorno, no por argumento).
import http from 'k6/http';
import encoding from 'k6/encoding';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';
import { mezcla, ID, aSegundos } from './lib/comun.js';

const R = parseInt(__ENV.R || '300');
const EV = parseInt(__ENV.EV || '30');
const DUR = __ENV.DUR || '120s';
const CIENTIFICA = (__ENV.CIENTIFICA || '0') === '1';
const RMQ = __ENV.RMQ_URL || 'http://127.0.0.1:15672';
const EXCHANGE = 'biohub.eventos.carga';
const COLA = __ENV.COLA || 'consulta-publica.carga.eventos';
const DLQ = `${COLA}.dlq`;
const VERSION_MAXIMA = 1000000;   // DatosHallazgo.VERSION_MAXIMA de consulta-publica
const AUTH = { Authorization: 'Basic ' + encoding.b64encode(`${__ENV.RMQ_USER}:${__ENV.RMQ_PASS}`), 'Content-Type': 'application/json' };

const eventosOk = new Rate('eventos_publicados_ok');
const eventosTotal = new Counter('eventos_publicados');
const cola_final = new Trend('cola_mensajes_pendientes_al_final');
const dlq_final = new Trend('dlq_mensajes');
const acked = new Trend('eventos_procesados_ack');

const scenarios = {
  publica: {
    executor: 'constant-arrival-rate', rate: R, timeUnit: '1s', duration: DUR,
    preAllocatedVUs: Math.min(300, R), maxVUs: 2000, exec: 'publica',
  },
};
if (CIENTIFICA) {
  scenarios.cientifica = {
    executor: 'constant-arrival-rate', rate: EV, timeUnit: '1s', duration: DUR,
    preAllocatedVUs: 20, maxVUs: 200, exec: 'cientifica',
  };
}

export const options = {
  scenarios,
  thresholds: {
    'http_req_duration{escenario:publica}': ['p(95)<2000'],   // RNF5 / HU-06 c.3
    'http_req_failed{escenario:publica}': ['rate<0.05'],
    errores_5xx: ['rate<0.05'],
    'http_reqs{escenario:publica}': ['count>0'],   // fuerza a k6 a reportar el conteo de peticiones públicas
    ...(CIENTIFICA ? { eventos_publicados_ok: ['rate>0.99'], dlq_mensajes: ['max<1'] } : {}),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  discardResponseBodies: false,
};

export function setup() {
  if (!CIENTIFICA) return {};
  if (!__ENV.RMQ_USER || !__ENV.RMQ_PASS) throw new Error('Faltan RMQ_USER / RMQ_PASS');
  // La cola de carga la declara consulta-publica al arrancar: se espera a verla y se parte de una DLQ vacía
  for (let i = 0; i < 30 && http.get(`${RMQ}/api/queues/%2F/${COLA}`, { headers: AUTH }).status !== 200; i++) sleep(1);
  http.del(`${RMQ}/api/queues/%2F/${DLQ}/contents`, null, { headers: AUTH });
  let r = http.put(`${RMQ}/api/exchanges/%2F/${EXCHANGE}`, JSON.stringify({ type: 'topic', durable: false, auto_delete: false }), { headers: AUTH });
  if (r.status !== 201 && r.status !== 204) throw new Error(`No se pudo crear el exchange temporal: ${r.status}`);
  r = http.post(`${RMQ}/api/bindings/%2F/e/${EXCHANGE}/q/${COLA}`, JSON.stringify({ routing_key: 'hallazgo.#' }), { headers: AUTH });
  if (r.status !== 201) throw new Error(`No se pudo enlazar a ${COLA}: ${r.status} (¿arrancó consulta-publica y declaró la cola?)`);
  const q0 = JSON.parse(http.get(`${RMQ}/api/queues/%2F/${COLA}`, { headers: AUTH }).body);
  return { ack0: (q0.message_stats && q0.message_stats.ack) || 0 };   // los contadores de la cola son acumulados
}

export function publica() {
  mezcla({ escenario: 'publica' });
}

const DEPTOS = ['05', '11', '15', '25', '41', '52', '68', '76'];
const ESTADOS = ['EN_PELIGRO', 'NO_AMENAZADA', 'SIN_EVALUAR'];
const rnd = (n) => Math.floor(Math.random() * n);

const hex = (n) => Array.from({ length: n }, () => Math.floor(Math.random() * 16).toString(16)).join('');
const uuidAleatorio = () => `${hex(8)}-${hex(4)}-4${hex(3)}-8${hex(3)}-${hex(12)}`;

export function cientifica() {
  const i = exec.scenario.iterationInTest;
  const u = Math.random();
  let hallazgoId, tipo, version;
  if (u < 0.2) {               // hallazgo nuevo (INSERT): ids 3001..., fuera de la semilla pero con el mismo prefijo de limpieza
    hallazgoId = ID(3001 + rnd(5000)); tipo = 'hallazgo.registrado'; version = 1 + rnd(3);
  } else {                     // reclasificación de uno existente (UPDATE); 10 % sobre las fichas virales para invalidar caché caliente
    hallazgoId = u < 0.3 ? ID(1 + rnd(5)) : ID(1 + rnd(2000)); tipo = 'hallazgo.reclasificado';
    // Crece 1 cada 4 s desde el inicio de la corrida (la semilla parte en versión 1): casi siempre "gana la versión
    // mayor" y el salto por hallazgo es pequeño. Acotada a VERSION_MAXIMA.
    version = Math.min(VERSION_MAXIMA, 2 + Math.floor(exec.instance.currentTestRunDuration / 4000));
  }
  const sobre = {
    id: uuidAleatorio(),
    tipo, origen: 'servicio-cientifico', fecha: new Date().toISOString(), usuario: 'carga-k6',
    datos: {
      hallazgoId, version, nombreCientifico: `Pristimantis carga${(i % 9999).toString().padStart(4, '0')}`,
      nombreComun: 'Rana de carga', familia: 'Strabomantidae', tipoEspecimen: 'Adulto',
      fechaRecoleccion: '2026-03-14', estadoConservacion: ESTADOS[rnd(3)], departamento: DEPTOS[rnd(DEPTOS.length)],
      descripcion: `Evento de carga ${i}`,
    },
  };
  const r = http.post(`${RMQ}/api/exchanges/%2F/${EXCHANGE}/publish`, JSON.stringify({
    properties: { delivery_mode: 2, content_type: 'application/json', message_id: sobre.id, user_id: __ENV.RMQ_USER },
    routing_key: tipo, payload: JSON.stringify(sobre), payload_encoding: 'string',
  }), { headers: AUTH, tags: { escenario: 'cientifica' }, timeout: '10s' });
  eventosOk.add(r.status === 200 && JSON.parse(r.body).routed === true);
  eventosTotal.add(1);
}

export function teardown(datos) {
  if (!CIENTIFICA) return;
  // Deja que el consumidor drene la cola (hasta 30 s) y que el management refresque sus contadores (cada ~5 s)
  for (let i = 0; i < 30; i++) {
    const p = http.get(`${RMQ}/api/queues/%2F/${COLA}`, { headers: AUTH });
    if (p.status === 200 && JSON.parse(p.body).messages === 0) break;
    sleep(1);
  }
  sleep(6);
  const q = http.get(`${RMQ}/api/queues/%2F/${COLA}`, { headers: AUTH });
  if (q.status === 200) {
    const cuerpo = JSON.parse(q.body);
    cola_final.add(cuerpo.messages);
    acked.add(((cuerpo.message_stats && cuerpo.message_stats.ack) || 0) - ((datos && datos.ack0) || 0));
  }
  const d = http.get(`${RMQ}/api/queues/%2F/${DLQ}`, { headers: AUTH });
  dlq_final.add(d.status === 200 ? JSON.parse(d.body).messages : 1);   // si no se puede leer la DLQ, la prueba falla
  http.del(`${RMQ}/api/exchanges/%2F/${EXCHANGE}`, null, { headers: AUTH });
}

export function handleSummary(data) {
  const m = data.metrics;
  const v = (n, s) => (m[n] && m[n].values && m[n].values[s] !== undefined ? m[n].values[s] : null);
  const k = 'http_req_duration{escenario:publica}';
  const resumen = {
    parametros: { R, EV: CIENTIFICA ? EV : 0, DUR, CIENTIFICA },
    publica: {
      peticiones: v('http_reqs{escenario:publica}', 'count'),
      p50: v(k, 'med'), p90: v(k, 'p(90)'), p95: v(k, 'p(95)'), p99: v(k, 'p(99)'), max: v(k, 'max'),
      fallos_no200_pct: (v('http_req_failed{escenario:publica}', 'rate') || 0) * 100,
      errores_5xx_pct: (v('errores_5xx', 'rate') || 0) * 100,
    },
    cientifica: CIENTIFICA ? {
      eventos_publicados: v('eventos_publicados', 'count'),
      publicados_ok_pct: (v('eventos_publicados_ok', 'rate') || 0) * 100,
      cola_pendiente_tras_drenar: v('cola_mensajes_pendientes_al_final', 'max'),
      eventos_procesados_ack: v('eventos_procesados_ack', 'max'),
      dlq_mensajes: v('dlq_mensajes', 'max'),
    } : null,
    iteraciones_descartadas: v('dropped_iterations', 'count') || 0,
  };
  const f = (x) => (x === null || x === undefined ? 'n/d' : Number(x).toFixed(1));
  const txt = `\n=== ASR4/RNF5 ${CIENTIFICA ? 'CON' : 'SIN'} carga científica (R=${R} req/s${CIENTIFICA ? `, ${EV} eventos/s` : ''}, ${DUR}) ===\n` +
    `públicas=${resumen.publica.peticiones} p50=${f(resumen.publica.p50)} p95=${f(resumen.publica.p95)} p99=${f(resumen.publica.p99)} max=${f(resumen.publica.max)} ms ` +
    `no200=${f(resumen.publica.fallos_no200_pct)}% 5xx=${f(resumen.publica.errores_5xx_pct)}% descartadas=${resumen.iteraciones_descartadas}\n` +
    (CIENTIFICA ? `eventos=${resumen.cientifica.eventos_publicados} ok=${f(resumen.cientifica.publicados_ok_pct)}% procesados=${f(resumen.cientifica.eventos_procesados_ack)} cola_pendiente=${f(resumen.cientifica.cola_pendiente_tras_drenar)} dlq=${f(resumen.cientifica.dlq_mensajes)}\n` : '');
  return { [__ENV.SALIDA || '/resultados/asr4.json']: JSON.stringify(resumen, null, 2), stdout: txt };
}
