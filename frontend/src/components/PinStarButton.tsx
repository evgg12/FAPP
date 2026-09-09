import { useState } from 'react'
import { ApiError, api } from '../api/client'
import type { PinnedGroup } from '../api/types'
import { ErrorNotice } from './Async'

type Step = 'closed' | 'choose' | 'note' | 'group'

/**
 * The star on a transaction row. Unpinned, clicking it reveals a small inline control,
 * attached to the row, offering to pin the transaction -- individually, or into an
 * existing group if the user has any. Pinned (which here means pinned individually,
 * since only an individual pin is a one-transaction fact this button alone can undo),
 * clicking it unpins.
 *
 * Groups are loaded lazily, only once the star is clicked, so a row with a star costs
 * nothing extra until it is used.
 */
export function PinStarButton({
  userId,
  transactionId,
  label,
  pinned,
  onChanged,
}: {
  userId: string
  transactionId: string
  label: string
  pinned: boolean
  onChanged: () => void
}) {
  const [step, setStep] = useState<Step>('closed')
  const [groups, setGroups] = useState<PinnedGroup[]>([])
  const [selectedGroup, setSelectedGroup] = useState('')
  const [note, setNote] = useState('')
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  function close() {
    setStep('closed')
    setNote('')
    setError(null)
  }

  async function openForPinning() {
    setError(null)
    setBusy(true)
    try {
      const list = await api.pinnedGroups(userId)
      setGroups(list)
      setSelectedGroup(list[0]?.id ?? '')
      setStep(list.length === 0 ? 'note' : 'choose')
    } catch (caught) {
      setError(caught as ApiError)
      setStep('note')
    } finally {
      setBusy(false)
    }
  }

  async function unpin() {
    setError(null)
    setBusy(true)
    try {
      await api.unpinIndividually(userId, transactionId)
      onChanged()
    } catch (caught) {
      setError(caught as ApiError)
    } finally {
      setBusy(false)
    }
  }

  async function savePinIndividually() {
    setError(null)
    setBusy(true)
    try {
      await api.pinIndividually(userId, transactionId, note.trim() || undefined)
      close()
      onChanged()
    } catch (caught) {
      setError(caught as ApiError)
    } finally {
      setBusy(false)
    }
  }

  async function addToGroup() {
    if (!selectedGroup) {
      return
    }
    setError(null)
    setBusy(true)
    try {
      await api.addPinnedTransactions(userId, selectedGroup, [transactionId])
      close()
      onChanged()
    } catch (caught) {
      setError(caught as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <span className="pin-control">
      <button
        type="button"
        className={pinned ? 'btn-star btn-star-active' : 'btn-star'}
        aria-pressed={pinned}
        aria-label={pinned ? `Unpin ${label}` : `Pin ${label}`}
        disabled={busy}
        onClick={() => (pinned ? void unpin() : void openForPinning())}
      >
        {pinned ? '★' : '☆'}
      </button>

      {step !== 'closed' && (
        <div className="pin-popover" role="group" aria-label={`Pin ${label}`}>
          {error && <ErrorNotice error={error} />}

          {step === 'choose' && (
            <div className="row row-tight">
              <button type="button" className="btn-quiet" disabled={busy} onClick={() => setStep('note')}>
                Pin individually
              </button>
              <button type="button" className="btn-quiet" disabled={busy} onClick={() => setStep('group')}>
                Add to a group
              </button>
              <button type="button" className="btn-quiet" disabled={busy} onClick={close}>
                Cancel
              </button>
            </div>
          )}

          {step === 'note' && (
            <div className="stack">
              <label>
                Description (optional)
                <input
                  value={note}
                  maxLength={500}
                  placeholder="What this is for"
                  onChange={(e) => setNote(e.target.value)}
                />
              </label>
              <div className="row row-tight">
                <button type="button" disabled={busy} onClick={() => void savePinIndividually()}>
                  {busy ? 'Pinning…' : 'Pin'}
                </button>
                <button type="button" className="btn-quiet" disabled={busy} onClick={close}>
                  Cancel
                </button>
              </div>
            </div>
          )}

          {step === 'group' && (
            <div className="stack">
              <label>
                Group
                <select value={selectedGroup} onChange={(e) => setSelectedGroup(e.target.value)}>
                  {groups.map((group) => (
                    <option key={group.id} value={group.id}>
                      {group.name}
                    </option>
                  ))}
                </select>
              </label>
              <div className="row row-tight">
                <button type="button" disabled={busy || !selectedGroup} onClick={() => void addToGroup()}>
                  {busy ? 'Adding…' : 'Add'}
                </button>
                <button type="button" className="btn-quiet" disabled={busy} onClick={close}>
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </span>
  )
}
