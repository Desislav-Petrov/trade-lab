import { Navigate } from 'react-router-dom'
import { useSessionStore } from '../hooks/useSessionStore'

export function ProfilePage() {
  const user = useSessionStore((s) => s.user)

  if (!user) {
    return <Navigate to="/login" replace />
  }

  const joinedDate = new Date(user.createdAt).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })

  const today = new Date().toLocaleDateString(undefined, {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })

  return (
    <div className="max-w-lg">
      <p className="mb-1 text-xs tracking-widest text-[var(--color-accent)]">PROFILE</p>
      <h1 className="mb-2 text-sm font-medium text-[var(--color-text-primary)]">
        {user.firstName} {user.lastName}
      </h1>

      <p className="mb-6 text-xs text-[var(--color-text-muted)]">{today}</p>

      <dl className="space-y-3">
        {(
          [
            ['Email', user.email],
            ['Address', user.address],
            ['Status', user.status],
            ['Member since', joinedDate],
          ] as [string, string][]
        ).map(([label, value]) => (
          <div key={label} className="flex flex-col gap-0.5">
            <dt className="text-xs text-[var(--color-text-muted)]">{label}</dt>
            <dd className="text-xs text-[var(--color-text-primary)]">{value}</dd>
          </div>
        ))}
      </dl>
    </div>
  )
}
