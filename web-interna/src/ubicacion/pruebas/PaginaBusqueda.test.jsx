import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PaginaBusqueda } from '../index.js';
import { ALTO, ErrorHttp, INVESTIGADOR, clienteFalso, filaAlto, filaProtegida, pagina } from './ayudas.js';

afterEach(cleanup);

describe('PaginaBusqueda: coordenadas según el rol', () => {
  it('alto rango ve las coordenadas, la columna Acción y "Ver en el mapa"', async () => {
    const cliente = clienteFalso(() => pagina([filaAlto()]));
    const alVerEnMapa = vi.fn();
    const u = userEvent.setup();
    render(<PaginaBusqueda cliente={cliente} roles={ALTO} alVerEnMapa={alVerEnMapa} />);
    const tabla = await screen.findByRole('table');
    expect(within(tabla).getByText('5.12346, -73.12346')).toBeInTheDocument();
    expect(within(tabla).getByRole('columnheader', { name: 'Acción' })).toBeInTheDocument();
    expect(within(tabla).queryByText('Restringido')).toBeNull();
    expect(screen.queryByText(/Tu rol no permite ver las coordenadas exactas/)).toBeNull();
    await u.click(within(tabla).getByRole('button', { name: /Ver en el mapa/ }));
    expect(alVerEnMapa).toHaveBeenCalledWith('3f2a9c1e-5b7d-4e8a-9f10-2c3d4e5f6a7b');
  });

  it('alto rango: "Sin coordenadas" y "No disponible" según el estado, y aviso de omitidos', async () => {
    const cliente = clienteFalso(() =>
      pagina(
        [
          filaAlto({ id: 'a', estadoCoordenadas: 'SIN_COORDENADAS', latitud: undefined, longitud: undefined }),
          filaAlto({ id: 'b', estadoCoordenadas: 'NO_DISPONIBLES', latitud: undefined, longitud: undefined }),
        ],
        { omitidos: 1 },
      ),
    );
    render(<PaginaBusqueda cliente={cliente} roles={ALTO} />);
    expect(await screen.findByText('Sin coordenadas')).toBeInTheDocument();
    expect(screen.getByText('No disponible')).toBeInTheDocument();
    expect(screen.getByText(/No se pudieron mostrar 1 hallazgos por un problema técnico/)).toBeInTheDocument();
  });

  it('otro rol ve "Restringido" y NO ve coordenadas aunque la API las mande por error', async () => {
    const cliente = clienteFalso(() =>
      pagina([filaAlto({ id: 'a' }), filaProtegida({ id: 'b', latitud: 4.60971, longitud: -74.08175, coordenadas: [1, 2] })]),
    );
    const { container } = render(<PaginaBusqueda cliente={cliente} roles={INVESTIGADOR} />);
    const tabla = await screen.findByRole('table');
    expect(within(tabla).getAllByText('Restringido')).toHaveLength(2);
    const html = container.innerHTML;
    for (const valor of ['5.12346', '73.12346', '5.1234567', '4.60971', '74.08175']) expect(html).not.toContain(valor);
    expect(within(tabla).queryByRole('columnheader', { name: 'Acción' })).toBeNull();
    expect(within(tabla).queryByRole('button', { name: /Ver en el mapa/ })).toBeNull();
    // la columna se mantiene para que se vea qué falta
    expect(within(tabla).getByRole('columnheader', { name: /Coordenadas/ })).toBeInTheDocument();
  });

  it('otro rol: aviso del rol siempre visible y "¿Por qué?" despliega la explicación', async () => {
    const cliente = clienteFalso(() => pagina([filaProtegida()]));
    const u = userEvent.setup();
    render(<PaginaBusqueda cliente={cliente} roles={['auditor_cientifico']} />);
    await screen.findByRole('table');
    const textoAviso = /Tu rol no permite ver las coordenadas exactas\. Solo el personal científico de alto rango puede verlas\./;
    expect(screen.getByText(textoAviso)).toBeVisible();
    const porque = screen.getByRole('button', { name: '¿Por qué?' });
    expect(porque).toHaveAttribute('aria-expanded', 'false');
    await u.click(porque);
    expect(porque).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getAllByText(textoAviso)).toHaveLength(2);
  });

  it('sin roles (vacío) se trata como rol sin permiso', async () => {
    const cliente = clienteFalso(() => pagina([filaAlto()]));
    const { container } = render(<PaginaBusqueda cliente={cliente} />);
    await screen.findByRole('table');
    expect(screen.getByText('Restringido')).toBeInTheDocument();
    expect(container.innerHTML).not.toContain('5.12346');
  });
});

describe('PaginaBusqueda: filtros, paginación y estados', () => {
  it('pide la primera página con 20 y envía los filtros a la API', async () => {
    const cliente = clienteFalso(() => pagina([filaProtegida()]));
    const u = userEvent.setup();
    render(<PaginaBusqueda cliente={cliente} roles={INVESTIGADOR} />);
    await screen.findByRole('table');
    expect(cliente.get.mock.calls[0][0]).toBe('/api/cientifico/ubicacion/hallazgos?page=0&size=20');
    await u.type(screen.getByLabelText('Especie'), ' Atelopus ');
    await u.type(screen.getByLabelText('Desde'), '2026-01-01');
    await u.type(screen.getByLabelText('Hasta'), '2026-06-30');
    await u.type(screen.getByLabelText('Investigador'), 'ana.ruiz');
    await u.selectOptions(screen.getByLabelText('Departamento'), 'Boyacá');
    await u.click(screen.getByRole('button', { name: 'Buscar' }));
    await waitFor(() => expect(cliente.get.mock.calls.length).toBeGreaterThan(1));
    const url = new URL(cliente.get.mock.calls.at(-1)[0], 'http://x');
    expect(Object.fromEntries(url.searchParams)).toEqual({
      especie: 'Atelopus', desde: '2026-01-01', hasta: '2026-06-30', investigador: 'ana.ruiz', departamento: '15', page: '0', size: '20',
    });
  });

  it('"Desde" posterior a "Hasta": mensaje bajo "Hasta", foco en el campo y no se llama a la API', async () => {
    const cliente = clienteFalso(() => pagina([filaProtegida()]));
    const u = userEvent.setup();
    render(<PaginaBusqueda cliente={cliente} roles={INVESTIGADOR} />);
    await screen.findByRole('table');
    const llamadas = cliente.get.mock.calls.length;
    await u.type(screen.getByLabelText('Desde'), '2026-06-30');
    await u.type(screen.getByLabelText('Hasta'), '2026-01-01');
    await u.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(screen.getByText('La fecha «Desde» no puede ser posterior a la fecha «Hasta».')).toBeInTheDocument();
    expect(screen.getByLabelText('Hasta')).toHaveFocus();
    expect(screen.getByLabelText('Hasta')).toHaveAttribute('aria-invalid', 'true');
    expect(cliente.get.mock.calls.length).toBe(llamadas);
  });

  it('valida longitud mínima de la especie', async () => {
    const cliente = clienteFalso(() => pagina([filaProtegida()]));
    const u = userEvent.setup();
    render(<PaginaBusqueda cliente={cliente} roles={INVESTIGADOR} />);
    await screen.findByRole('table');
    await u.type(screen.getByLabelText('Especie'), 'a');
    await u.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(screen.getByText('Escribe al menos 2 letras.')).toBeInTheDocument();
  });

  it('paginación base 0 y selector de tamaño', async () => {
    const cliente = clienteFalso((ruta) => {
      const p = Number(new URL(ruta, 'http://x').searchParams.get('page'));
      return pagina([filaProtegida()], { page: p, total: 48, totalPaginas: 3 });
    });
    const u = userEvent.setup();
    render(<PaginaBusqueda cliente={cliente} roles={INVESTIGADOR} />);
    expect(await screen.findByText('48 hallazgos')).toBeInTheDocument();
    expect(screen.getByText('Página 1 de 3')).toBeInTheDocument();
    await u.click(screen.getByRole('button', { name: 'Siguiente' }));
    await screen.findByText('Página 2 de 3');
    expect(cliente.get.mock.calls.at(-1)[0]).toContain('page=1');
    await u.selectOptions(screen.getByLabelText('Mostrar'), '50');
    await waitFor(() => expect(cliente.get.mock.calls.at(-1)[0]).toContain('size=50'));
    expect(cliente.get.mock.calls.at(-1)[0]).toContain('page=0');
  });

  it('cargando: mensaje y esqueleto', () => {
    const cliente = { get: vi.fn(() => new Promise(() => {})) };
    render(<PaginaBusqueda cliente={cliente} roles={INVESTIGADOR} />);
    expect(screen.getByText('Buscando hallazgos…')).toBeInTheDocument();
    expect(screen.getAllByTestId('ub-esqueleto')).toHaveLength(5);
  });

  it('vacío: mensaje y sugerencia', async () => {
    const cliente = clienteFalso(() => pagina([], { total: 0, totalPaginas: 0 }));
    render(<PaginaBusqueda cliente={cliente} roles={INVESTIGADOR} />);
    expect(await screen.findByText('No hay hallazgos que coincidan con los filtros')).toBeInTheDocument();
    expect(screen.getByText('Prueba con menos filtros o con otro rango de fechas.')).toBeInTheDocument();
  });

  it('error 500: alerta con Reintentar que repite la llamada', async () => {
    let falla = true;
    const cliente = clienteFalso(() => {
      if (falla) throw new ErrorHttp(500);
      return pagina([filaProtegida()]);
    });
    const u = userEvent.setup();
    render(<PaginaBusqueda cliente={cliente} roles={INVESTIGADOR} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar los hallazgos. Intenta de nuevo.');
    falla = false;
    await u.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('table')).toBeInTheDocument();
  });

  it('401: se descarta la tabla, se avisa y se llama a alSesionVencida', async () => {
    const cliente = clienteFalso(() => {
      throw new ErrorHttp(401);
    });
    const alSesionVencida = vi.fn();
    render(<PaginaBusqueda cliente={cliente} roles={ALTO} alSesionVencida={alSesionVencida} />);
    expect(await screen.findByText('Tu sesión terminó. Te llevamos al inicio de sesión…')).toBeInTheDocument();
    expect(alSesionVencida).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('403: pantalla de acceso denegado', async () => {
    const cliente = clienteFalso(() => {
      throw new ErrorHttp(403, 'ROL_INSUFICIENTE');
    });
    render(<PaginaBusqueda cliente={cliente} roles={INVESTIGADOR} />);
    expect(await screen.findByRole('heading', { name: 'No tienes permiso para ver esta información' })).toBeInTheDocument();
  });
});
