import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

const sim = vi.hoisted(() => ({ fitBounds: vi.fn(), eventosTeselas: null, propsMapa: null, propsTeselas: null }));

vi.mock('react-leaflet', () => ({
  MapContainer: ({ children, ...p }) => {
    sim.propsMapa = p;
    return <div data-testid="mapa-leaflet">{children}</div>;
  },
  TileLayer: (p) => {
    sim.propsTeselas = p;
    sim.eventosTeselas = p.eventHandlers;
    return <div data-testid="teselas" />;
  },
  Marker: ({ position, eventHandlers, title }) => (
    <button type="button" data-testid="marcador" data-posicion={JSON.stringify(position)} onClick={() => eventHandlers.click({ target: { getElement: () => null } })}>
      {title}
    </button>
  ),
  useMap: () => ({ fitBounds: sim.fitBounds }),
}));

import { PaginaMapa } from '../index.js';
import { ALTO, ErrorHttp, INVESTIGADOR, clienteFalso, feature, mapa } from './ayudas.js';

afterEach(() => {
  cleanup();
  sim.fitBounds.mockClear();
});

describe('PaginaMapa: acceso por rol', () => {
  it.each([['investigador_campo'], ['auditor_cientifico'], ['analista_ambiental'], ['administrador']])(
    'rol %s: acceso denegado y NINGUNA llamada a la API ni mapa',
    async (rol) => {
      const cliente = clienteFalso(() => mapa([]));
      const alIrABusqueda = vi.fn();
      const u = userEvent.setup();
      render(<PaginaMapa cliente={cliente} roles={[rol]} alIrABusqueda={alIrABusqueda} />);
      expect(screen.getByRole('heading', { level: 1, name: 'No tienes permiso para ver esta información' })).toBeInTheDocument();
      expect(screen.getByText(/El mapa muestra coordenadas exactas y solo está disponible para el personal científico de alto rango/)).toBeInTheDocument();
      await u.click(screen.getByRole('button', { name: 'Ir a la búsqueda de hallazgos' }));
      expect(alIrABusqueda).toHaveBeenCalled();
      expect(cliente.get).not.toHaveBeenCalled();
      expect(screen.queryByTestId('mapa-leaflet')).toBeNull();
      expect(document.title).toBe('Acceso denegado · BioHub');
    },
  );

  it('sin roles también se deniega sin llamar a la API', () => {
    const cliente = clienteFalso(() => mapa([]));
    render(<PaginaMapa cliente={cliente} />);
    expect(cliente.get).not.toHaveBeenCalled();
    expect(screen.queryByRole('region', { name: 'Mapa de hallazgos' })).toBeNull();
  });

  it('403 del servidor: se destruye el mapa y se muestra acceso denegado', async () => {
    const cliente = clienteFalso(() => {
      throw new ErrorHttp(403, 'ROL_INSUFICIENTE');
    });
    render(<PaginaMapa cliente={cliente} roles={ALTO} />);
    expect(await screen.findByRole('heading', { name: 'No tienes permiso para ver esta información' })).toBeInTheDocument();
    expect(screen.queryByTestId('mapa-leaflet')).toBeNull();
  });
});

describe('PaginaMapa: alto rango', () => {
  it('convierte GeoJSON [lon, lat] a Leaflet [lat, lon]', async () => {
    const cliente = clienteFalso(() => mapa([feature('a', -73.1234567, 5.1234567), feature('b', -75.5, 6.25)]));
    render(<PaginaMapa cliente={cliente} roles={ALTO} />);
    const marcadores = await screen.findAllByTestId('marcador');
    expect(marcadores.map((m) => JSON.parse(m.dataset.posicion))).toEqual([
      [5.1234567, -73.1234567],
      [6.25, -75.5],
    ]);
    expect(cliente.get.mock.calls[0][0]).toBe('/api/cientifico/ubicacion/mapa');
    expect(screen.getByText('2 hallazgos en el mapa')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Mapa de hallazgos' })).toBeInTheDocument();
  });

  it('configura maxZoom 12 y la atribución visible de OpenStreetMap', async () => {
    const cliente = clienteFalso(() => mapa([feature('a', -73.1, 5.1)]));
    render(<PaginaMapa cliente={cliente} roles={ALTO} />);
    await screen.findByTestId('marcador');
    expect(sim.propsMapa.maxZoom).toBe(12);
    expect(sim.propsMapa.center).toEqual([4.5, -74.1]);
    expect(sim.propsTeselas.maxZoom).toBe(12);
    expect(sim.propsTeselas.attribution).toMatch(/OpenStreetMap<\/a> contributors/);
    expect(sim.propsTeselas.attribution).toContain('https://www.openstreetmap.org/copyright');
    expect(sim.propsTeselas.url).toBe('https://tile.openstreetmap.org/{z}/{x}/{y}.png');
    expect(sim.fitBounds).toHaveBeenCalledWith([[5.1, -73.1]], expect.objectContaining({ maxZoom: 12 }));
  });

  it('ignora features con coordenadas inválidas', async () => {
    const cliente = clienteFalso(() => mapa([feature('a', -73.1, 5.1), feature('x', 'a', null), feature('y', 500, 5)]));
    render(<PaginaMapa cliente={cliente} roles={ALTO} />);
    expect(await screen.findAllByTestId('marcador')).toHaveLength(1);
  });

  it('truncado: aviso permanente y contador "(hay más)"', async () => {
    const cliente = clienteFalso(() => mapa([feature('a', -73.1, 5.1)], { truncado: true, total: 1500 }));
    render(<PaginaMapa cliente={cliente} roles={ALTO} />);
    expect(await screen.findByText('Se muestran 1000 de los hallazgos que cumplen los filtros. Aplica más filtros para ver el resto.')).toBeInTheDocument();
    expect(screen.getByText('1000 hallazgos en el mapa (hay más)')).toBeInTheDocument();
    expect(screen.getAllByTestId('marcador')).toHaveLength(1);
  });

  it('sin truncado no aparece el aviso', async () => {
    const cliente = clienteFalso(() => mapa([feature('a', -73.1, 5.1)]));
    render(<PaginaMapa cliente={cliente} roles={ALTO} />);
    await screen.findByTestId('marcador');
    expect(screen.queryByText(/Se muestran 1000/)).toBeNull();
    expect(screen.getByText('1 hallazgo en el mapa')).toBeInTheDocument();
  });

  it('omitidos: aviso informativo', async () => {
    const cliente = clienteFalso(() => mapa([feature('a', -73.1, 5.1)], { omitidos: 2 }));
    render(<PaginaMapa cliente={cliente} roles={ALTO} />);
    expect(await screen.findByText('No se pudieron mostrar 2 hallazgos por un problema técnico. Avisa al equipo de soporte.')).toBeInTheDocument();
  });

  it('vacío: tarjeta con mensaje', async () => {
    const cliente = clienteFalso(() => mapa([]));
    render(<PaginaMapa cliente={cliente} roles={ALTO} />);
    expect(await screen.findByText('No hay hallazgos para los filtros seleccionados')).toBeInTheDocument();
    expect(screen.queryAllByTestId('marcador')).toHaveLength(0);
  });

  it('cargando: mensaje de estado y filtros desactivados', () => {
    const cliente = { get: vi.fn(() => new Promise(() => {})) };
    render(<PaginaMapa cliente={cliente} roles={ALTO} />);
    expect(screen.getByText('Cargando hallazgos…')).toBeInTheDocument();
    expect(screen.getByLabelText('Especie')).toBeDisabled();
  });

  it('error 500: alerta, se quitan los marcadores anteriores y Reintentar recupera', async () => {
    let falla = false;
    const cliente = clienteFalso(() => {
      if (falla) throw new ErrorHttp(500);
      return mapa([feature('a', -73.1, 5.1)]);
    });
    const u = userEvent.setup();
    render(<PaginaMapa cliente={cliente} roles={ALTO} />);
    await screen.findByTestId('marcador');
    falla = true;
    await u.click(screen.getByRole('button', { name: 'Aplicar filtros' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar el mapa. Intenta de nuevo.');
    expect(screen.queryAllByTestId('marcador')).toHaveLength(0);
    falla = false;
    await u.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByTestId('marcador')).toBeInTheDocument();
  });

  it('401: avisa y llama a alSesionVencida', async () => {
    const cliente = clienteFalso(() => {
      throw new ErrorHttp(401);
    });
    const alSesionVencida = vi.fn();
    render(<PaginaMapa cliente={cliente} roles={ALTO} alSesionVencida={alSesionVencida} />);
    expect(await screen.findByText('Tu sesión terminó. Te llevamos al inicio de sesión…')).toBeInTheDocument();
    expect(alSesionVencida).toHaveBeenCalledTimes(1);
  });

  it('aplica filtros del mapa (sin investigador) en la consulta', async () => {
    const cliente = clienteFalso(() => mapa([feature('a', -73.1, 5.1)]));
    const u = userEvent.setup();
    render(<PaginaMapa cliente={cliente} roles={ALTO} />);
    await screen.findByTestId('marcador');
    expect(screen.queryByLabelText('Investigador')).toBeNull();
    await u.type(screen.getByLabelText('Especie'), 'Atelopus');
    await u.selectOptions(screen.getByLabelText('Departamento'), '15');
    await u.click(screen.getByRole('button', { name: 'Aplicar filtros' }));
    await waitFor(() => expect(cliente.get.mock.calls.length).toBe(2));
    const url = new URL(cliente.get.mock.calls[1][0], 'http://x');
    expect(url.pathname).toBe('/api/cientifico/ubicacion/mapa');
    expect(Object.fromEntries(url.searchParams)).toEqual({ especie: 'Atelopus', departamento: '15' });
  });

  it('con hallazgoId pide solo ese hallazgo, abre su detalle y permite ver todos', async () => {
    const id = '3f2a9c1e-5b7d-4e8a-9f10-2c3d4e5f6a7b';
    const cliente = clienteFalso((ruta) => (ruta.includes('hallazgoId') ? mapa([feature(id, -73.1234567, 5.1234567)]) : mapa([feature(id, -73.1, 5.1), feature('b', -74, 4)])));
    const u = userEvent.setup();
    render(<PaginaMapa cliente={cliente} roles={ALTO} hallazgoId={id} />);
    const detalle = await screen.findByRole('complementary', { name: 'Detalle del hallazgo' });
    await within(detalle).findByText('5.12346');
    expect(cliente.get.mock.calls[0][0]).toBe(`/api/cientifico/ubicacion/mapa?hallazgoId=${id}`);
    await u.click(screen.getByRole('button', { name: 'Ver todos los hallazgos' }));
    await waitFor(() => expect(screen.getAllByTestId('marcador')).toHaveLength(2));
  });

  it('al elegir un marcador muestra el detalle con latitud y longitud como texto; Esc y Cerrar lo cierran', async () => {
    const cliente = clienteFalso(() => mapa([feature('a', -73.1234567, 5.1234567)]));
    const u = userEvent.setup();
    render(<PaginaMapa cliente={cliente} roles={ALTO} />);
    expect(await screen.findByText('Selecciona un marcador para ver el detalle.')).toBeInTheDocument();
    await u.click(await screen.findByTestId('marcador'));
    const detalle = screen.getByRole('complementary', { name: 'Detalle del hallazgo' });
    expect(within(detalle).getByText('5.12346')).toBeInTheDocument();
    expect(within(detalle).getByText('-73.12346')).toBeInTheDocument();
    expect(within(detalle).getByText('investigador1')).toBeInTheDocument();
    expect(within(detalle).getByText('14/03/2026')).toBeInTheDocument();
    await u.keyboard('{Escape}');
    expect(screen.getByText('Selecciona un marcador para ver el detalle.')).toBeInTheDocument();
    await u.click(screen.getByTestId('marcador'));
    await u.click(screen.getByRole('button', { name: 'Cerrar' }));
    expect(screen.getByText('Selecciona un marcador para ver el detalle.')).toBeInTheDocument();
  });

  it('no deja coordenadas en el título de la pestaña, ni en atributos de la página', async () => {
    const cliente = clienteFalso(() => mapa([feature('a', -73.1234567, 5.1234567)]));
    const u = userEvent.setup();
    const { container } = render(<PaginaMapa cliente={cliente} roles={ALTO} />);
    await u.click(await screen.findByTestId('marcador'));
    expect(document.title).toBe('Mapa de hallazgos · BioHub');
    expect(window.location.href).not.toMatch(/5\.12|73\.12/);
    const conAtributo = [...container.querySelectorAll('*')].filter((el) => [...el.attributes].some((a) => a.name !== 'data-posicion' && /5\.12|73\.12/.test(a.value)));
    expect(conAtributo).toHaveLength(0);
  });

  it('teselas caídas (más de 3 errores): aviso y cambio a la vista Lista con las coordenadas', async () => {
    const cliente = clienteFalso(() => mapa([feature('a', -73.1234567, 5.1234567)]));
    render(<PaginaMapa cliente={cliente} roles={ALTO} />);
    await screen.findByTestId('marcador');
    act(() => {
      for (let i = 0; i < 4; i++) sim.eventosTeselas.tileerror();
    });
    expect(await screen.findByText('No se pudo cargar el mapa base. Los hallazgos siguen disponibles en la vista Lista.')).toBeInTheDocument();
    expect(screen.queryByTestId('mapa-leaflet')).toBeNull();
    const tabla = screen.getByRole('table');
    expect(within(tabla).getByText('5.12346')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Lista' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('vista Lista alternativa: el enlace "Saltar el mapa" la activa y la fila abre el detalle', async () => {
    const cliente = clienteFalso(() => mapa([feature('a', -73.1234567, 5.1234567)]));
    const u = userEvent.setup();
    render(<PaginaMapa cliente={cliente} roles={ALTO} />);
    await screen.findByTestId('marcador');
    await u.click(screen.getByRole('link', { name: 'Saltar el mapa y ver la lista' }));
    await u.click(within(screen.getByRole('table')).getByRole('button', { name: 'Atelopus ebenoides' }));
    expect(within(screen.getByRole('complementary', { name: 'Detalle del hallazgo' })).getByText('Longitud')).toBeInTheDocument();
  });
});
