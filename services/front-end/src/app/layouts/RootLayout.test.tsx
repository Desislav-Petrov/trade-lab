import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { RootLayout } from './RootLayout'

function renderLayout(initialPath = '/') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route element={<RootLayout />}>
          <Route path="*" element={<div>Page content</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('RootLayout', () => {
  it('RootLayout - renders - displays topbar', () => {
    renderLayout()

    expect(screen.getByRole('banner')).toBeInTheDocument()
  })

  it('RootLayout - renders - displays sidebar navigation', () => {
    renderLayout()

    expect(screen.getByRole('navigation', { name: /main navigation/i })).toBeInTheDocument()
  })

  it('RootLayout - renders - renders child route content via Outlet', () => {
    renderLayout()

    expect(screen.getByText('Page content')).toBeInTheDocument()
  })

  it('RootLayout - renders - main content region exists', () => {
    renderLayout()

    expect(screen.getByRole('main')).toBeInTheDocument()
  })
})
