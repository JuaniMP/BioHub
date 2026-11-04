import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * SEC-J-07: la CSP vive como <meta> en index.html. `%CONNECT_SRC%` se reemplaza por el origen de la API
 * (VITE_API_URL, el mismo que usa src/api/cliente.js). En `vite dev` se quita la meta porque el
 * refresco en caliente necesita scripts en línea; la CSP aplica al build y al preview.
 */
function cspPlugin(modo) {
  const env = loadEnv(modo, process.cwd(), 'VITE_');
  let origen = 'http://localhost:8080';
  try {
    origen = new URL(env.VITE_API_URL || origen).origin;
  } catch {
    // VITE_API_URL inválida: se deja el valor por defecto
  }
  return {
    name: 'biohub-csp',
    transformIndexHtml: {
      order: 'pre',
      handler(html, ctx) {
        if (ctx.server) return html.replace(/<meta http-equiv="Content-Security-Policy"[^>]*>\s*/s, '');
        return html.replace('%CONNECT_SRC%', origen);
      },
    },
  };
}

export default defineConfig(({ mode }) => ({
  plugins: [react(), cspPlugin(mode)],
  server: { port: 5174, strictPort: true },
  preview: { port: 5174, strictPort: true },
  test: {
    environment: 'jsdom',
    setupFiles: './src/setupTests.js',
    include: ['src/**/*.test.{js,jsx}'],
  },
}));
