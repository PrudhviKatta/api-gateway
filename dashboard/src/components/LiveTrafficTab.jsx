import { useState, useEffect, useRef } from 'react'
import { SSE_BASE } from '../api.js'

const MAX_EVENTS = 50

export default function LiveTrafficTab() {
  const [events, setEvents] = useState([])
  const [connected, setConnected] = useState(false)
  const esRef = useRef(null)

  useEffect(() => {
    // Open SSE connection. EventSource automatically reconnects on error,
    // but we track our own state for the UI status badge.
    const es = new EventSource(`${SSE_BASE}/dashboard/stream`)
    esRef.current = es

    es.onopen = () => setConnected(true)

    es.onmessage = (e) => {
      try {
        const event = JSON.parse(e.data)
        setEvents(prev => [event, ...prev].slice(0, MAX_EVENTS))
      } catch {
        // Malformed event — ignore
      }
    }

    es.onerror = () => setConnected(false)

    return () => {
      es.close()
      setConnected(false)
    }
  }, [])

  return (
    <div>
      <div className="flex items-center gap-3 mb-4">
        <h2 className="text-lg font-medium">Live Traffic</h2>
        <span className={`flex items-center gap-1.5 text-xs px-2 py-0.5 rounded-full ${
          connected ? 'bg-green-900/40 text-green-400' : 'bg-red-900/40 text-red-400'
        }`}>
          <span className={`w-1.5 h-1.5 rounded-full ${connected ? 'bg-green-400' : 'bg-red-400'}`} />
          {connected ? 'Connected' : 'Disconnected'}
        </span>
        <span className="text-xs text-gray-500">showing last {MAX_EVENTS} events</span>
      </div>

      {events.length === 0 ? (
        <p className="text-gray-500 text-sm">
          {connected
            ? 'Waiting for requests through the gateway…'
            : 'Connecting to event stream…'}
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm border-collapse">
            <thead>
              <tr className="border-b border-gray-800 text-gray-400 text-left">
                <Th>Time</Th>
                <Th>IP</Th>
                <Th>Method</Th>
                <Th>Path</Th>
                <Th>Status</Th>
                <Th>Latency</Th>
                <Th>Rate Ltd</Th>
              </tr>
            </thead>
            <tbody>
              {events.map((ev, i) => (
                <tr key={i} className="border-b border-gray-800/50 hover:bg-gray-900/50">
                  <Td className="text-gray-500 whitespace-nowrap">{formatTime(ev.timestamp)}</Td>
                  <Td className="font-mono text-xs">{ev.clientIp}</Td>
                  <Td className="font-mono text-xs font-medium">{ev.method}</Td>
                  <Td className="font-mono text-xs max-w-xs truncate">{ev.path}</Td>
                  <Td><StatusBadge code={ev.statusCode} /></Td>
                  <Td className="text-gray-300">{ev.latencyMs}ms</Td>
                  <Td>
                    {ev.rateLimited
                      ? <span className="text-orange-400 text-xs">Yes</span>
                      : <span className="text-gray-600 text-xs">No</span>}
                  </Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function StatusBadge({ code }) {
  let cls = 'text-xs font-mono px-1.5 py-0.5 rounded '
  if (code >= 500)      cls += 'bg-red-900/50 text-red-300'
  else if (code >= 400) cls += 'bg-yellow-900/50 text-yellow-300'
  else if (code >= 300) cls += 'bg-blue-900/50 text-blue-300'
  else                  cls += 'bg-green-900/50 text-green-300'
  return <span className={cls}>{code}</span>
}

function Th({ children }) {
  return <th className="py-2 pr-4 font-medium">{children}</th>
}

function Td({ children, className = '' }) {
  return <td className={`py-2.5 pr-4 ${className}`}>{children}</td>
}

function formatTime(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleTimeString(undefined, {
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}
