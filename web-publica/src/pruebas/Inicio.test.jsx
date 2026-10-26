import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { listado, mockFetch, renderizar, respuesta } from './ayudas.jsx';

describe('Inicio: búsqueda', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('carga la primera página y muestra tarjetas con un único enlace y el total', async () => {
    const f = mockFetch(() => respuesta(listado()));
    renderizar('/');
    expect(screen.getByText('Buscando especies…')).toBeInTheDocument();
    const lista = await screen.findByRole('list', { name: 'Resultados de la búsqueda' });
    const enlace = within(lista).getByRole('link');
    expect(enlace).toHaveAttribute('href', '/especies/3f2a9c1e-5b7d-4e8a-9f10-2c3d4e5f6a7b');
    expect(within(enlace).getByText('Atelopus ebenoides').tagName).toBe('I');
    expect(within(enlace).getByText('En peligro')).toBeInTheDocument();
    expect(within(enlace).getByText('Familia Bufonidae')).toBeInTheDocument();
    expect(within(enlace).getByText('Boyacá · Región Andina')).toBeInTheDocument();
    expect(screen.getByText('1 especie encontrada')).toBeInTheDocument();
    const url = new URL(f.mock.calls[0][0]);
    expect(url.pathname).toBe('/api/publica/fichas');
    expect(url.searchParams.get('page')).toBe('0');
    expect(url.searchParams.get('size')).toBe('20');
    expect(screen.getByText(/BioHub no publica ubicaciones exactas/)).toBeInTheDocument();
  });

  it('los filtros viajan a la URL y a la API (departamento por código) al buscar', async () => {
    const f = mockFetch(() => respuesta(listado()));
    const u = userEvent.setup();
    renderizar('/');
    await screen.findByText('1 especie encontrada');
    await u.type(screen.getByLabelText('Nombre científico o común'), 'atelopus');
    await u.type(screen.getByLabelText('Familia'), 'Bufonidae');
    await u.selectOptions(screen.getByLabelText('Departamento'), 'Boyacá');
    await u.click(screen.getByRole('button', { name: 'Buscar' }));
    await waitFor(() => expect(screen.getByTestId('url')).toHaveTextContent('/?q=atelopus&familia=Bufonidae&departamento=15'));
    await waitFor(() => expect(f.mock.calls.length).toBeGreaterThan(1));
    const ultima = new URL(f.mock.calls.at(-1)[0]);
    expect(ultima.searchParams.get('q')).toBe('atelopus');
    expect(ultima.searchParams.get('familia')).toBe('Bufonidae');
    expect(ultima.searchParams.get('departamento')).toBe('15');
  });

  it('lee los filtros y la página desde la URL', async () => {
    const f = mockFetch(() => respuesta(listado({ page: 2, totalPaginas: 5, total: 90 })));
    renderizar('/?q=rana&departamento=15&page=2');
    await screen.findByText('90 especies encontradas');
    const url = new URL(f.mock.calls[0][0]);
    expect(url.searchParams.get('q')).toBe('rana');
    expect(url.searchParams.get('page')).toBe('2');
    expect(screen.getByLabelText('Nombre científico o común')).toHaveValue('rana');
    expect(screen.getByLabelText('Departamento')).toHaveValue('15');
  });

  it('ofrece los 33 departamentos más la opción "Todos"', async () => {
    mockFetch(() => respuesta(listado()));
    renderizar('/');
    await screen.findByText('1 especie encontrada');
    expect(within(screen.getByLabelText('Departamento')).getAllByRole('option')).toHaveLength(34);
    expect(screen.getByRole('option', { name: 'Todos los departamentos' })).toBeInTheDocument();
  });

  it('valida q en el cliente: 1 letra no se envía y el foco queda en el campo', async () => {
    const f = mockFetch(() => respuesta(listado()));
    const u = userEvent.setup();
    renderizar('/');
    await screen.findByText('1 especie encontrada');
    const antes = f.mock.calls.length;
    const campo = screen.getByLabelText('Nombre científico o común');
    await u.type(campo, 'a');
    await u.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(screen.getByText('Escribe al menos 2 letras.')).toBeInTheDocument();
    expect(campo).toHaveAttribute('aria-invalid', 'true');
    expect(campo).toHaveFocus();
    expect(f.mock.calls.length).toBe(antes);
    expect(screen.getByTestId('url')).toHaveTextContent(/^\/$/);
  });

  it('valida q con más de 100 caracteres', async () => {
    mockFetch(() => respuesta(listado()));
    const u = userEvent.setup();
    renderizar('/');
    await screen.findByText('1 especie encontrada');
    await u.click(screen.getByLabelText('Nombre científico o común'));
    await u.paste('x'.repeat(101));
    await u.keyboard('{Enter}');
    expect(screen.getByText('Usa máximo 100 caracteres.')).toBeInTheDocument();
  });
});

describe('Inicio: paginación', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('Siguiente cambia la página en la URL (base 0) y pide esa página; Anterior vuelve', async () => {
    const f = mockFetch((url) => respuesta(listado({ page: Number(url.searchParams.get('page')), total: 45, totalPaginas: 3 })));
    const u = userEvent.setup();
    renderizar('/?q=rana');
    await screen.findByText('Página 1 de 3');
    const anterior = screen.getByRole('button', { name: 'Anterior' });
    expect(anterior).toHaveAttribute('aria-disabled', 'true');
    await u.click(screen.getByRole('button', { name: 'Siguiente' }));
    await screen.findByText('Página 2 de 3');
    expect(screen.getByTestId('url')).toHaveTextContent('/?q=rana&page=1');
    expect(new URL(f.mock.calls.at(-1)[0]).searchParams.get('page')).toBe('1');
    await waitFor(() => expect(screen.getByRole('heading', { name: '45 especies encontradas' })).toHaveFocus());
    await u.click(screen.getByRole('button', { name: 'Anterior' }));
    await screen.findByText('Página 1 de 3');
    expect(screen.getByTestId('url')).toHaveTextContent(/^\/\?q=rana$/);
  });

  it('en la última página, Siguiente no hace nada', async () => {
    const f = mockFetch(() => respuesta(listado({ page: 2, total: 45, totalPaginas: 3 })));
    const u = userEvent.setup();
    renderizar('/?page=2');
    await screen.findByText('Página 3 de 3');
    const n = f.mock.calls.length;
    await u.click(screen.getByRole('button', { name: 'Siguiente' }));
    expect(f.mock.calls.length).toBe(n);
  });

  it('no muestra paginación con una sola página', async () => {
    mockFetch(() => respuesta(listado()));
    renderizar('/');
    await screen.findByText('1 especie encontrada');
    expect(screen.queryByRole('navigation', { name: 'Paginación' })).toBeNull();
  });
});

describe('Inicio: estados', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('vacío: mensaje, sugerencia y Limpiar búsqueda que borra la URL', async () => {
    mockFetch(() => respuesta(listado({ content: [], total: 0, totalPaginas: 0 })));
    const u = userEvent.setup();
    renderizar('/?q=zzz&familia=Bufonidae');
    expect(await screen.findByRole('heading', { name: 'No se encontraron especies' })).toBeInTheDocument();
    expect(screen.getByText(/Revisa la ortografía o prueba con menos palabras\. Prueba quitando algún filtro\./)).toBeInTheDocument();
    await u.click(screen.getByRole('button', { name: 'Limpiar búsqueda' }));
    await waitFor(() => expect(screen.getByTestId('url')).toHaveTextContent(/^\/$/));
  });

  it('error de red: alerta con texto fijo y Reintentar que repite la petición', async () => {
    let falla = true;
    const f = mockFetch(() => {
      if (falla) throw new TypeError('network');
      return respuesta(listado());
    });
    const u = userEvent.setup();
    renderizar('/');
    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar la información, intenta de nuevo');
    falla = false;
    await u.click(screen.getByRole('button', { name: 'Reintentar' }));
    await screen.findByText('1 especie encontrada');
    expect(f.mock.calls.length).toBe(2);
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('503 usa el mismo texto genérico y no muestra detalle técnico', async () => {
    mockFetch(() => respuesta({ codigo: 'SERVICIO_NO_DISPONIBLE', detail: 'detalle interno' }, 503));
    renderizar('/');
    const alerta = await screen.findByRole('alert');
    expect(alerta).toHaveTextContent('No pudimos cargar la información, intenta de nuevo');
    expect(alerta).not.toHaveTextContent('503');
    expect(alerta).not.toHaveTextContent('detalle interno');
  });

  it('429: texto propio y Reintentar desactivado con cuenta regresiva', async () => {
    mockFetch(() => respuesta({ codigo: 'LIMITE_EXCEDIDO' }, 429, { 'Retry-After': '5' }));
    renderizar('/');
    expect(await screen.findByRole('alert')).toHaveTextContent('Hiciste muchas búsquedas seguidas. Espera un momento e intenta de nuevo.');
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeDisabled();
    expect(screen.getByText('Podrás reintentar en 10 s.')).toBeInTheDocument();
  });

  it('400 desde la URL: mensaje de búsqueda no válida y Limpiar búsqueda', async () => {
    mockFetch(() => respuesta({ codigo: 'PARAMETRO_INVALIDO' }, 400));
    renderizar('/?departamento=zz');
    expect(await screen.findByRole('alert')).toHaveTextContent('Algo en la búsqueda no es válido. Revisa lo que escribiste.');
    expect(screen.getByRole('button', { name: 'Limpiar búsqueda' })).toBeInTheDocument();
  });

  it('degradado: banner, filtros desactivados y se reactivan cuando el servicio se recupera', async () => {
    let degradado = true;
    mockFetch(() =>
      respuesta(listado({ modoDegradado: degradado, filtrosIgnorados: degradado ? ['familia', 'departamento'] : [] })),
    );
    const u = userEvent.setup();
    renderizar('/?familia=Bufonidae&departamento=15');
    await screen.findByText('1 especie encontrada');
    expect(screen.getAllByText(/Hay muchas visitas en este momento/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/Se ignoraron los filtros de familia y departamento\./)).toBeInTheDocument();
    expect(screen.getByLabelText('Familia')).toBeDisabled();
    expect(screen.getByLabelText('Departamento')).toBeDisabled();
    expect(screen.getByLabelText('Nombre científico o común')).toBeEnabled();
    degradado = false;
    await u.type(screen.getByLabelText('Nombre científico o común'), 'rana');
    await u.click(screen.getByRole('button', { name: 'Buscar' }));
    await waitFor(() => expect(screen.getByLabelText('Familia')).toBeEnabled());
    expect(screen.queryAllByText(/Hay muchas visitas/)).toHaveLength(0);
  });

  it('no pinta datos viejos: tras un error desaparecen los resultados anteriores', async () => {
    let falla = false;
    mockFetch(() => (falla ? respuesta({}, 500) : respuesta(listado())));
    const u = userEvent.setup();
    renderizar('/');
    await screen.findByText('1 especie encontrada');
    falla = true;
    await u.type(screen.getByLabelText('Nombre científico o común'), 'rana');
    await u.click(screen.getByRole('button', { name: 'Buscar' }));
    await screen.findByRole('alert');
    expect(screen.queryByText('Atelopus ebenoides')).toBeNull();
  });
});
