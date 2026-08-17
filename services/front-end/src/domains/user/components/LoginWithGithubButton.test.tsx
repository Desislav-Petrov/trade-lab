import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { LoginWithGithubButton } from './LoginWithGithubButton'

describe('LoginWithGithubButton', () => {
  it('LoginWithGithubButton - renders - displays login with github text', () => {
    render(<LoginWithGithubButton onClick={vi.fn()} />)
    expect(screen.getByRole('button', { name: /login with github/i })).toBeInTheDocument()
  })

  it('LoginWithGithubButton - click - calls onClick exactly once', () => {
    const handleClick = vi.fn()
    render(<LoginWithGithubButton onClick={handleClick} />)
    fireEvent.click(screen.getByRole('button', { name: /login with github/i }))
    expect(handleClick).toHaveBeenCalledTimes(1)
  })
})
