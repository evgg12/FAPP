import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * The app calls the API with relative paths and the dev server forwards /api to the
 * backend. That keeps requests same-origin, so no CORS configuration is needed on the
 * Spring Boot side and the backend stays untouched.
 *
 * Point it somewhere else with FAPP_API_URL when the backend is not on localhost:8080.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.FAPP_API_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    // Testing Library registers its between-test DOM cleanup against a global
    // afterEach, so without this each render would stack on the previous test's.
    globals: true,
  },
})
