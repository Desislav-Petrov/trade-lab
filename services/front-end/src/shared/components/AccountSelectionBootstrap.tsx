import { useDefaultAccountSelection } from '../hooks/useDefaultAccountSelection'

/**
 * Renderless component that seeds the shared selected-account store with a
 * default account for the whole authenticated session. Mounted once at the app
 * root so the default is available on every page — not only Portfolio.
 */
export function AccountSelectionBootstrap() {
  useDefaultAccountSelection()
  return null
}
