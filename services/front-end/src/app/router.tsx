import { createBrowserRouter, Navigate } from 'react-router-dom'
import { RootLayout } from './layouts/RootLayout'
import { LoginPage } from '../domains/user/pages/LoginPage'
import { RegistrationPage } from '../domains/user/pages/RegistrationPage'
import { ProfilePage } from '../domains/user/pages/ProfilePage'
import { AccountsPage } from '../domains/ledger/pages/AccountsPage'
import { TransactionListPage } from '../domains/ledger/pages/TransactionListPage'
import { StockTradingPage } from '../domains/stocktrading/pages/StockTradingPage'

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      { index: true, element: <Navigate to="/login" replace /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegistrationPage /> },
      { path: '/profile', element: <ProfilePage /> },
      { path: '/accounts', element: <AccountsPage /> },
      { path: '/accounts/:accountId/transactions', element: <TransactionListPage /> },
      { path: '/trade', element: <StockTradingPage /> },
    ],
  },
])
