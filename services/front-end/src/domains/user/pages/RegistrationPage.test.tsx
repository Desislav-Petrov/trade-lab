import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { createElement } from 'react'
import { RegistrationPage } from './RegistrationPage'

vi.mock('../components/RegistrationForm', () => ({
  RegistrationForm: ({ onSuccess }: { onSuccess?: () => void }) =>
    createElement('div', null,
      createElement('span', null, 'RegistrationForm'),
      createElement('button', { onClick: onSuccess }, 'Trigger Success')
    ),
}))

describe('RegistrationPage', () => {
  it('RegistrationPage - renders - displays RegistrationForm', () => {
    render(
      createElement(MemoryRouter, { initialEntries: ['/register'] },
        createElement(Routes, null,
          createElement(Route, { path: '/register', element: createElement(RegistrationPage) })
        )
      )
    )

    expect(screen.getByText('RegistrationForm')).toBeInTheDocument()
  })

  it('RegistrationPage - success - redirects to /login', async () => {
    render(
      createElement(MemoryRouter, { initialEntries: ['/register'] },
        createElement(Routes, null,
          createElement(Route, { path: '/register', element: createElement(RegistrationPage) }),
          createElement(Route, { path: '/login', element: createElement('div', null, 'Login Page') })
        )
      )
    )

    screen.getByRole('button', { name: /trigger success/i }).click()

    expect(await screen.findByText('Login Page')).toBeInTheDocument()
  })
})
