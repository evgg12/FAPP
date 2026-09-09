import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { PinnedGroup, PinnedTransaction } from '../api/types'
import { PinnedGroupsPanel } from './PinnedGroupsPanel'
import { PinStarButton } from './PinStarButton'

afterEach(() => {
  vi.restoreAllMocks()
})

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const NOTE_PIN: PinnedTransaction = {
  transactionId: 't1',
  accountId: 'a1',
  bookingDate: '2026-08-03',
  amount: -24.15,
  currency: 'GBP',
  description: 'GREENFIELD GROCERS 4821',
  merchant: 'Greenfield Grocers',
  category: 'GROCERIES',
  note: 'Lent for the school trip',
  pinnedAt: '2026-01-02T00:00:00Z',
}

const IOU_GROUP: PinnedGroup = {
  id: 'g1',
  userId: 'u1',
  name: 'IOU: Sam',
  notes: 'Lent for the school trip',
  transactions: [
    {
      transactionId: 't2',
      accountId: 'a1',
      bookingDate: '2026-08-07',
      amount: -10,
      currency: 'GBP',
      description: 'CORNER SHOP',
      category: 'SHOPPING',
      pinnedAt: '2026-01-02T00:00:00Z',
    },
  ],
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function mockPinnedFetch({
  individual = [],
  groups = [],
}: {
  individual?: PinnedTransaction[]
  groups?: PinnedGroup[]
} = {}) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((url) => {
    const href = String(url)
    if (href.includes('/pinned-groups')) {
      return Promise.resolve(jsonResponse(groups))
    }
    if (href.includes('/pinned-transactions')) {
      return Promise.resolve(jsonResponse(individual))
    }
    return Promise.resolve(jsonResponse([]))
  })
}

describe('pinned panel', () => {
  it('defaults to the Individual view and shows its empty state', async () => {
    mockPinnedFetch()

    await act(async () => {
      render(<PinnedGroupsPanel userId="u1" version={0} onChanged={() => {}} />)
    })

    expect(screen.getByRole('button', { name: 'Individual' }).getAttribute('aria-pressed')).toBe('true')
    expect(screen.getByText('No individually pinned transactions yet.')).toBeTruthy()
  })

  it('shows individually pinned transactions, compactly, with the note hidden until opened', async () => {
    mockPinnedFetch({ individual: [NOTE_PIN] })

    await act(async () => {
      render(<PinnedGroupsPanel userId="u1" version={0} onChanged={() => {}} />)
    })

    expect(screen.getByText(/Greenfield Grocers/)).toBeTruthy()
    expect(screen.queryByText('Lent for the school trip')).toBeNull()

    await act(async () => {
      screen.getByRole('button', { name: /03 Aug 2026/ }).click()
    })

    expect(screen.getByText('Lent for the school trip')).toBeTruthy()
  })

  it('unpins an individual transaction without deleting it', async () => {
    let pins = [NOTE_PIN]
    const fetching = vi.spyOn(globalThis, 'fetch').mockImplementation((url, init) => {
      const href = String(url)
      const method = (init as RequestInit | undefined)?.method ?? 'GET'
      if (method === 'DELETE') {
        pins = []
        return Promise.resolve(jsonResponse(undefined, 204))
      }
      if (href.includes('/pinned-groups')) {
        return Promise.resolve(jsonResponse([]))
      }
      return Promise.resolve(jsonResponse(pins))
    })
    const onChanged = vi.fn()

    await act(async () => {
      render(<PinnedGroupsPanel userId="u1" version={0} onChanged={onChanged} />)
    })

    await act(async () => {
      screen.getByRole('button', { name: 'Unpin Greenfield Grocers' }).click()
    })

    expect(fetching).toHaveBeenCalledWith(
      '/api/users/u1/pinned-transactions/t1',
      expect.objectContaining({ method: 'DELETE' }),
    )
    expect(onChanged).toHaveBeenCalled()
  })

  it('switches to the Groups view and shows pinned groups', async () => {
    mockPinnedFetch({ groups: [IOU_GROUP] })

    await act(async () => {
      render(<PinnedGroupsPanel userId="u1" version={0} onChanged={() => {}} />)
    })

    await act(async () => {
      screen.getByRole('button', { name: 'Groups' }).click()
    })

    expect(screen.getByText(/IOU: Sam/)).toBeTruthy()
  })

  it('expands a group to rename it, edit its notes, and remove a transaction', async () => {
    let group = IOU_GROUP
    const fetching = vi.spyOn(globalThis, 'fetch').mockImplementation((url, init) => {
      const href = String(url)
      const method = (init as RequestInit | undefined)?.method ?? 'GET'
      if (method === 'PUT') {
        group = { ...group, name: 'IOU: Samantha', notes: 'Paid back half' }
        return Promise.resolve(jsonResponse(group))
      }
      if (method === 'DELETE' && href.includes('/transactions/')) {
        group = { ...group, transactions: [] }
        return Promise.resolve(jsonResponse(undefined, 204))
      }
      if (href.includes('/pinned-groups')) {
        return Promise.resolve(jsonResponse([group]))
      }
      return Promise.resolve(jsonResponse([]))
    })

    await act(async () => {
      render(<PinnedGroupsPanel userId="u1" version={0} onChanged={() => {}} />)
    })
    await act(async () => {
      screen.getByRole('button', { name: 'Groups' }).click()
    })
    await act(async () => {
      screen.getByRole('button', { name: /IOU: Sam/ }).click()
    })

    expect(screen.getByText(/CORNER SHOP/)).toBeTruthy()

    await act(async () => {
      screen.getByRole('button', { name: 'Remove CORNER SHOP from IOU: Sam' }).click()
    })
    expect(fetching).toHaveBeenCalledWith(
      '/api/users/u1/pinned-groups/g1/transactions/t2',
      expect.objectContaining({ method: 'DELETE' }),
    )

    await act(async () => {
      screen.getByRole('button', { name: 'Rename / notes' }).click()
    })
    const nameInput = screen.getByLabelText('Group name')
    await act(async () => {
      fireEvent.change(nameInput, { target: { value: 'IOU: Samantha' } })
    })
    await act(async () => {
      screen.getByRole('button', { name: 'Save' }).click()
    })

    expect(fetching).toHaveBeenCalledWith(
      '/api/users/u1/pinned-groups/g1',
      expect.objectContaining({ method: 'PUT' }),
    )
  })

  it('deletes a group without deleting its transactions', async () => {
    const fetching = vi.spyOn(globalThis, 'fetch').mockImplementation((url, init) => {
      const href = String(url)
      const method = (init as RequestInit | undefined)?.method ?? 'GET'
      if (method === 'DELETE') {
        return Promise.resolve(jsonResponse(undefined, 204))
      }
      if (href.includes('/pinned-groups')) {
        return Promise.resolve(jsonResponse([IOU_GROUP]))
      }
      return Promise.resolve(jsonResponse([]))
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    await act(async () => {
      render(<PinnedGroupsPanel userId="u1" version={0} onChanged={() => {}} />)
    })
    await act(async () => {
      screen.getByRole('button', { name: 'Groups' }).click()
    })
    await act(async () => {
      screen.getByRole('button', { name: 'Delete' }).click()
    })

    expect(fetching).toHaveBeenCalledWith(
      '/api/users/u1/pinned-groups/g1',
      expect.objectContaining({ method: 'DELETE' }),
    )
  })

  it('reveals an inline form (not a dialog) to create a group and refreshes afterwards', async () => {
    let groups: PinnedGroup[] = []
    const fetching = vi.spyOn(globalThis, 'fetch').mockImplementation((url, init) => {
      const href = String(url)
      const method = (init as RequestInit | undefined)?.method ?? 'GET'
      if (method === 'POST') {
        const created = { ...IOU_GROUP, id: 'g2', name: 'New Group', notes: undefined, transactions: [] }
        groups = [...groups, created]
        return Promise.resolve(jsonResponse(created, 201))
      }
      if (href.includes('/pinned-groups')) {
        return Promise.resolve(jsonResponse(groups))
      }
      return Promise.resolve(jsonResponse([]))
    })
    const onChanged = vi.fn()

    await act(async () => {
      render(<PinnedGroupsPanel userId="u1" version={0} onChanged={onChanged} />)
    })

    expect(screen.queryByRole('dialog')).toBeNull()

    await act(async () => {
      screen.getByRole('button', { name: 'Groups' }).click()
    })
    await act(async () => {
      screen.getByRole('button', { name: 'Create pinned group' }).click()
    })
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Close' })).toBeNull()

    const nameInput = screen.getByPlaceholderText('Group name, e.g. IOU: Sam')
    await act(async () => {
      fireEvent.change(nameInput, { target: { value: 'New Group' } })
    })
    await act(async () => {
      screen.getByRole('button', { name: 'Create' }).click()
    })

    expect(fetching).toHaveBeenCalledWith(
      '/api/users/u1/pinned-groups',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(onChanged).toHaveBeenCalled()
    // Back to the normal panel, with the "Create pinned group" trigger restored.
    expect(screen.getByRole('button', { name: 'Create pinned group' })).toBeTruthy()
  })

  it('keeps the create-group form fully visible outside the scrollable list, never clipped or scrolled', async () => {
    mockPinnedFetch()

    await act(async () => {
      render(<PinnedGroupsPanel userId="u1" version={0} onChanged={() => {}} />)
    })

    await act(async () => {
      screen.getByRole('button', { name: 'Groups' }).click()
    })
    await act(async () => {
      screen.getByRole('button', { name: 'Create pinned group' }).click()
    })

    // Only the pinned/group list scrolls (capped by .pinned-scroll). The heading,
    // toggle, and create-group form must never end up inside that capped, scrollable
    // region, or they'd be clipped.
    const form = screen.getByRole('form', { name: 'Create pinned group' })
    expect(form.closest('.pinned-scroll')).toBeNull()

    expect(screen.getByRole('heading', { name: 'Pinned' }).closest('.pinned-scroll')).toBeNull()
    expect(screen.getByRole('button', { name: 'Individual' }).closest('.pinned-scroll')).toBeNull()
  })
})

describe('pin star button', () => {
  it('shows filled when pinned and hollow when not', () => {
    render(<PinStarButton userId="u1" transactionId="t1" label="Greenfield Grocers" pinned onChanged={() => {}} />)
    expect(screen.getByRole('button', { name: 'Unpin Greenfield Grocers' }).getAttribute('aria-pressed')).toBe('true')
  })

  it('unpins directly when the star is already filled', async () => {
    const fetching = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(undefined, 204))
    const onChanged = vi.fn()

    render(
      <PinStarButton userId="u1" transactionId="t1" label="Greenfield Grocers" pinned onChanged={onChanged} />,
    )
    await act(async () => {
      screen.getByRole('button', { name: 'Unpin Greenfield Grocers' }).click()
    })

    expect(fetching).toHaveBeenCalledWith(
      '/api/users/u1/pinned-transactions/t1',
      expect.objectContaining({ method: 'DELETE' }),
    )
    expect(onChanged).toHaveBeenCalled()
  })

  it('pins individually straight away when the user has no groups, via a contextual control (not a modal)', async () => {
    const fetching = vi.spyOn(globalThis, 'fetch').mockImplementation((url) => {
      const href = String(url)
      if (href.includes('/pinned-groups')) {
        return Promise.resolve(jsonResponse([]))
      }
      return Promise.resolve(jsonResponse({ transactionId: 't1' }))
    })
    const onChanged = vi.fn()

    render(
      <PinStarButton
        userId="u1"
        transactionId="t1"
        label="Greenfield Grocers"
        pinned={false}
        onChanged={onChanged}
      />,
    )
    await act(async () => {
      screen.getByRole('button', { name: 'Pin Greenfield Grocers' }).click()
    })

    expect(screen.queryByRole('dialog')).toBeNull()
    // No groups exist, so the description step is offered directly -- no "choose" step shown.
    expect(await screen.findByPlaceholderText('What this is for')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Add to a group' })).toBeNull()

    await act(async () => {
      screen.getByRole('button', { name: 'Pin' }).click()
    })

    expect(fetching).toHaveBeenCalledWith(
      '/api/users/u1/pinned-transactions',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ transactionId: 't1', note: undefined }) }),
    )
    expect(onChanged).toHaveBeenCalled()
  })

  it('offers a choice between pinning individually and adding to a group when groups exist', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((url) => {
      const href = String(url)
      if (href.includes('/pinned-groups')) {
        return Promise.resolve(jsonResponse([IOU_GROUP]))
      }
      return Promise.resolve(jsonResponse({}))
    })

    render(
      <PinStarButton userId="u1" transactionId="t1" label="Greenfield Grocers" pinned={false} onChanged={() => {}} />,
    )
    await act(async () => {
      screen.getByRole('button', { name: 'Pin Greenfield Grocers' }).click()
    })

    expect(await screen.findByRole('button', { name: 'Pin individually' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Add to a group' })).toBeTruthy()
  })

  it('adds the transaction to the chosen group', async () => {
    const fetching = vi.spyOn(globalThis, 'fetch').mockImplementation((url) => {
      const href = String(url)
      if (href.includes('/pinned-groups') && !href.includes('/transactions')) {
        return Promise.resolve(jsonResponse([IOU_GROUP]))
      }
      return Promise.resolve(jsonResponse(IOU_GROUP))
    })
    const onChanged = vi.fn()

    render(
      <PinStarButton userId="u1" transactionId="t1" label="Greenfield Grocers" pinned={false} onChanged={onChanged} />,
    )
    await act(async () => {
      screen.getByRole('button', { name: 'Pin Greenfield Grocers' }).click()
    })
    await act(async () => {
      screen.getByRole('button', { name: 'Add to a group' }).click()
    })
    await act(async () => {
      screen.getByRole('button', { name: 'Add' }).click()
    })

    expect(fetching).toHaveBeenCalledWith(
      '/api/users/u1/pinned-groups/g1/transactions',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ transactionIds: ['t1'] }) }),
    )
    expect(onChanged).toHaveBeenCalled()
  })
})
