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
  const { summary } = useConversationSummary()
  const [message, setMessage] = useState('')

  const canSend = hasSelectedAccount && message.trim().length > 0 && !isSending

  // Flow G: when the popup opens onto an empty conversation and a durable summary
  // exists, present it as the opening assistant message. Suppressed right after an
  // explicit "New conversation" so that deliberately starting fresh opens on a
  // blank history. The store action also no-ops once the transcript has real turns,
  // so the greeting is never duplicated and never overwrites a live conversation.
  // When summary is null (first-ever conversation) the popup opens empty.
  useEffect(() => {
    if (isOpen && !suppressSummaryGreeting && transcript.length === 0 && summary) {
      seedSummaryGreeting(summary)
    }
  }, [isOpen, suppressSummaryGreeting, transcript.length, summary, seedSummaryGreeting])

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

  // Flow F: start a new conversation from scratch. Wipes the in-memory transcript
  // and conversationId and opens on a blank history — the carried-forward summary
  // is deliberately NOT shown here (it reappears only when the assistant is opened
  // fresh, per Flow G).
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
