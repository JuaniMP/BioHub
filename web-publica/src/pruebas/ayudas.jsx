import { render } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router';
import { vi } from 'vitest';
import App from '../App.jsx';

export function UbicacionActual() {
  const l = useLocation();
  return <output data-testid="url">{l.pathname + l.search}</output>;
}

export function renderizar(ruta = '/') {
  return render(
    <MemoryRouter initialEntries={[ruta]}>
      <App />
      <UbicacionActual />
    </MemoryRouter>,
  );
}

export const FICHA = {
  id: '3f2a9c1e-5b7d-4e8a-9f10-2c3d4e5f6a7b',
  nombreCientifico: 'Atelopus ebenoides',
  nombreComun: 'Rana arlequín de Boyacá',
  familia: 'Bufonidae',
  estadoConservacion: 'EN_PELIGRO',
  departamento: 'Boyacá',
  departamentoCodigo: '15',
  region: 'Andina',
};

export function listado(extra = {}) {
  return { content: [FICHA], page: 0, size: 20, total: 1, totalPaginas: 1, modoDegradado: false, filtrosIgnorados: [], ...extra };
}

export const respuesta = (cuerpo, status = 200, cabeceras = {}) => ({
  ok: status >= 200 && status < 300,
  status,
  headers: { get: (n) => cabeceras[n] ?? null },
  json: () => Promise.resolve(cuerpo),
});

/** Sustituye fetch por un mock que responde según la URL. */
export function mockFetch(manejador) {
  const f = vi.fn((url, opciones) => Promise.resolve(manejador(new URL(url), opciones)));
  vi.stubGlobal('fetch', f);
  return f;
}
