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
