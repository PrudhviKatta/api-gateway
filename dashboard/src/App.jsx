import { useState } from 'react'
import RoutesTab from './components/RoutesTab.jsx'
import LiveTrafficTab from './components/LiveTrafficTab.jsx'
import CircuitStatesTab from './components/CircuitStatesTab.jsx'

const TABS = [
  { id: 'routes',   label: 'Routes' },
  { id: 'traffic',  label: 'Live Traffic' },
  { id: 'circuits', label: 'Circuit States' },
]

export default function App() {
  const [activeTab, setActiveTab] = useState('routes')

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      {/* Header */}
      <header className="border-b border-gray-800 px-6 py-4">
        <h1 className="text-xl font-semibold tracking-tight">API Gateway Dashboard</h1>
      </header>

      {/* Tab bar */}
      <nav className="border-b border-gray-800 px-6">
        <div className="flex gap-1">
          {TABS.map(tab => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors ${
                activeTab === tab.id
                  ? 'border-blue-500 text-blue-400'
                  : 'border-transparent text-gray-400 hover:text-gray-200'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </nav>

      {/* Tab panel */}
      <main className="px-6 py-6">
        {activeTab === 'routes'   && <RoutesTab />}
        {activeTab === 'traffic'  && <LiveTrafficTab />}
        {activeTab === 'circuits' && <CircuitStatesTab />}
      </main>
    </div>
  )
}
