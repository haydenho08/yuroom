import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  server: {
    // In development the Spring Boot API runs on :8080. Proxying /api keeps the browser on one
    // origin, so there's no CORS to configure and the code uses plain relative URLs everywhere.
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // Like the backend: run tests far from Toronto so timezone bugs can't hide.
    env: { TZ: 'Pacific/Auckland' },
    restoreMocks: true,
  },
})
