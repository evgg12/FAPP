-- V8 lets a user pin selected transactions into a named group with notes -- for
-- example, tracking money a family member owes them. Purely organisational: nothing
-- here is read by analytics, categorisation or transfer detection, and no financial
-- fact about a transaction is touched.
--
-- Ownership is enforced the same way V2 enforces it elsewhere: composite foreign keys
-- carrying user_id, so the database itself refuses a group or a membership row that
-- belongs to two different users, rather than relying on the service layer alone.

CREATE TABLE pinned_groups (
    id         uuid          NOT NULL,
    user_id    uuid          NOT NULL,
    name       varchar(120)  NOT NULL,
    notes      varchar(2000) NULL,
    created_at timestamptz   NOT NULL DEFAULT now(),
    updated_at timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_pinned_groups PRIMARY KEY (id),
    CONSTRAINT fk_pinned_groups_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,

    CONSTRAINT ck_pinned_groups_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_pinned_groups_notes CHECK (notes IS NULL OR btrim(notes) <> ''),

    -- Composite-FK target: lets membership rows prove a group and a transaction share
    -- the same owner.
    CONSTRAINT uq_pinned_groups_id_user UNIQUE (id, user_id)
);

CREATE INDEX ix_pinned_groups_user ON pinned_groups (user_id);


-- One row per transaction pinned into a group. Deleting the group removes only these
-- rows -- the transactions themselves are untouched. Deleting a transaction removes its
-- membership rows via the same cascade, so a reverted import can never leave one
-- pointing at nothing.
CREATE TABLE pinned_group_transactions (
    id             uuid        NOT NULL,
    group_id       uuid        NOT NULL,
    user_id        uuid        NOT NULL,
    transaction_id uuid        NOT NULL,
    pinned_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_pinned_group_transactions PRIMARY KEY (id),

    CONSTRAINT fk_pgt_group FOREIGN KEY (group_id, user_id)
        REFERENCES pinned_groups (id, user_id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_pgt_transaction FOREIGN KEY (transaction_id, user_id)
        REFERENCES transactions (id, user_id) ON DELETE CASCADE ON UPDATE RESTRICT,

    -- A transaction may be pinned into the same group at most once.
    CONSTRAINT uq_pgt_group_transaction UNIQUE (group_id, transaction_id)
);

CREATE INDEX ix_pgt_group ON pinned_group_transactions (group_id);
CREATE INDEX ix_pgt_transaction ON pinned_group_transactions (transaction_id);
