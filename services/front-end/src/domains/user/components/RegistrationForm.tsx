import { useState } from 'react'
import { useRegisterUser } from '../hooks/useRegisterUser'
import type { AxiosError } from 'axios'
import { Button } from '@/shared/components/ui/button'
import { Input } from '@/shared/components/ui/input'
import { Label } from '@/shared/components/ui/label'
import { Alert, AlertDescription } from '@/shared/components/ui/alert'

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
        <Alert variant="destructive" role="alert">
          <AlertDescription>{serverConflict}</AlertDescription>
        </Alert>
      )}

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="firstName">First name</Label>
        <Input
          id="firstName"
          name="firstName"
          value={fields.firstName}
          onChange={handleChange}
        />
        {fieldErrors.firstName && (
          <span role="alert" className="text-xs text-[var(--color-danger)]">
            {fieldErrors.firstName}
          </span>
        )}
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="lastName">Last name</Label>
        <Input
          id="lastName"
          name="lastName"
          value={fields.lastName}
          onChange={handleChange}
        />
        {fieldErrors.lastName && (
          <span role="alert" className="text-xs text-[var(--color-danger)]">
            {fieldErrors.lastName}
          </span>
        )}
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="address">Address</Label>
        <Input
          id="address"
          name="address"
          value={fields.address}
          onChange={handleChange}
        />
        {fieldErrors.address && (
          <span role="alert" className="text-xs text-[var(--color-danger)]">
            {fieldErrors.address}
          </span>
        )}
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="email">Email</Label>
        <Input
          id="email"
          name="email"
          type="email"
          value={fields.email}
          onChange={handleChange}
        />
        {fieldErrors.email && (
          <span role="alert" className="text-xs text-[var(--color-danger)]">
            {fieldErrors.email}
          </span>
        )}
      </div>

      <Button type="submit" className="mt-2 w-full" disabled={isPending}>
        {isPending ? 'Submitting…' : 'Submit'}
      </Button>
    </form>
  )
}
