import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mockFetch, renderizar, respuesta, listado } from './pruebas/ayudas.jsx';

describe('App (rutas)', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('una ruta desconocida muestra el 404 de página con botón al buscador', async () => {
    mockFetch(() => respuesta(listado()));
    renderizar('/no-existe');
    expect(await screen.findByRole('heading', { level: 1, name: 'Esta página no existe' })).toBeInTheDocument();
    expect(screen.getByText('Revisa la dirección o vuelve al buscador.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir al buscador' })).toHaveAttribute('href', '/');
    expect(document.title).toBe('Página no encontrada · BioHub');
  });

  it('incluye enlace para saltar al contenido y un único main', async () => {
    mockFetch(() => respuesta(listado()));
    renderizar('/');
    expect(screen.getByRole('link', { name: 'Saltar al contenido' })).toHaveAttribute('href', '#contenido');
    expect(screen.getAllByRole('main')).toHaveLength(1);
    await screen.findByText('1 especie encontrada');
  });

  it('solo llama al gateway, sin token ni cookies', async () => {
    const f = mockFetch(() => respuesta(listado()));
    renderizar('/');
    await screen.findByText('1 especie encontrada');
    for (const [url, op] of f.mock.calls) {
      expect(url.startsWith('http://localhost:8080/api/publica/')).toBe(true);
      expect(op.headers.Authorization).toBeUndefined();
      expect(op.credentials).toBe('omit');
    }
  });
});
