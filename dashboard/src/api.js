// In dev: VITE_API_BASE_URL is unset → BASE is '' → Vite proxy forwards
//         /routes, /actuator, /dashboard to localhost:8080. No CORS needed.
// In prod: VITE_API_BASE_URL=https://api.railway.app → direct calls to backend.
const BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export const getRoutes = () =>
  fetch(`${BASE}/routes`).then(r => r.json())

export const createRoute = (body) =>
  fetch(`${BASE}/routes`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })

export const updateRoute = (id, body) =>
  fetch(`${BASE}/routes/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })

export const deleteRoute = (id) =>
  fetch(`${BASE}/routes/${id}`, { method: 'DELETE' })

export const getCircuitBreakers = () =>
  fetch(`${BASE}/actuator/circuitbreakers`).then(r => r.json())

// The SSE base URL is needed by EventSource constructor. In dev it's empty
// (proxy handles it); in prod it points to the backend host.
export const SSE_BASE = BASE
