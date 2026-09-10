/**
 * Formats a numeric value as a localised currency string.
 *
 * Consolidates the previously duplicated `Intl.NumberFormat` currency
 * formatters that lived in each chart component.
 */
export function formatCurrency(value: number, currency: string = 'USD'): string {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}
