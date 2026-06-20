import { createBrowserRouter } from 'react-router-dom'
import { RootLayout } from './layouts/RootLayout'
import { RegistrationPage } from '../domains/user/pages/RegistrationPage'

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [{ path: '/register', element: <RegistrationPage /> }],
  },
])
