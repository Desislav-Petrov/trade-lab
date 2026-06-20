import { Outlet } from 'react-router-dom'
import { Sidebar } from '../../shared/components/Sidebar'
import { Topbar } from '../../shared/components/Topbar'

export function RootLayout() {
  return (
    <div className="flex h-full flex-col bg-[var(--color-bg)]">
      <Topbar />
      <div className="flex min-h-0 flex-1">
        <Sidebar />
        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
