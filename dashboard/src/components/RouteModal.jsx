import { useState, useEffect } from 'react'
import { createRoute, updateRoute } from '../api.js'

// Controlled form for both creating and editing a route.
// When `route` prop is provided, the form is in edit mode (PUT).
// When `route` is null, it's in create mode (POST).
export default function RouteModal({ route, onSaved, onClose }) {
  const isEdit = route != null

  const [form, setForm] = useState({
    path: '',
    targetUrl: '',
    capacity: '',
    refillRatePerSecond: '',
  })
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  // Pre-fill when editing
  useEffect(() => {
    if (route) {
      setForm({
        path: route.path ?? '',
        targetUrl: route.targetUrl ?? '',
        capacity: route.capacity ?? '',
        refillRatePerSecond: route.refillRatePerSecond ?? '',
      })
    }
  }, [route])

  function handleChange(e) {
    setForm(prev => ({ ...prev, [e.target.name]: e.target.value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    setSaving(true)

    // Build body — only include capacity/refillRate if they were filled in
    const body = {
      path: form.path.trim(),
      targetUrl: form.targetUrl.trim(),
      ...(form.capacity !== '' && { capacity: Number(form.capacity) }),
      ...(form.refillRatePerSecond !== '' && { refillRatePerSecond: Number(form.refillRatePerSecond) }),
    }

    try {
      const res = isEdit
        ? await updateRoute(route.id, body)
        : await createRoute(body)

      if (res.status === 409) {
        setError('A route with this path already exists.')
        return
      }
      if (!res.ok) {
        setError(`Unexpected error: ${res.status}`)
        return
      }
      onSaved()
    } catch (err) {
      setError('Network error — is the backend running?')
    } finally {
      setSaving(false)
    }
  }

  return (
    // Backdrop
    <div
      className="fixed inset-0 bg-black/60 flex items-center justify-center z-50"
      onClick={e => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="bg-gray-900 border border-gray-700 rounded-lg w-full max-w-md p-6">
        <h2 className="text-lg font-semibold mb-4">
          {isEdit ? 'Edit Route' : 'New Route'}
        </h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <Field
            label="Path *"
            name="path"
            placeholder="/api/users"
            value={form.path}
            onChange={handleChange}
            required
            disabled={isEdit} // path is the identifier — don't change it on edit
          />
          <Field
            label="Target URL *"
            name="targetUrl"
            placeholder="http://user-service:3000"
            value={form.targetUrl}
            onChange={handleChange}
            required
          />
          <div className="grid grid-cols-2 gap-4">
            <Field
              label="Capacity"
              name="capacity"
              type="number"
              placeholder="100"
              value={form.capacity}
              onChange={handleChange}
            />
            <Field
              label="Refill / sec"
              name="refillRatePerSecond"
              type="number"
              placeholder="10"
              value={form.refillRatePerSecond}
              onChange={handleChange}
            />
          </div>

          {error && (
            <p className="text-red-400 text-sm">{error}</p>
          )}

          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm rounded border border-gray-600 text-gray-300 hover:bg-gray-800"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={saving}
              className="px-4 py-2 text-sm rounded bg-blue-600 hover:bg-blue-700 disabled:opacity-50 font-medium"
            >
              {saving ? 'Saving…' : isEdit ? 'Save Changes' : 'Create Route'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// Small reusable field component — just to keep the form DRY within this file
function Field({ label, name, value, onChange, type = 'text', placeholder, required, disabled }) {
  return (
    <div>
      <label className="block text-xs text-gray-400 mb-1">{label}</label>
      <input
        type={type}
        name={name}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        required={required}
        disabled={disabled}
        min={type === 'number' ? 1 : undefined}
        className="w-full bg-gray-800 border border-gray-600 rounded px-3 py-2 text-sm
                   placeholder-gray-500 focus:outline-none focus:border-blue-500
                   disabled:opacity-50 disabled:cursor-not-allowed"
      />
    </div>
  )
}
