import { useEffect, useState } from 'react'
import type { KeyboardEvent } from 'react'
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
import { useConversationSummary } from '../hooks/useConversationSummary'
import { AssistantMessage } from './AssistantMessage'

export interface AssistantChatPopupProps {
  className?: string
}

export function AssistantChatPopup({ className }: AssistantChatPopupProps) {
  const isOpen = useAgentStore((state) => state.isOpen)
  const transcript = useAgentStore((state) => state.transcript)
  const open = useAgentStore((state) => state.open)
  const close = useAgentStore((state) => state.close)
  const startNewConversation = useAgentStore((state) => state.startNewConversation)
  const seedSummaryGreeting = useAgentStore((state) => state.seedSummaryGreeting)
  const suppressSummaryGreeting = useAgentStore((state) => state.suppressSummaryGreeting)
  const { error, hasSelectedAccount, isSending, sendMessage, clearError } = useAgentChat()
  const { summary, name } = useConversationSummary()
  const [message, setMessage] = useState('')

  const canSend = hasSelectedAccount && message.trim().length > 0 && !isSending

  // Flow F/G/I: when the popup opens onto an empty conversation, decide the opening
  // greeting. On a genuine open with a durable summary present, show the summary
  // (Flow G). After an explicit "New conversation" the summary is suppressed and the
  // user is greeted by name instead (Flow F -> Flow I) — starting a new conversation is
  // a deliberate fresh start that clears prior context. When no summary exists the name
  // greeting is shown too (Flow I). The store action no-ops once the transcript has
  // turns, avoiding duplicate greetings and preserving live conversations.
  useEffect(() => {
    if (!isOpen || transcript.length > 0) return

    if (summary && !suppressSummaryGreeting) {
      seedSummaryGreeting(summary)
      return
    }

    const firstName = name?.trim().split(/\s+/)[0]
    if (firstName) {
      seedSummaryGreeting(`Hello ${firstName}`)
    }
  }, [isOpen, suppressSummaryGreeting, transcript.length, summary, name, seedSummaryGreeting])

  async function handleSend() {
    clearError()

    const sent = await sendMessage(message)
    if (sent) {
      setMessage('')
    }
  }

  function handleInputKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'Enter' || event.shiftKey) return
    event.preventDefault()
    if (!canSend) return
    void handleSend()
  }

  // Flow F: start a new conversation from scratch. Wipes the in-memory transcript and
  // conversationId and clears prior context; the greeting effect then seeds the name
  // greeting (Flow I). The durable summary is untouched on the backend and reappears on
  // the next genuine empty open (page reload / restart), per Flow G.
  function handleNewConversation() {
    startNewConversation()
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
          <CardHeader className="flex flex-row items-center justify-between gap-2 pb-2">
            <CardTitle>Conversation</CardTitle>
            <Button variant="outline" size="sm" type="button" onClick={handleNewConversation}>
              New conversation
            </Button>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            {!hasSelectedAccount && (
              <Alert variant="warning">
                <AlertDescription>
                  Select an account in Portfolio before starting a chat.
                </AlertDescription>
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

            <div className="flex gap-2">
              <Input
                aria-label="Assistant message"
                value={message}
                onChange={(event) => setMessage(event.target.value)}
                onKeyDown={handleInputKeyDown}
                placeholder="Ask about your portfolio…"
                disabled={!hasSelectedAccount || isSending}
              />
              <Button type="button" onClick={handleSend} disabled={!canSend}>
                Send
              </Button>
            </div>
          </CardContent>
        </Card>
      </DialogContent>
    </Dialog>
  )
}
