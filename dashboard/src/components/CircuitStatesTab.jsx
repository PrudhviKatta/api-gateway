import { useState, useEffect, useRef } from 'react'
import { getCircuitBreakers } from '../api.js'

const POLL_INTERVAL_MS = 5000

export default function CircuitStatesTab() {
  const [breakers, setBreakers] = useState(null) // null = not loaded yet
  const [error, setError] = useState(null)
  const intervalRef = useRef(null)

  async function load() {
    try {
      const data = await getCircuitBreakers()
      // Actuator response shape: { circuitBreakers: { routeName: { state, failureRate, ... } } }
      setBreakers(data.circuitBreakers ?? {})
      setError(null)
    } catch {
      setError('Failed to fetch circuit breaker states.')
    }
  }

  useEffect(() => {
    load()
    intervalRef.current = setInterval(load, POLL_INTERVAL_MS)
    return () => clearInterval(intervalRef.current)
  }, [])

  const entries = breakers ? Object.entries(breakers) : []

  return (
    <div>
      <div className="flex items-center gap-3 mb-4">
        <h2 className="text-lg font-medium">Circuit States</h2>
        <span className="text-xs text-gray-500">polled every {POLL_INTERVAL_MS / 1000}s</span>
      </div>

      {error && <p className="text-red-400 text-sm">{error}</p>}

      {breakers === null && !error && (
        <p className="text-gray-500 text-sm">Loading…</p>
      )}

      {breakers !== null && entries.length === 0 && (
        <p className="text-gray-500 text-sm">
          No circuit breakers registered yet. Route traffic through the gateway to create them.
        </p>
      )}

      {entries.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {entries.map(([name, cb]) => (
            <BreakerCard key={name} name={name} cb={cb} />
          ))}
        </div>
      )}
    </div>
  )
}

function BreakerCard({ name, cb }) {
  const state = cb.state ?? 'UNKNOWN'

  let stateCls = ''
  if (state === 'CLOSED')    stateCls = 'bg-green-900/40 text-green-400 border-green-800'
  else if (state === 'OPEN') stateCls = 'bg-red-900/40 text-red-400 border-red-800'
  else                       stateCls = 'bg-yellow-900/40 text-yellow-400 border-yellow-800' // HALF_OPEN

  return (
    <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
      <div className="flex items-start justify-between gap-2 mb-3">
        <span className="font-mono text-sm text-gray-200 break-all">{name}</span>
        <span className={`shrink-0 text-xs font-semibold px-2 py-0.5 rounded border ${stateCls}`}>
          {state}
        </span>
      </div>

      <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
        <Stat label="Failure rate" value={cb.failureRate ?? '—'} />
        <Stat label="Slow call rate" value={cb.slowCallRate ?? '—'} />
        <Stat label="Buffered calls" value={cb.bufferedCalls ?? '—'} />
        <Stat label="Failed calls" value={cb.failedCalls ?? '—'} />
        <Stat label="Not permitted" value={cb.notPermittedCalls ?? '—'} />
      </dl>
    </div>
  )
}

function Stat({ label, value }) {
  return (
    <>
      <dt className="text-gray-500">{label}</dt>
      <dd className="text-gray-300 font-mono">{String(value)}</dd>
    </>
  )
}
