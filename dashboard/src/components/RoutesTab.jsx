import { useState, useEffect, useCallback } from 'react'
import { getRoutes, deleteRoute } from '../api.js'
import RouteModal from './RouteModal.jsx'

export default function RoutesTab() {
  const [routes, setRoutes] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [modalRoute, setModalRoute] = useState(undefined) // undefined=closed, null=new, obj=edit

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getRoutes()
      setRoutes(data)
    } catch {
      setError('Failed to load routes — is the backend running?')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  async function handleDelete(id, path) {
    if (!confirm(`Delete route "${path}"?`)) return
    try {
      await deleteRoute(id)
      setRoutes(prev => prev.filter(r => r.id !== id))
    } catch {
      alert('Delete failed.')
    }
  }

  function handleSaved() {
    setModalRoute(undefined)
    load()
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-medium">Routes</h2>
        <button
          onClick={() => setModalRoute(null)}
          className="px-3 py-1.5 text-sm bg-blue-600 hover:bg-blue-700 rounded font-medium"
        >
          + New Route
        </button>
      </div>

      {loading && <p className="text-gray-500 text-sm">Loading…</p>}
      {error   && <p className="text-red-400 text-sm">{error}</p>}

      {!loading && !error && routes.length === 0 && (
        <p className="text-gray-500 text-sm">No routes yet. Create one to get started.</p>
      )}

      {!loading && routes.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-sm border-collapse">
            <thead>
              <tr className="border-b border-gray-800 text-gray-400 text-left">
                <Th>ID</Th>
                <Th>Path</Th>
                <Th>Target URL</Th>
                <Th>Capacity</Th>
                <Th>Refill / sec</Th>
                <Th>Created</Th>
                <Th></Th>
              </tr>
            </thead>
            <tbody>
              {routes.map(r => (
                <tr key={r.id} className="border-b border-gray-800/50 hover:bg-gray-900/50">
                  <Td className="text-gray-500">{r.id}</Td>
                  <Td className="font-mono">{r.path}</Td>
                  <Td className="text-gray-300 truncate max-w-xs">{r.targetUrl}</Td>
                  <Td>{r.capacity ?? <span className="text-gray-600">—</span>}</Td>
                  <Td>{r.refillRatePerSecond ?? <span className="text-gray-600">—</span>}</Td>
                  <Td className="text-gray-500">{formatDate(r.createdAt)}</Td>
                  <Td>
                    <div className="flex gap-3">
                      <button
                        onClick={() => setModalRoute(r)}
                        className="text-blue-400 hover:text-blue-300 text-xs"
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => handleDelete(r.id, r.path)}
                        className="text-red-400 hover:text-red-300 text-xs"
                      >
                        Delete
                      </button>
                    </div>
                  </Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modalRoute !== undefined && (
        <RouteModal
          route={modalRoute}
          onSaved={handleSaved}
          onClose={() => setModalRoute(undefined)}
        />
      )}
    </div>
  )
}

function Th({ children }) {
  return <th className="py-2 pr-4 font-medium">{children}</th>
}

function Td({ children, className = '' }) {
  return <td className={`py-2.5 pr-4 ${className}`}>{children}</td>
}

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString(undefined, {
    month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}
