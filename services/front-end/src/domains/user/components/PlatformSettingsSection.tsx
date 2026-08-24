import { FeedTypeSelector } from './FeedTypeSelector'
import { usePatchUserSettings } from '../hooks/usePatchUserSettings'
import { useSessionStore } from '../hooks/useSessionStore'
import type { FeedType } from '../types/userSettings'
import { Alert, AlertDescription } from '@/shared/components/ui/alert'

export interface PlatformSettingsSectionProps {}

export function PlatformSettingsSection(_props: PlatformSettingsSectionProps) {
  const user = useSessionStore((s) => s.user)
  const settings = useSessionStore((s) => s.settings)
  const { mutate, isPending, isError, isSuccess, errorStatus } = usePatchUserSettings(
    user?.userId ?? '',
  )

  function handleFeedTypeChange(feedType: FeedType) {
    mutate({ feedType })
  }

  if (!settings) {
    return null
  }

  return (
    <section>
      <h2 className="mb-4 text-xs font-medium tracking-widest text-[var(--color-text-primary)]">
        General Platform Settings
      </h2>
      <FeedTypeSelector
        currentFeedType={settings.feedType}
        onFeedTypeChange={handleFeedTypeChange}
        isPending={isPending}
        isError={isError}
        errorStatus={errorStatus}
      />
      {isSuccess && (
        <Alert variant="success" role="status" className="mt-2">
          <AlertDescription>Saved</AlertDescription>
        </Alert>
      )}
    </section>
  )
}
