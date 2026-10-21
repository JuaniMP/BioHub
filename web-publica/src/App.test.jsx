import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App.jsx';

describe('App (web pública)', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('llama a /api/publica/ping sin token y muestra el servicio', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ servicio: 'servicio-consulta-publica', usuario: 'anonimo', roles: [], fecha: '2026-10-21T23:00:00Z' }),
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    expect(await screen.findByText('servicio-consulta-publica')).toBeInTheDocument();
    const [url, opciones] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/api\/publica\/ping$/);
    expect(opciones.headers.Authorization).toBeUndefined();
  });

  it('muestra un aviso si la API no responde', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 502 }));

    render(<App />);

    expect(await screen.findByRole('alert')).toHaveTextContent('502');
  });
});
