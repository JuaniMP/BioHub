import { expect, FICHA_PELIGRO, FICHA_COMUN, ID_COMUN, ID_PELIGRO, simularApi, test } from './fixtures.js';

const TEXTO_PROTEGIDA = /BioHub no publica el lugar exacto/;

test.describe('RF10 · buscar y abrir ficha (HU-06.2, HU-06.3, RNF6)', () => {
  test('RF10/HU-06.2: buscar una especie y ver su tarjeta sin ubicación exacta', async ({ page, erroresCsp }) => {
    const pedidas = await simularApi(page);
    await page.goto('/');
    await page.getByLabel('Nombre científico o común').fill('atelopus');
    await page.getByRole('button', { name: 'Buscar' }).click();

    await expect(page.getByRole('heading', { name: /1 especie/i })).toBeVisible();
    const tarjeta = page.getByRole('link', { name: /Atelopus ebenoides/ });
    await expect(tarjeta).toBeVisible();
    await expect(tarjeta).toContainText('Boyacá');
    await expect(tarjeta).toContainText('Andina');
    await expect(page.getByText('Dendropsophus columbianus')).toHaveCount(0);
    expect(pedidas.some((u) => u.includes('q=atelopus'))).toBe(true);
    expect(erroresCsp).toEqual([]);
  });

  test('RF10/HU-06.3/RNF6: buscar y abrir la ficha en 3 pasos (escribir, buscar, abrir)', async ({ page }) => {
    await simularApi(page);
    await page.goto('/');
    await page.getByLabel('Nombre científico o común').fill('dendropsophus');
    await page.getByRole('button', { name: 'Buscar' }).click();
    await page.getByRole('link', { name: /Dendropsophus columbianus/ }).click();

    await expect(page).toHaveURL(new RegExp(`/especies/${ID_COMUN}$`));
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(FICHA_COMUN.nombreCientifico);
    await expect(page.getByText('Familia')).toBeVisible();
    await expect(page.getByText('Antioquia')).toBeVisible();
    await page.getByRole('link', { name: 'Volver a los resultados' }).click();
    await expect(page.getByLabel('Nombre científico o común')).toHaveValue('dendropsophus');
  });
});

test.describe('RF11 / RNF7 · ubicación protegida', () => {
  test('RF11/RNF7: ficha en peligro sin coordenadas y con el mensaje explicativo visible', async ({ page, erroresCsp }) => {
    await simularApi(page);
    await page.goto(`/especies/${ID_PELIGRO}`);

    await expect(page.getByRole('heading', { level: 1 })).toHaveText(FICHA_PELIGRO.nombreCientifico);
    const aviso = page.getByRole('note', { name: 'Ubicación protegida' });
    await expect(aviso).toBeVisible(); // visible sin interacción: no tooltip ni plegable
    await expect(aviso).toContainText(TEXTO_PROTEGIDA);
    await expect(aviso).toContainText('está en peligro de extinción');
    await expect(aviso).toContainText('solo te mostramos el departamento y la región');
    await expect(page.getByText('Boyacá', { exact: true })).toBeVisible();

    // Ninguna coordenada en el DOM visible ni en atributos.
    const html = await page.content();
    expect(html).not.toMatch(/latitud|longitud|coordenadas|google\.com\/maps/i);
    expect(await page.locator('body').innerText()).not.toMatch(/-?\d{1,2}\.\d{3,}\s*,\s*-?\d{1,3}\.\d{3,}/);
    expect(await page.locator('[title]').count()).toBe(0);
    expect(erroresCsp).toEqual([]);
  });

  test('RNF7: la API envía coordenadas por error y la interfaz no las muestra (D14)', async ({ page }) => {
    await page.route('**/api/publica/fichas/*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...FICHA_PELIGRO, latitud: 5.6355, longitud: -73.8123, coordenadas: '5.6355,-73.8123' }),
      }),
    );
    await page.goto(`/especies/${ID_PELIGRO}`);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    const html = await page.content();
    expect(html).not.toContain('5.6355');
    expect(html).not.toContain('73.8123');
  });

  test('RNF7: el aviso general aparece en la página de resultados', async ({ page }) => {
    await simularApi(page);
    await page.goto('/');
    await expect(page.getByText(/no publica ubicaciones exactas, solo el departamento y la región/)).toBeVisible();
  });
});

test.describe('Estados de la búsqueda (HU-06.2)', () => {
  test('HU-06.2: sin resultados muestra mensaje y permite limpiar', async ({ page }) => {
    await simularApi(page, { modo: 'vacio' });
    await page.goto('/');
    await expect(page.getByText('No se encontraron especies')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Limpiar búsqueda' })).toBeVisible();
  });

  test('HU-06.2/RNF1: error 503 de la API muestra mensaje amable y reintentar', async ({ page }) => {
    await simularApi(page, { modo: 'error503' });
    await page.goto('/');
    await expect(page.getByText('No pudimos cargar la información, intenta de nuevo')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Reintentar' })).toBeVisible();
    await expect(page.locator('body')).not.toContainText(/SERVICIO_NO_DISPONIBLE|503|Exception/);
  });

  test('HU-06.3: 503 en la ficha muestra el mismo mensaje amable', async ({ page }) => {
    await simularApi(page, { modo: 'error503' });
    await page.goto(`/especies/${ID_PELIGRO}`);
    await expect(page.getByText('No pudimos cargar la información, intenta de nuevo')).toBeVisible();
  });

  test('HU-06.3: ficha inexistente (404) muestra "no encontrada"', async ({ page }) => {
    await simularApi(page);
    await page.goto('/especies/00000000-0000-4000-8000-000000000000');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.locator('main')).toContainText(/No encontramos esta especie/);
  });

  test('HU-06.2: texto de 1 letra se valida en el cliente sin llamar a la API', async ({ page }) => {
    const pedidas = await simularApi(page);
    await page.goto('/');
    await expect(page.getByRole('heading', { name: /especies/i }).first()).toBeVisible();
    const antes = pedidas.length;
    await page.getByLabel('Nombre científico o común').fill('a');
    await page.getByRole('button', { name: 'Buscar' }).click();
    await expect(page.getByText('Escribe al menos 2 letras.')).toBeVisible();
    expect(pedidas.length).toBe(antes);
  });
});

test.describe('Accesibilidad básica con teclado (RNF6)', () => {
  test('RNF6: buscar y abrir una ficha solo con el teclado', async ({ page, browserName }) => {
    await simularApi(page);
    await page.goto('/');
    const campo = page.getByLabel('Nombre científico o común');
    await campo.focus(); // Tab en Safari no recorre enlaces por defecto; se parte del campo
    await expect(campo).toBeFocused();
    await campo.fill('atelopus');
    await page.keyboard.press('Enter');
    const tarjeta = page.getByRole('link', { name: /Atelopus ebenoides/ });
    await expect(tarjeta).toBeVisible();
    await tarjeta.focus();
    await expect(tarjeta).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(new RegExp(`/especies/${ID_PELIGRO}$`));
    // al cargar la ficha el foco pasa al título
    await expect(page.getByRole('heading', { level: 1 })).toBeFocused();
  });

  test('RNF6: enlace "Saltar al contenido" es el primero en el orden de foco', async ({ page, browserName }) => {
    test.skip(browserName === 'webkit', 'Safari solo enfoca enlaces con Opción+Tab; se cubre en Chromium y Firefox');
    await simularApi(page);
    await page.goto('/');
    await page.keyboard.press('Tab');
    await expect(page.getByRole('link', { name: 'Saltar al contenido' })).toBeFocused();
  });
});

test.describe('SEC-J-07 · CSP', () => {
  test('SEC-J-07: la CSP está presente, sin unsafe-inline en scripts, y la app carga sin violaciones', async ({ page, erroresCsp }) => {
    await simularApi(page);
    await page.goto('/');
    const csp = await page.locator('meta[http-equiv="Content-Security-Policy"]').getAttribute('content');
    expect(csp).toContain("script-src 'self'");
    expect(csp).not.toMatch(/script-src[^;]*unsafe-(inline|eval)/);
    expect(csp).toContain("object-src 'none'");
    expect(csp).toMatch(/connect-src 'self' http/);
    await expect(page.getByRole('heading', { name: 'Busca una especie' })).toBeVisible();
    await page.getByRole('link', { name: /Atelopus ebenoides/ }).click();
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    expect(erroresCsp).toEqual([]);
  });

  test('SEC-J-07: un script en línea inyectado es bloqueado por la CSP', async ({ page }) => {
    await simularApi(page);
    await page.goto('/');
    const bloqueado = await page.evaluate(() => {
      window.__xss = false;
      const s = document.createElement('script');
      s.textContent = 'window.__xss = true';
      document.head.appendChild(s);
      return window.__xss === false;
    });
    expect(bloqueado).toBe(true);
  });
});
