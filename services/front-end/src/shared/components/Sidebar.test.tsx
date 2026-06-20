import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { Sidebar } from './Sidebar'

function renderSidebar(initialPath = '/') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Sidebar />
    </MemoryRouter>,
  )
}

describe('Sidebar', () => {
  it('Sidebar - renders - displays all nav links', () => {
    renderSidebar()

    expect(screen.getByRole('link', { name: /trade/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /ledger/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /market/i })).toBeInTheDocument()
  })

  it('Sidebar - renders - has main navigation landmark', () => {
    renderSidebar()

    expect(screen.getByRole('navigation', { name: /main navigation/i })).toBeInTheDocument()
  })

  it('Sidebar - active route /trade - Trade link has active styles', () => {
    renderSidebar('/trade')

    const tradeLink = screen.getByRole('link', { name: /trade/i })
    expect(tradeLink.className).toContain('text-[var(--color-accent)]')
  })

  it('Sidebar - active route /trade - other links do not have active styles', () => {
    renderSidebar('/trade')

    const ledgerLink = screen.getByRole('link', { name: /ledger/i })
    expect(ledgerLink.className).not.toContain('text-[var(--color-accent)]')
  })
})
