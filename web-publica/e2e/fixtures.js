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

/**
 * Se ejecuta en el navegador antes que cualquier script de la página: reenvía cada violación de CSP
 * (evento `securitypolicyviolation`) con sus detalles estructurados a la función `__biohubCsp`.
 * `estiloInternoNoAtribuido` indica que el elemento afectado NO tiene atributo `style` en el DOM.
 */
function vigilarCsp() {
  document.addEventListener('securitypolicyviolation', (e) => {
    const el = e.target instanceof Element ? e.target : null;
    window.__biohubCsp({
      directiva: e.violatedDirective,
      bloqueado: e.blockedURI,
      origen: e.sourceFile,
      linea: e.lineNumber,
      muestra: e.sample,
      elemento: el ? el.tagName : null,
      estiloInternoNoAtribuido: el ? !el.hasAttribute('style') : false,
    });
  });
}

/**
 * Artefacto conocido de WebKit (no de la app): al crear el menú de un <select> nativo, WebKit aplica un
 * estilo interno que el motor trata como `style` en línea y lo reporta como violación de `style-src-attr`
 * cuando la CSP es `style-src 'self'`. Se reproduce con un <select> estático de HTML puro, sin JS ni
 * atributo `style` (Playwright 1.64.1 / WebKit, imagen mcr.microsoft.com/playwright:v1.64.1-noble); en
 * Chromium y Firefox no ocurre. La app no tiene estilos en línea (src/ sin <style>, `style=` ni `style={`)
 * y añadir 'unsafe-inline' a style-src queda descartado por SEC-J-07.
 * Solo se ignora ESA combinación exacta: directiva style-src-attr, recurso "inline", elemento <select>
 * y sin atributo `style` en el DOM. Un `<select style="...">`, un `<style>` o un `style=` en cualquier
 * otro elemento (violación real de la app) siguen reportándose.
 */
function esArtefactoSelectWebKit(v) {
  return v.directiva === 'style-src-attr'
    && v.bloqueado === 'inline'
    && v.elemento === 'SELECT'
    && v.estiloInternoNoAtribuido === true;
}

/**
 * Recoge violaciones de CSP y errores de página (SEC-J-07). Las violaciones se toman del evento
 * `securitypolicyviolation` (con directiva, recurso, elemento y origen) en vez del texto de consola, que
 * en WebKit es genérico ("Refused to apply a stylesheet...", línea 0) y no permite distinguir su causa.
 */
export const test = base.extend({
  erroresCsp: async ({ page }, use) => {
    const errores = [];
    await page.exposeFunction('__biohubCsp', (v) => {
      if (esArtefactoSelectWebKit(v)) return;
      errores.push(`CSP ${v.directiva} bloqueó ${v.bloqueado} en <${v.elemento}> (${v.origen}:${v.linea}) ${v.muestra}`.trim());
    });
    await page.addInitScript(vigilarCsp);
    page.on('pageerror', (e) => errores.push(`pageerror: ${e.message}`));
    await use(errores);
  },
});
export { expect };
