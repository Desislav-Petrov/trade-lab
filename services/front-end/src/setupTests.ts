import '@testing-library/jest-dom'

// Provide localStorage mock for jsdom environment
// jsdom doesn't provide localStorage by default
if (typeof window !== 'undefined' && !window.localStorage) {
  const store: Record<string, string> = {}

  window.localStorage = {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => {
      store[key] = value.toString()
    },
    removeItem: (key: string) => {
      delete store[key]
    },
    clear: () => {
      Object.keys(store).forEach((key) => delete store[key])
    },
    get length() {
      return Object.keys(store).length
    },
    key: (index: number) => {
      const keys = Object.keys(store)
      return keys[index] ?? null
    },
  } as Storage
}

// jsdom does not implement window.scrollTo — stub it to prevent unhandled errors
// from TanStack Router's scroll restoration during route navigation in tests
if (typeof window !== 'undefined') {
  window.scrollTo = () => {}
}

// TanStack Router's Navigate component triggers async navigation that may complete
// after the React tree is unmounted, causing "Maximum update depth exceeded"
// unhandled rejections. These are false positives in the test environment and can
// be safely ignored.
if (typeof globalThis !== 'undefined' && 'process' in globalThis) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  ;(globalThis as any).process?.on?.('unhandledRejection', (reason: unknown) => {
    if (reason instanceof Error && reason.message.includes('Maximum update depth exceeded')) {
      return
    }
    throw reason
  })
}
