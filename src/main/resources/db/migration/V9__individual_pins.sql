-- V9 lets a transaction be pinned individually, with an optional short note, without
-- belonging to any group -- for example, flagging one payment worth remembering without
-- setting up a whole group for it.
--
-- An individual pin is represented by the same membership row a group pin uses, with
-- group_id left null: it is a pin with no group, not a different kind of fact, so it does
-- not need a second table. user_id is already its own column on this table (V8), so
-- ownership stays enforced the same way for both kinds of row.
--
-- fk_pgt_group uses the default MATCH SIMPLE, which skips a foreign key check entirely
-- when any of its columns is null -- so a null group_id is never checked against
-- pinned_groups, and group membership's own ownership guarantee is unaffected.

ALTER TABLE pinned_group_transactions
    ALTER COLUMN group_id DROP NOT NULL,
    ADD COLUMN note varchar(500) NULL;

ALTER TABLE pinned_group_transactions
    ADD CONSTRAINT ck_pgt_note CHECK (note IS NULL OR btrim(note) <> '');

-- A transaction may be pinned individually at most once. This is a separate invariant
-- from uq_pgt_group_transaction: that constraint still stops a transaction being added
-- twice to the *same* group, and is untouched by this migration.
CREATE UNIQUE INDEX uq_pgt_individual_transaction
    ON pinned_group_transactions (user_id, transaction_id) WHERE group_id IS NULL;

-- Individual pins and "is this transaction pinned at all" both filter by user_id alone.
CREATE INDEX ix_pgt_user ON pinned_group_transactions (user_id);
