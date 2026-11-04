// Utilidades compartidas por los scripts de carga (k6). Sin secretos: todo llega por variables de entorno (-e).
import http from 'k6/http';
import { Rate, Counter } from 'k6/metrics';

export const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:18080';

// Ids de la semilla datos/semilla-fichas.sql: c0a9a000-0000-4000-8000-<12 hex>, 1..POOL
export const POOL = parseInt(__ENV.POOL || '2000');
export const ID = (i) => `c0a9a000-0000-4000-8000-${i.toString(16).padStart(12, '0')}`;
// Fichas "virales": las mismas pocas fichas piden casi todos (ejercitan la caché Redis).
export const VIRALES = [1, 2, 3, 4, 5].map(ID);

const GENEROS = ['atelopus', 'pristimantis', 'rhinella', 'boana', 'dendropsophus', 'leptodactylus', 'hyloxalus',
  'oophaga', 'espeletia', 'cattleya', 'puya', 'passiflora', 'tangara', 'anolis', 'bothrops', 'micrurus',
  'panthera', 'tremarctos', 'quercus', 'podocarpus'];
const FAMILIAS = ['Bufonidae', 'Hylidae', 'Dendrobatidae', 'Felidae', 'Viperidae', 'Orchidaceae'];
const DEPTOS = ['05', '11', '15', '25', '41', '52', '68', '76'];
const POPULARES = ['atelopus', 'pristimantis', 'rhinella'];

const rnd = (n) => Math.floor(Math.random() * n);
const pick = (a) => a[rnd(a.length)];

export const errores5xx = new Rate('errores_5xx');         // 5xx o sin respuesta (timeout/conexión): lo que mide el RNF1
export const respuestas = new Counter('respuestas_total');

// Todo lo que no sea 200 cuenta como fallo en http_req_failed (no se espera 4xx en esta carga).
http.setResponseCallback(http.expectedStatuses(200));

// Ficha: 85 % virales (caché caliente), 15 % cualquiera de las POOL (primer acceso = miss en Redis -> MySQL).
export function pedirFicha(etiquetas) {
  const id = Math.random() < 0.85 ? pick(VIRALES) : ID(1 + rnd(POOL));
  return registrar(http.get(`${BASE_URL}/api/publica/fichas/${id}`, { tags: { ...etiquetas, tipo: 'ficha', name: 'GET /api/publica/fichas/{id}' }, timeout: '10s' }));
}

// Búsqueda: 45 % consultas populares idénticas (caché de listados, TTL 60 s); 55 % combinaciones variadas
// (q + familia/departamento + página + tamaño) que casi siempre fallan la caché y hacen FULLTEXT en MySQL.
export function pedirBusqueda(etiquetas) {
  let qs;
  if (Math.random() < 0.45) {
    qs = `q=${pick(POPULARES)}&page=0&size=20`;
  } else {
    qs = `q=${pick(GENEROS)}&page=${rnd(5)}&size=${pick([10, 20, 30])}`;
    if (Math.random() < 0.3) qs += `&familia=${pick(FAMILIAS)}`;
    if (Math.random() < 0.3) qs += `&departamento=${pick(DEPTOS)}`;
  }
  return registrar(http.get(`${BASE_URL}/api/publica/fichas?${qs}`, { tags: { ...etiquetas, tipo: 'busqueda', name: 'GET /api/publica/fichas?...' }, timeout: '10s' }));
}

function registrar(res) {
  errores5xx.add(res.status === 0 || res.status >= 500);
  respuestas.add(1);
  return res;
}

export function mezcla(etiquetas) {
  return Math.random() < 0.6 ? pedirFicha(etiquetas) : pedirBusqueda(etiquetas);
}

// Ventanas de 15 s para medir p95 en el tiempo (estabilización). Devuelve 'w00', 'w01', ...
export const VENTANA_S = 15;
export const ventana = (msDesdeInicio) => 'w' + String(Math.floor(msDesdeInicio / 1000 / VENTANA_S)).padStart(2, '0');

export function aSegundos(txt) {
  const m = /^(\d+)(s|m)$/.exec(txt);
  return parseInt(m[1]) * (m[2] === 'm' ? 60 : 1);
}
