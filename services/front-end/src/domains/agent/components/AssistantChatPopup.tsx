import { useState } from 'react'
import type { FormEvent } from 'react'
import { Alert, AlertDescription } from '../../../shared/components/ui/alert'
import { Button } from '../../../shared/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '../../../shared/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '../../../shared/components/ui/dialog'
import { Input } from '../../../shared/components/ui/input'
import { cn } from '../../../shared/lib/utils'
import { useAgentChat } from '../hooks/useAgentChat'
import { useAgentStore } from '../hooks/useAgentStore'
import { AssistantMessage } from './AssistantMessage'

export interface AssistantChatPopupProps {
  className?: string
}

export function AssistantChatPopup({ className }: AssistantChatPopupProps) {
  const isOpen = useAgentStore((state) => state.isOpen)
  const transcript = useAgentStore((state) => state.transcript)
  const open = useAgentStore((state) => state.open)
  const close = useAgentStore((state) => state.close)
  const { error, hasSelectedAccount, isSending, sendMessage, clearError } = useAgentChat()
  const [message, setMessage] = useState('')

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    clearError()

    const sent = await sendMessage(message)
    if (sent) {
      setMessage('')
    }
  }

  function handleOpenChange(nextOpen: boolean) {
    if (nextOpen) open()
    else close()
  }

  return (
    <Dialog open={isOpen} onOpenChange={handleOpenChange}>
      <DialogContent
        className={cn(
          'right-6 bottom-24 left-auto top-auto translate-x-0 translate-y-0 p-0 sm:max-w-md',
          className,
        )}
      >
        <DialogHeader className="px-4 pt-4">
          <DialogTitle>Trade Lab AI Assistant</DialogTitle>
          <DialogDescription>
            Ask portfolio questions for the currently selected account.
          </DialogDescription>
        </DialogHeader>

        <Card className="border-0 shadow-none">
          <CardHeader className="pb-2">
            <CardTitle>Conversation</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            {!hasSelectedAccount && (
              <Alert variant="warning">
                <AlertDescription>Select an account in Portfolio before starting a chat.</AlertDescription>
              </Alert>
            )}

            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}

            <div className="flex max-h-80 min-h-40 flex-col gap-3 overflow-y-auto rounded border border-[var(--color-border)] bg-[var(--color-surface)] p-3">
              {transcript.length === 0 ? (
                <p className="text-xs text-[var(--color-text-muted)]">
                  Start a conversation with the assistant.
                </p>
              ) : (
                transcript.map((turn) => <AssistantMessage key={turn.id} turn={turn} />)
              )}
            </div>

            <form className="flex gap-2" onSubmit={handleSubmit}>
              <Input
                aria-label="Assistant message"
                value={message}
                onChange={(event) => setMessage(event.target.value)}
                placeholder="Ask about your portfolio…"
                disabled={!hasSelectedAccount || isSending}
              />
              <Button
                type="submit"
                disabled={!hasSelectedAccount || message.trim().length === 0 || isSending}
              >
                Send
              </Button>
            </form>
          </CardContent>
        </Card>
      </DialogContent>
    </Dialog>
  )
}
