import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { aFichaPublica } from '../api/fichas.js';
import { FICHA, listado, mockFetch, renderizar, respuesta } from './ayudas.jsx';

const ID = FICHA.id;
const COMPLETA = {
  ...FICHA,
  tipoEspecimen: 'Adulto',
  fechaRecoleccion: '2026-03-15',
  descripcion: 'Anfibio endémico de páramo.',
  ubicacionProtegida: true,
};
const AVISO = /Por seguridad, BioHub no publica el lugar exacto donde se encontró esta especie\./;

describe('Ficha de especie', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('muestra los datos, el título de pestaña y el foco en el h1', async () => {
    mockFetch(() => respuesta(COMPLETA));
    renderizar(`/especies/${ID}`);
    const h1 = await screen.findByRole('heading', { level: 1 });
    expect(h1).toHaveTextContent('Atelopus ebenoides');
    await waitFor(() => expect(h1).toHaveFocus());
    expect(document.title).toBe('Atelopus ebenoides · BioHub');
    expect(screen.getByText('Rana arlequín de Boyacá')).toBeInTheDocument();
    expect(screen.getByText('15 de marzo de 2026')).toBeInTheDocument();
    expect(screen.getByText('Bufonidae')).toBeInTheDocument();
    expect(screen.getByText('Adulto')).toBeInTheDocument();
    expect(screen.getByText('Anfibio endémico de páramo.')).toBeInTheDocument();
    expect(screen.getByText('Boyacá')).toBeInTheDocument();
    expect(screen.getByText('Andina')).toBeInTheDocument();
  });

  it('el aviso de ubicación protegida está siempre visible (sin plegar), con la línea de peligro', async () => {
    mockFetch(() => respuesta(COMPLETA));
    renderizar(`/especies/${ID}`);
    const aviso = await screen.findByRole('note');
    expect(within(aviso).getByRole('heading', { name: 'Ubicación protegida' })).toBeVisible();
    expect(within(aviso).getByText(AVISO)).toBeVisible();
    expect(within(aviso).getByText(/Si se supiera dónde está, personas dedicadas a la caza o al tráfico ilegal/)).toBeVisible();
    expect(within(aviso).getByText('Esta especie está en peligro de extinción, así que protegerla es todavía más importante.')).toBeVisible();
    expect(within(aviso).getByText(/Las personas investigadoras autorizadas sí pueden consultar la ubicación exacta/)).toBeVisible();
    expect(aviso.closest('details')).toBeNull();
  });

  it('el aviso se muestra aunque el servidor mande ubicacionProtegida=false; sin la línea de peligro si no aplica', async () => {
    mockFetch(() => respuesta({ ...COMPLETA, estadoConservacion: 'NO_AMENAZADA', ubicacionProtegida: false }));
    renderizar(`/especies/${ID}`);
    const aviso = await screen.findByRole('note');
    expect(within(aviso).getByText(AVISO)).toBeVisible();
    expect(within(aviso).queryByText(/en peligro de extinción/)).toBeNull();
  });

  it('NUNCA pinta coordenadas aunque lleguen (ni en texto ni en atributos)', async () => {
    mockFetch(() =>
      respuesta({
        ...COMPLETA,
        coordenadas: { latitud: 4.6097, longitud: -74.0817 },
        latitud: 4.6097,
        longitud: -74.0817,
        ubicacion: '4.6097,-74.0817',
        investigador: 'ana.ruiz',
      }),
    );
    const { container } = renderizar(`/especies/${ID}`);
    await screen.findByRole('note');
    const html = container.innerHTML;
    for (const prohibido of ['4.6097', '4.6', '74.0817', '-74.0', 'ana.ruiz', 'latitud', 'longitud', 'coordenadas']) {
      expect(html).not.toContain(prohibido);
    }
    expect(screen.queryByText(/latitud|longitud/i)).toBeNull();
  });

  it('el adaptador descarta todo campo fuera de la lista blanca', () => {
    const ficha = aFichaPublica({ ...COMPLETA, coordenadas: { latitud: 1, longitud: 2 }, latitud: 1, longitud: 2, usuario: 'x' });
    expect(Object.keys(ficha).sort()).toEqual(
      ['id', 'nombreCientifico', 'nombreComun', 'familia', 'tipoEspecimen', 'fechaRecoleccion', 'estadoConservacion', 'departamento', 'region', 'descripcion'].sort(),
    );
    expect(JSON.stringify(ficha)).not.toMatch(/latitud|longitud|coordenadas|usuario/);
  });

  it('también descarta coordenadas en el listado', async () => {
    mockFetch(() => respuesta(listado({ content: [{ ...FICHA, latitud: 4.6097, longitud: -74.0817, coordenadas: { latitud: 4.6097 } }] })));
    const { container } = renderizar('/');
    await screen.findByText('1 especie encontrada');
    expect(container.innerHTML).not.toMatch(/4\.6097|74\.0817|latitud|longitud/);
  });

  it('omite filas y secciones con campos ausentes (sin "null" ni "-")', async () => {
    mockFetch(() => respuesta({ ...FICHA, nombreComun: null, tipoEspecimen: null, fechaRecoleccion: null, descripcion: null }));
    const { container } = renderizar(`/especies/${ID}`);
    await screen.findByRole('note');
    expect(screen.queryByText('Tipo de espécimen')).toBeNull();
    expect(screen.queryByText('Fecha de recolección')).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Descripción' })).toBeNull();
    expect(container.textContent).not.toMatch(/null/);
  });

  it('cargando: esqueleto y mensaje de estado', async () => {
    mockFetch(() => new Promise(() => {}));
    renderizar(`/especies/${ID}`);
    expect(screen.getByText('Cargando la ficha…')).toBeInTheDocument();
    expect(screen.getAllByTestId('esqueleto').length).toBeGreaterThan(0);
  });

  it('404: pantalla "No encontramos esta especie" con botón al buscador y foco en el h1', async () => {
    mockFetch(() => respuesta({ codigo: 'NO_ENCONTRADO' }, 404));
    renderizar(`/especies/${ID}`);
    const h1 = await screen.findByRole('heading', { level: 1, name: 'No encontramos esta especie' });
    await waitFor(() => expect(h1).toHaveFocus());
    expect(screen.getByText('Es posible que el enlace esté mal escrito o que la ficha ya no esté disponible.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir al buscador' })).toBeInTheDocument();
    expect(document.title).toBe('Especie no encontrada · BioHub');
  });

  it('400 (id que no es UUID) se ve igual que el 404', async () => {
    mockFetch(() => respuesta({ codigo: 'PARAMETRO_INVALIDO' }, 400));
    renderizar('/especies/abc');
    expect(await screen.findByRole('heading', { level: 1, name: 'No encontramos esta especie' })).toBeInTheDocument();
  });

  it('error 503: alerta, Reintentar y enlace al buscador', async () => {
    let falla = true;
    mockFetch(() => (falla ? respuesta({}, 503) : respuesta(COMPLETA)));
    const u = userEvent.setup();
    renderizar(`/especies/${ID}`);
    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar la información, intenta de nuevo');
    expect(screen.getByRole('link', { name: 'Ir al buscador' })).toBeInTheDocument();
    falla = false;
    await u.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('note')).toBeInTheDocument();
  });

  it('429 en la ficha: texto de límite y Reintentar desactivado', async () => {
    mockFetch(() => respuesta({}, 429));
    renderizar(`/especies/${ID}`);
    expect(await screen.findByRole('alert')).toHaveTextContent('Hiciste muchas búsquedas seguidas');
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeDisabled();
  });

  it('desde la lista, "Volver a los resultados" conserva la búsqueda', async () => {
    mockFetch((url) => (url.pathname.endsWith(`/${ID}`) ? respuesta(COMPLETA) : respuesta(listado())));
    const u = userEvent.setup();
    renderizar('/?q=atelopus');
    await u.click(await screen.findByRole('link', { name: /Atelopus ebenoides/ }));
    const volver = await screen.findByRole('link', { name: 'Volver a los resultados' });
    expect(volver).toHaveAttribute('href', '/?q=atelopus');
    await u.click(volver);
    expect(await screen.findByText('1 especie encontrada')).toBeInTheDocument();
    expect(screen.getByLabelText('Nombre científico o común')).toHaveValue('atelopus');
  });
});
