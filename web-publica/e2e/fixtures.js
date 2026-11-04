import { test as base, expect } from '@playwright/test';

/** Datos con la forma de 04-contrato-api.md §4. Sin coordenadas, como en el contrato (D14). */
export const ID_PELIGRO = '3f2a9c1e-5b7d-4e8a-9f10-2c3d4e5f6a7b';
export const ID_COMUN = '8a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d';

export const FICHA_PELIGRO = {
  id: ID_PELIGRO,
  nombreCientifico: 'Atelopus ebenoides',
  nombreComun: 'Rana arlequín de Boyacá',
  familia: 'Bufonidae',
  tipoEspecimen: 'Adulto',
  fechaRecoleccion: '2026-03-14',
  estadoConservacion: 'EN_PELIGRO',
  departamento: 'Boyacá',
  departamentoCodigo: '15',
  region: 'Andina',
  descripcion: 'Anfibio endémico de páramo.',
  ubicacionProtegida: true,
};
export const FICHA_COMUN = {
  ...FICHA_PELIGRO,
  id: ID_COMUN,
  nombreCientifico: 'Dendropsophus columbianus',
  nombreComun: 'Rana de árbol',
  estadoConservacion: 'PREOCUPACION_MENOR',
  departamento: 'Antioquia',
  departamentoCodigo: '05',
};

const resumen = ({ tipoEspecimen, fechaRecoleccion, descripcion, ubicacionProtegida, ...r }) => r;

export function pagina(fichas) {
  return {
    content: fichas.map(resumen),
    page: 0,
    size: 20,
    total: fichas.length,
    totalPaginas: fichas.length ? 1 : 0,
    modoDegradado: false,
    filtrosIgnorados: [],
  };
}

export function problema(status, codigo, detail, instance) {
  return { type: 'about:blank', title: 'Error', status, detail, instance, codigo };
}

const json = (status, cuerpo) => ({ status, contentType: 'application/json', body: JSON.stringify(cuerpo) });

/**
 * Simula el gateway. `modo`: 'normal' | 'vacio' | 'error503'.
 * Devuelve las URLs pedidas para que las pruebas comprueben qué se consultó.
 */
export async function simularApi(page, { modo = 'normal' } = {}) {
  const pedidas = [];
  await page.route('**/api/publica/**', async (route) => {
    const url = new URL(route.request().url());
    pedidas.push(url.pathname + url.search);
    if (modo === 'error503') {
      return route.fulfill({
        ...json(503, problema(503, 'SERVICIO_NO_DISPONIBLE', 'El servicio no está disponible en este momento. Intenta de nuevo.', url.pathname)),
        headers: { 'Cache-Control': 'no-store' },
      });
    }
    const m = url.pathname.match(/\/api\/publica\/fichas\/([^/]+)$/);
    if (m) {
      const ficha = [FICHA_PELIGRO, FICHA_COMUN].find((f) => f.id === m[1]);
      return route.fulfill(ficha ? json(200, ficha) : json(404, problema(404, 'NO_ENCONTRADO', 'La ficha solicitada no existe.', url.pathname)));
    }
    if (url.pathname.endsWith('/api/publica/fichas')) {
      if (modo === 'vacio') return route.fulfill(json(200, pagina([])));
      const q = (url.searchParams.get('q') ?? '').toLowerCase();
      const todas = [FICHA_PELIGRO, FICHA_COMUN];
      const f = q ? todas.filter((x) => `${x.nombreCientifico} ${x.nombreComun}`.toLowerCase().includes(q)) : todas;
      return route.fulfill(json(200, pagina(f)));
    }
    return route.fulfill(json(404, problema(404, 'NO_ENCONTRADO', 'No existe.', url.pathname)));
  });
  return pedidas;
}

/** Recoge errores de CSP y de consola/página (SEC-J-07). */
export const test = base.extend({
  erroresCsp: async ({ page }, use) => {
    const errores = [];
    page.on('console', (m) => {
      const t = m.text();
      if (/content security policy|violates the following|Refused to/i.test(t)) errores.push(t);
    });
    page.on('pageerror', (e) => errores.push(`pageerror: ${e.message}`));
    await use(errores);
  },
});
export { expect };
