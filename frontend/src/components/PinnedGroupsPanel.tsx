import { useState } from 'react'
import { ApiError, api } from '../api/client'
import type { PinnedGroup, PinnedTransaction } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { Async, ErrorNotice } from './Async'
import { day, money } from '../format'

type Mode = 'individual' | 'groups'

/**
 * Everything pinned, in one compact panel. Individual pins and groups share the same
 * space rather than two permanent panels -- the two small controls next to the heading
 * switch which is shown, and creating a group reveals a small inline form in place
 * rather than sitting on the dashboard as its own section or opening a modal.
 *
 * The pinned/group list itself scrolls inside `.pinned-scroll`, capped to a compact
 * height, so pinning more things doesn't grow the panel unboundedly. The heading,
 * toggle, and create-group control/form sit outside it and are always shown in full.
 */
export function PinnedGroupsPanel({
  userId,
  version,
  onChanged,
}: {
  userId: string
  version: number
  onChanged: () => void
}) {
  const [mode, setMode] = useState<Mode>('individual')
  const [creating, setCreating] = useState(false)

  const individual = useAsync(() => api.individualPins(userId), [userId, version])
  const groups = useAsync(() => api.pinnedGroups(userId), [userId, version])

  return (
    <section className="panel pinned-panel">
      <div className="panel-head">
        <h2>Pinned</h2>
        <div className="row row-tight">
          <button
            type="button"
            className={mode === 'individual' ? 'chip chip-on' : 'chip'}
            aria-pressed={mode === 'individual'}
            onClick={() => setMode('individual')}
          >
            Individual
          </button>
          <button
            type="button"
            className={mode === 'groups' ? 'chip chip-on' : 'chip'}
            aria-pressed={mode === 'groups'}
            onClick={() => setMode('groups')}
          >
            Groups
          </button>
          {mode === 'groups' && !creating && (
            <button
              type="button"
              className="btn-icon"
              aria-label="Create pinned group"
              onClick={() => setCreating(true)}
            >
              +
            </button>
          )}
        </div>
      </div>

      {creating && (
        <CreateGroupForm
          userId={userId}
          onCreated={() => {
            onChanged()
            setCreating(false)
          }}
          onCancel={() => setCreating(false)}
        />
      )}

      <div className="pinned-scroll">
        {mode === 'individual' ? (
          <Async state={individual} empty="No individually pinned transactions yet." lines={3}>
            {(pins) => <IndividualList userId={userId} pins={pins} onChanged={onChanged} />}
          </Async>
        ) : (
          <Async state={groups} empty="No pinned groups yet." lines={3}>
            {(list) => (
              <div className="stack">
                {list.map((group) => (
                  <PinnedGroupCard key={group.id} userId={userId} group={group} onChanged={onChanged} />
                ))}
              </div>
            )}
          </Async>
        )}
      </div>
    </section>
  )
}

function CreateGroupForm({
  userId,
  onCreated,
  onCancel,
}: {
  userId: string
  onCreated: () => void
  onCancel: () => void
}) {
  const [name, setName] = useState('')
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await api.createPinnedGroup(userId, name.trim())
      onCreated()
    } catch (caught) {
      setError(caught as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="inline-form inline-form-row" aria-label="Create pinned group">
      {error && <ErrorNotice error={error} />}
      <input
        value={name}
        required
        maxLength={120}
        placeholder="Group name, e.g. IOU: Sam"
        aria-label="Group name"
        onChange={(e) => setName(e.target.value)}
      />
      <div className="row row-tight">
        <button type="submit" disabled={busy}>
          {busy ? 'Creating…' : 'Create'}
        </button>
        <button type="button" className="btn-quiet" disabled={busy} onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}

function IndividualList({
  userId,
  pins,
  onChanged,
}: {
  userId: string
  pins: PinnedTransaction[]
  onChanged: () => void
}) {
  const [openId, setOpenId] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [error, setError] = useState<ApiError | null>(null)

  async function unpin(transactionId: string) {
    setError(null)
    setBusyId(transactionId)
    try {
      await api.unpinIndividually(userId, transactionId)
      onChanged()
    } catch (caught) {
      setError(caught as ApiError)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="stack">
      {error && <ErrorNotice error={error} />}
      <ul className="stack pin-list" aria-label="Individually pinned transactions">
        {pins.map((pin) => {
          const name = pin.merchant ?? pin.description
          const expanded = openId === pin.transactionId
          return (
            <li key={pin.transactionId}>
              <div className="spread pin-entry">
                <button
                  type="button"
                  className="btn-quiet pin-entry-name"
                  onClick={() => setOpenId(expanded ? null : pin.transactionId)}
                  aria-expanded={expanded}
                >
                  {day(pin.bookingDate)} · {name}
                </button>
                <span className={`pin-amount ${pin.amount < 0 ? 'tone-down' : 'tone-up'}`}>
                  {money(pin.amount, pin.currency)}
                </span>
                <button
                  type="button"
                  className="btn-quiet"
                  disabled={busyId === pin.transactionId}
                  aria-label={`Unpin ${name}`}
                  onClick={() => void unpin(pin.transactionId)}
                >
                  Unpin
                </button>
              </div>
              {expanded && <p className="muted">{pin.note ?? 'No note.'}</p>}
            </li>
          )
        })}
      </ul>
    </div>
  )
}

function PinnedGroupCard({
  userId,
  group,
  onChanged,
}: {
  userId: string
  group: PinnedGroup
  onChanged: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(group.name)
  const [notes, setNotes] = useState(group.notes ?? '')
  const [expanded, setExpanded] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function run(action: () => Promise<unknown>) {
    setError(null)
    setBusy(true)
    try {
      await action()
      onChanged()
    } catch (caught) {
      setError(caught as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <article className="pinned-group-card">
      <div className="spread">
        {editing ? (
          <input
            value={name}
            maxLength={120}
            aria-label="Group name"
            onChange={(e) => setName(e.target.value)}
          />
        ) : (
          <button
            type="button"
            className="btn-quiet"
            onClick={() => setExpanded((value) => !value)}
            aria-expanded={expanded}
          >
            {group.name} <span className="muted">({group.transactions.length})</span>
          </button>
        )}
        <div className="row row-tight">
          <button
            type="button"
            className="btn-quiet"
            disabled={busy}
            onClick={() => setEditing((value) => !value)}
          >
            {editing ? 'Cancel' : 'Rename / notes'}
          </button>
          <button
            type="button"
            className="btn-danger"
            disabled={busy}
            onClick={() => {
              if (window.confirm(`Delete the group "${group.name}"?`)) {
                void run(() => api.deletePinnedGroup(userId, group.id))
              }
            }}
          >
            Delete
          </button>
        </div>
      </div>

      {editing ? (
        <div className="stack">
          <label>
            Notes
            <textarea
              value={notes}
              maxLength={2000}
              aria-label={`Notes for ${group.name}`}
              onChange={(e) => setNotes(e.target.value)}
            />
          </label>
          <button
            type="button"
            className="btn-quiet"
            disabled={busy || name.trim() === ''}
            onClick={() =>
              run(async () => {
                await api.updatePinnedGroup(userId, group.id, name.trim(), notes.trim() || undefined)
                setEditing(false)
              })
            }
          >
            {busy ? 'Saving…' : 'Save'}
          </button>
        </div>
      ) : (
        expanded && group.notes && <p className="muted">{group.notes}</p>
      )}

      {error && <ErrorNotice error={error} />}

      {expanded &&
        (group.transactions.length === 0 ? (
          <p className="empty">No transactions pinned yet.</p>
        ) : (
          <ul className="stack" aria-label={`Transactions in ${group.name}`}>
            {group.transactions.map((transaction) => (
              <li key={transaction.transactionId} className="spread pin-entry">
                <span className="pin-entry-name">
                  {day(transaction.bookingDate)} · {transaction.merchant ?? transaction.description}
                </span>
                <span className={`pin-amount ${transaction.amount < 0 ? 'tone-down' : 'tone-up'}`}>
                  {money(transaction.amount, transaction.currency)}
                </span>
                <button
                  type="button"
                  className="btn-quiet"
                  disabled={busy}
                  aria-label={`Remove ${transaction.merchant ?? transaction.description} from ${group.name}`}
                  onClick={() =>
                    run(() => api.removePinnedTransaction(userId, group.id, transaction.transactionId))
                  }
                >
                  Remove
                </button>
              </li>
            ))}
          </ul>
        ))}
    </article>
  )
}
