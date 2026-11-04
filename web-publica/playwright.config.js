import { defineConfig, devices } from '@playwright/test';

/**
 * RNF13: la misma suite corre en Chromium, Firefox y WebKit (motor de Safari).
 * Edge está basado en Chromium: el proyecto `msedge` solo se agrega con E2E_EDGE=1 (requiere el canal
 * instalado con `npx playwright install msedge`); si no, `chromium` cubre ese motor.
 * La API se simula con page.route (e2e/fixtures.js): no hace falta backend.
 */
const proyectos = [
  { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
];
// E2E_SIN_WEBKIT=1: omite Safari en equipos sin las librerías del sistema (sudo npx playwright install-deps webkit).
if (process.env.E2E_SIN_WEBKIT !== '1') proyectos.push({ name: 'webkit', use: { ...devices['Desktop Safari'] } });
if (process.env.E2E_EDGE === '1') {
  proyectos.push({ name: 'msedge', use: { ...devices['Desktop Edge'], channel: 'msedge' } });
}

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: { baseURL: 'http://localhost:5174', locale: 'es-CO', trace: 'retain-on-failure' },
  projects: proyectos,
  webServer: {
    // Se prueba el build real (con la CSP aplicada), no el servidor de desarrollo.
    command: 'npm run build && npm run preview',
    url: 'http://localhost:5174',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
