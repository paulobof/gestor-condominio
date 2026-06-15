import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    css: true,
    // Testes unitários vivem em src/. Os specs de e2e/ rodam pelo Playwright.
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    // No pre-push o vitest roda logo após os testes de backend (Testcontainers/Docker),
    // com a máquina ainda saturada e os testes (jsdom + userEvent) estouravam o timeout
    // padrão de 5s. A margem maior cobre a execução sob carga sem mascarar bug real.
    testTimeout: 20000,
    hookTimeout: 20000,
  },
});
