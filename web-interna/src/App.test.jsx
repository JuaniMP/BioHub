import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.jsx';

vi.mock('./auth/keycloak.js', () => ({
  keycloak: { tokenParsed: { preferred_username: 'investigador1' } },
  iniciarSesion: vi.fn(() => Promise.resolve(true)),
  cerrarSesion: vi.fn(),
  rolesUsuario: () => ['investigador_campo'],
}));

vi.mock('./api/cliente.js', () => ({ getAutenticado: vi.fn() }));

import { getAutenticado } from './api/cliente.js';

describe('App (web interna)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('tras iniciar sesión muestra el ping del Servicio Científico', async () => {
    getAutenticado.mockResolvedValue({
      servicio: 'servicio-cientifico',
      usuario: 'investigador1',
      roles: ['investigador_campo'],
      fecha: '2026-10-21T23:00:00Z',
    });

    render(<App />);

    expect(await screen.findByText('servicio-cientifico')).toBeInTheDocument();
    expect(getAutenticado).toHaveBeenCalledWith('/api/cientifico/ping');
    expect(screen.getByText('investigador_campo')).toBeInTheDocument();
  });

  it('muestra un error si el gateway no responde', async () => {
    getAutenticado.mockRejectedValue(new Error('El servidor respondió 502'));

    render(<App />);

    expect(await screen.findByRole('alert')).toHaveTextContent('502');
  });
});
