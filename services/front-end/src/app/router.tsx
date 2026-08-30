import { createBrowserHistory, createRouter, createRoute, createRootRoute, redirect } from '@tanstack/react-router'
import { RootLayout } from './layouts/RootLayout'
import { LoginPage } from '../domains/user/pages/LoginPage'
import { RegistrationPage } from '../domains/user/pages/RegistrationPage'
import { ProfilePage } from '../domains/user/pages/ProfilePage'
import { AuthCallbackPage } from '../domains/user/pages/AuthCallbackPage'
import { AccountsPage } from '../domains/ledger/pages/AccountsPage'
import { TransactionListPage } from '../domains/ledger/pages/TransactionListPage'
import { StockTradingPage } from '../domains/stocktrading/pages/StockTradingPage'
import { PortfolioPage } from '../domains/portfolio/pages/PortfolioPage'

const rootRoute = createRootRoute({
  component: RootLayout,
})

const enableNoAuth = import.meta.env.VITE_ENABLE_NO_AUTH === 'true'

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  beforeLoad: () => {
    throw redirect({ to: enableNoAuth ? '/login' : '/oauth2/authorization/google' })
  },
})

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/login',
  component: LoginPage,
})

const registerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/register',
  component: RegistrationPage,
})

const authCallbackRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/auth/callback',
  component: AuthCallbackPage,
})

const profileRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/profile',
  component: ProfilePage,
})

const accountsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/accounts',
  component: AccountsPage,
})

const transactionListRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/accounts/$accountId/transactions',
  component: TransactionListPage,
})

const tradeRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/trade',
  component: StockTradingPage,
})

const portfolioRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/portfolio',
  component: PortfolioPage,
})

const noAuthRoutes = enableNoAuth ? [loginRoute, registerRoute] : []

export const routeTree = rootRoute.addChildren([
  indexRoute,
  ...noAuthRoutes,
  authCallbackRoute,
  profileRoute,
  accountsRoute,
  transactionListRoute,
  tradeRoute,
  portfolioRoute,
])

export const router = createRouter({
  routeTree,
  history: createBrowserHistory(),
})
