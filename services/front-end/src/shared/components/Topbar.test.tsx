import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Topbar } from './Topbar'

describe('Topbar', () => {
  it('Topbar - renders - displays platform name', () => {
    render(<Topbar />)

    expect(screen.getByText('TRADE-LAB')).toBeInTheDocument()
  })

  it('Topbar - renders - has top bar landmark', () => {
    render(<Topbar />)

    expect(screen.getByRole('banner')).toBeInTheDocument()
  })

  it('Topbar - renders - has user area', () => {
    render(<Topbar />)

    expect(screen.getByLabelText(/user area/i)).toBeInTheDocument()
  })
})
