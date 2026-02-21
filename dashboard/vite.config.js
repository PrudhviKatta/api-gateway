import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In dev (npm run dev), the proxy forwards /routes, /actuator, /dashboard
// requests to the Spring Boot backend at :8080. This makes API calls appear
// same-origin to the browser — no CORS issues in development.
//
// In production (npm run build), there is no proxy. The built static files
// call the backend directly using VITE_API_BASE_URL.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/routes':    'http://localhost:8080',
      '/actuator':  'http://localhost:8080',
      '/dashboard': 'http://localhost:8080',
    }
  }
})
