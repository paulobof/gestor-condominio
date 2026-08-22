import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'node:path';

export default defineConfig(({ mode }) => {
  // Alvo do proxy configurável: 8080 costuma estar ocupada por outro projeto local.
  const env = { ...loadEnv(mode, process.cwd(), 'VITE_'), ...process.env };
  return {
    plugins: [
      react(),
      VitePWA({
        registerType: 'autoUpdate',
        includeAssets: ['icon-192.png', 'icon-512.png', 'apple-touch-icon.png'],
        manifest: {
          name: 'HELBOR TRILOGY HOME',
          short_name: 'Helbor',
          description: 'Portal de gestão do condomínio HELBOR TRILOGY HOME.',
          start_url: '/',
          scope: '/',
          display: 'standalone',
          orientation: 'portrait-primary',
          background_color: '#111111',
          theme_color: '#111111',
          lang: 'pt-BR',
          categories: ['productivity', 'utilities'],
          icons: [
            { src: '/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
            { src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
            { src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
          ],
        },
        workbox: {
          // SPA fallback; nunca cachear chamadas à API (sempre rede).
          navigateFallback: '/index.html',
          navigateFallbackDenylist: [/^\/api/],
          globPatterns: ['**/*.{js,css,html,svg,woff2}'],
        },
      }),
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      port: Number(env.VITE_DEV_PORT ?? 5173),
      proxy: {
        // Backend local. Configurável porque 8080 costuma estar ocupada por outro projeto.
        '/api': {
          target: env.VITE_DEV_API_TARGET ?? 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  };
});
