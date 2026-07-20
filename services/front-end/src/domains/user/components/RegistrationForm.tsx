import { useState } from 'react'
import { useRegisterUser } from '../hooks/useRegisterUser'
import type { AxiosError } from 'axios'

interface RegistrationFormProps {
  onSuccess?: () => void
}

interface FormFields {
  firstName: string
  lastName: string
  address: string
  email: string
}

interface FieldErrors {
  firstName?: string
  lastName?: string
  address?: string
  email?: string
}

function validate(fields: FormFields): FieldErrors {
  const errors: FieldErrors = {}
  if (!fields.firstName.trim()) errors.firstName = 'First name is required'
  if (!fields.lastName.trim()) errors.lastName = 'Last name is required'
  if (!fields.address.trim()) errors.address = 'Address is required'
  if (!fields.email.trim()) {
    errors.email = 'Email is required'
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(fields.email)) {
    errors.email = 'Must be a valid email address'
  }
  return errors
}

export function RegistrationForm({ onSuccess }: RegistrationFormProps) {
  const [fields, setFields] = useState<FormFields>({
    firstName: '',
    lastName: '',
    address: '',
    email: '',
  })
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})

  const { mutate, isPending, error } = useRegisterUser({ onSuccess })

  const serverConflict =
    error && (error as AxiosError)?.response?.status === 409
      ? 'An account with this email already exists.'
      : null

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const { name, value } = e.target
    setFields((prev) => ({ ...prev, [name]: value }))
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const errors = validate(fields)
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors)
      return
    }
    setFieldErrors({})
    mutate(fields)
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
      {serverConflict && (
        <p
          role="alert"
          className="border-l-2 border-[var(--color-danger)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-danger)]"
        >
          {serverConflict}
        </p>
      )}

      <div className="flex flex-col gap-1">
        <label
          htmlFor="firstName"
          className="text-xs uppercase tracking-wider text-[var(--color-text-muted)]"
        >
          First name
        </label>
        <input
          id="firstName"
          name="firstName"
          value={fields.firstName}
          onChange={handleChange}
          className="w-full rounded border border-[var(--color-border)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-text-primary)] focus:border-[var(--color-accent)] focus:outline-none"
        />
        {fieldErrors.firstName && (
          <span role="alert" className="text-xs text-[var(--color-danger)]">
            {fieldErrors.firstName}
          </span>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label
          htmlFor="lastName"
          className="text-xs uppercase tracking-wider text-[var(--color-text-muted)]"
        >
          Last name
        </label>
        <input
          id="lastName"
          name="lastName"
          value={fields.lastName}
          onChange={handleChange}
          className="w-full rounded border border-[var(--color-border)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-text-primary)] focus:border-[var(--color-accent)] focus:outline-none"
        />
        {fieldErrors.lastName && (
          <span role="alert" className="text-xs text-[var(--color-danger)]">
            {fieldErrors.lastName}
          </span>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label
          htmlFor="address"
          className="text-xs uppercase tracking-wider text-[var(--color-text-muted)]"
        >
          Address
        </label>
        <input
          id="address"
          name="address"
          value={fields.address}
          onChange={handleChange}
          className="w-full rounded border border-[var(--color-border)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-text-primary)] focus:border-[var(--color-accent)] focus:outline-none"
        />
        {fieldErrors.address && (
          <span role="alert" className="text-xs text-[var(--color-danger)]">
            {fieldErrors.address}
          </span>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label
          htmlFor="email"
          className="text-xs uppercase tracking-wider text-[var(--color-text-muted)]"
        >
          Email
        </label>
        <input
          id="email"
          name="email"
          type="email"
          value={fields.email}
          onChange={handleChange}
          className="w-full rounded border border-[var(--color-border)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-text-primary)] focus:border-[var(--color-accent)] focus:outline-none"
        />
        {fieldErrors.email && (
          <span role="alert" className="text-xs text-[var(--color-danger)]">
            {fieldErrors.email}
          </span>
        )}
      </div>

      <button
        type="submit"
        disabled={isPending}
        className="mt-2 w-full rounded bg-[var(--color-accent)] px-4 py-2 text-xs font-medium text-[var(--color-bg)] transition-opacity disabled:cursor-not-allowed disabled:opacity-40"
      >
        {isPending ? 'Submitting…' : 'Submit'}
      </button>
    </form>
  )
}
