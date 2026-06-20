import { useState } from 'react'
import type { UseRegisterUserOptions } from '../hooks/useRegisterUser'
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
  const [fields, setFields] = useState<FormFields>({ firstName: '', lastName: '', address: '', email: '' })
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
    <form onSubmit={handleSubmit} noValidate>
      {serverConflict && <p role="alert">{serverConflict}</p>}

      <div>
        <label htmlFor="firstName">First name</label>
        <input id="firstName" name="firstName" value={fields.firstName} onChange={handleChange} />
        {fieldErrors.firstName && <span role="alert">{fieldErrors.firstName}</span>}
      </div>

      <div>
        <label htmlFor="lastName">Last name</label>
        <input id="lastName" name="lastName" value={fields.lastName} onChange={handleChange} />
        {fieldErrors.lastName && <span role="alert">{fieldErrors.lastName}</span>}
      </div>

      <div>
        <label htmlFor="address">Address</label>
        <input id="address" name="address" value={fields.address} onChange={handleChange} />
        {fieldErrors.address && <span role="alert">{fieldErrors.address}</span>}
      </div>

      <div>
        <label htmlFor="email">Email</label>
        <input id="email" name="email" type="email" value={fields.email} onChange={handleChange} />
        {fieldErrors.email && <span role="alert">{fieldErrors.email}</span>}
      </div>

      <button type="submit" disabled={isPending}>
        {isPending ? 'Submitting…' : 'Submit'}
      </button>
    </form>
  )
}
