import {
  createBrowserHistory,
  createRouter,
  createRoute,
  createRootRoute,
  lazyRouteComponent,
  redirect,
} from '@tanstack/react-router'
import { RootLayout } from './layouts/RootLayout'
import { LoginPage } from '../domains/user/pages/LoginPage'
import { RegistrationPage } from '../domains/user/pages/RegistrationPage'
import { AuthCallbackPage } from '../domains/user/pages/AuthCallbackPage'

// Authenticated / chart-heavy pages are code-split so the unauthenticated
// login & registration flows do not pull in the portfolio pages or recharts.
const ProfilePage = lazyRouteComponent(
  () => import('../domains/user/pages/ProfilePage'),
  'ProfilePage',
)
const AccountsPage = lazyRouteComponent(
  () => import('../domains/ledger/pages/AccountsPage'),
  'AccountsPage',
)
const TransactionListPage = lazyRouteComponent(
  () => import('../domains/ledger/pages/TransactionListPage'),
  'TransactionListPage',
)
const StockTradingPage = lazyRouteComponent(
  () => import('../domains/stocktrading/pages/StockTradingPage'),
  'StockTradingPage',
)
const PortfolioPage = lazyRouteComponent(
  () => import('../domains/portfolio/pages/PortfolioPage'),
  'PortfolioPage',
)

const rootRoute = createRootRoute({
  component: RootLayout,
})

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  beforeLoad: () => {
    throw redirect({ to: '/login' })
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

const routeTree = rootRoute.addChildren([
  indexRoute,
  loginRoute,
  registerRoute,
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
