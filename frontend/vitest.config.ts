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
  },
});
