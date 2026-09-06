-- V2 introduces the FAPP core financial domain: users, accounts, statement imports,
-- the unified transaction model and internal transfers.
--
-- Design notes that the DDL below depends on:
--
--  * Sign convention. `transactions.amount` is signed. Negative means money left the
--    user's financial position, positive means it entered. This is universal across
--    every account type: on a CREDIT_CARD a purchase is negative even though it
--    increases the balance owed. Adapters normalise each bank's representation into
--    this convention; no table stores a provider-specific sign.
--
--  * Provider independence. `provider` is a stable slug owned by the adapter that
--    produced the data. There is deliberately no providers table and no CHECK on its
--    *values* -- only on its format -- so registering a new bank never requires a
--    schema change. No other column in this migration refers to a bank.
--
--  * Composite foreign keys. Several FKs below carry redundant columns (user_id,
--    currency, account_id) so that ownership and currency invariants are enforced by
--    the database rather than by application discipline. Each one needs a matching
--    multi-column UNIQUE on the parent; those are marked "composite-FK target".
--
--  * Analytical date. `booking_date` is the sole canonical date for every calculation.
--    `occurred_on` is nullable and informational only, and deliberately has no
--    relational CHECK against booking_date.


CREATE TABLE users (
    id           uuid         NOT NULL,
    email        varchar(320) NOT NULL,
    display_name varchar(100) NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT ck_users_email CHECK (email = lower(email) AND position('@' IN email) > 1),
    CONSTRAINT ck_users_display_name CHECK (btrim(display_name) <> '')
);

-- Case-insensitive uniqueness. Authentication mechanics (credentials, tokens, roles)
-- are deliberately not part of this migration; users exists here only to own data.
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));


CREATE TABLE accounts (
    id           uuid         NOT NULL,
    user_id      uuid         NOT NULL,
    provider     varchar(40)  NOT NULL,
    display_name varchar(100) NOT NULL,
    account_type varchar(20)  NOT NULL,
    currency     char(3)      NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,

    CONSTRAINT ck_accounts_provider CHECK (provider ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT ck_accounts_display_name CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_accounts_type CHECK (account_type IN ('CURRENT', 'SAVINGS', 'CREDIT_CARD', 'OTHER')),
    CONSTRAINT ck_accounts_currency CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT uq_accounts_user_provider_name UNIQUE (user_id, provider, display_name),

    -- Composite-FK target: lets statement_imports pin the owning user.
    CONSTRAINT uq_accounts_id_user UNIQUE (id, user_id),
    -- Composite-FK target: lets transactions pin owner and currency in one constraint.
    CONSTRAINT uq_accounts_id_user_currency UNIQUE (id, user_id, currency)
);

CREATE INDEX ix_accounts_user ON accounts (user_id);


CREATE TABLE statement_imports (
    id              uuid        NOT NULL,
    user_id         uuid        NOT NULL,
    account_id      uuid        NOT NULL,
    provider        varchar(40) NOT NULL,
    content_hash    char(64)    NOT NULL,
    period_start    date        NOT NULL,
    period_end      date        NOT NULL,
    row_count       integer     NOT NULL,
    imported_count  integer     NOT NULL,
    duplicate_count integer     NOT NULL,
    imported_at     timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_statement_imports PRIMARY KEY (id),

    -- user_id is carried so the import cannot claim an account owned by someone else.
    CONSTRAINT fk_statement_imports_account FOREIGN KEY (account_id, user_id)
        REFERENCES accounts (id, user_id) ON DELETE CASCADE ON UPDATE RESTRICT,

    CONSTRAINT ck_statement_imports_provider CHECK (provider ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT ck_statement_imports_hash CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_statement_imports_period CHECK (period_start <= period_end),
    -- Import is atomic: a malformed statement is rejected whole, so every parsed data
    -- row is either imported or recognised as a duplicate. Nothing is ever skipped.
    CONSTRAINT ck_statement_imports_counts CHECK (
        row_count >= 0
        AND imported_count >= 0
        AND duplicate_count >= 0
        AND imported_count + duplicate_count = row_count
    ),

    -- A byte-identical re-upload of the same statement is rejected outright.
    CONSTRAINT uq_statement_imports_content UNIQUE (account_id, content_hash),

    -- Composite-FK target: lets transactions prove their import is for the same account.
    CONSTRAINT uq_statement_imports_id_account UNIQUE (id, account_id)
);

CREATE INDEX ix_statement_imports_user ON statement_imports (user_id, imported_at);


CREATE TABLE transactions (
    id                  uuid          NOT NULL,
    account_id          uuid          NOT NULL,
    -- Denormalised from accounts so every analytical query can scope by user without a
    -- join. Kept honest by fk_transactions_account, not by application discipline.
    user_id             uuid          NOT NULL,
    statement_import_id uuid          NOT NULL,

    booking_date        date          NOT NULL,
    occurred_on         date          NULL,

    amount              numeric(19,4) NOT NULL,
    currency            char(3)       NOT NULL,
    original_amount     numeric(19,4) NULL,
    original_currency   char(3)       NULL,

    description         varchar(500)  NOT NULL,
    merchant            varchar(200)  NULL,

    category            varchar(30)   NOT NULL,
    category_source     varchar(20)   NOT NULL,
    transaction_type    varchar(25)   NOT NULL,

    external_id         varchar(120)  NULL,
    fingerprint         char(64)      NOT NULL,
    fingerprint_version smallint      NOT NULL,
    occurrence          smallint      NOT NULL,

    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_transactions PRIMARY KEY (id),

    -- One constraint, two invariants: the transaction belongs to the claimed user, and
    -- its currency is the account's currency. ON UPDATE RESTRICT additionally freezes
    -- an account's owner and currency for as long as it holds transactions.
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id, user_id, currency)
        REFERENCES accounts (id, user_id, currency) ON DELETE CASCADE ON UPDATE RESTRICT,

    -- account_id is carried so a transaction cannot cite an import for another account.
    -- CASCADE supports a future "revert import" workflow; deletion is gated in the
    -- service layer so an import cannot be removed casually.
    CONSTRAINT fk_transactions_import FOREIGN KEY (statement_import_id, account_id)
        REFERENCES statement_imports (id, account_id) ON DELETE CASCADE ON UPDATE RESTRICT,

    CONSTRAINT ck_transactions_amount_nonzero CHECK (amount <> 0),
    CONSTRAINT ck_transactions_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- The foreign-currency leg is all-or-nothing, points the same way as the settled
    -- amount, and is only recorded when it is genuinely a different currency.
    CONSTRAINT ck_transactions_original_pair CHECK ((original_amount IS NULL) = (original_currency IS NULL)),
    CONSTRAINT ck_transactions_original_currency CHECK (
        original_currency IS NULL OR (original_currency ~ '^[A-Z]{3}$' AND original_currency <> currency)
    ),
    CONSTRAINT ck_transactions_original_sign CHECK (
        original_amount IS NULL OR sign(original_amount) = sign(amount)
    ),

    CONSTRAINT ck_transactions_description CHECK (btrim(description) <> ''),
    CONSTRAINT ck_transactions_merchant CHECK (merchant IS NULL OR btrim(merchant) <> ''),

    CONSTRAINT ck_transactions_category CHECK (category IN (
        'GROCERIES', 'RESTAURANTS', 'TRANSPORT', 'SUBSCRIPTIONS', 'BILLS', 'SHOPPING',
        'ENTERTAINMENT', 'INCOME', 'TRANSFER', 'SAVINGS', 'UNCATEGORISED'
    )),
    CONSTRAINT ck_transactions_category_source CHECK (category_source IN ('USER', 'RULE', 'ADAPTER', 'DEFAULT')),
    CONSTRAINT ck_transactions_type CHECK (transaction_type IN (
        'CARD_PAYMENT', 'DIRECT_DEBIT', 'STANDING_ORDER', 'BANK_TRANSFER',
        'CASH_WITHDRAWAL', 'FEE', 'INTEREST', 'REFUND', 'OTHER'
    )),

    CONSTRAINT ck_transactions_fingerprint CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_transactions_fingerprint_version CHECK (fingerprint_version >= 1),
    -- occurrence distinguishes genuinely identical same-day transactions (two coffees
    -- of the same price at the same merchant) from a re-imported duplicate.
    CONSTRAINT ck_transactions_occurrence CHECK (occurrence >= 1),
    CONSTRAINT ck_transactions_external_id CHECK (external_id IS NULL OR btrim(external_id) <> ''),

    CONSTRAINT uq_transactions_dedup UNIQUE (account_id, fingerprint, occurrence),

    -- Composite-FK target: lets transfers prove both legs belong to the same user.
    CONSTRAINT uq_transactions_id_user UNIQUE (id, user_id)
);

-- Partial, because Bank of Scotland statements carry no transaction id at all while
-- Monzo's are authoritative. A plain UNIQUE would collide on the nulls.
CREATE UNIQUE INDEX ux_transactions_external_id
    ON transactions (account_id, external_id) WHERE external_id IS NOT NULL;

CREATE INDEX ix_transactions_user_booking_date ON transactions (user_id, booking_date);
CREATE INDEX ix_transactions_account_booking_date ON transactions (account_id, booking_date);
CREATE INDEX ix_transactions_user_category_booking_date ON transactions (user_id, category, booking_date);
CREATE INDEX ix_transactions_import ON transactions (statement_import_id);


-- A move between two of the user's own accounts appears as a separate line in each
-- bank's statement, so it is stored as two faithful transactions linked here. Analytics
-- excludes linked legs from income and expenditure: without this a 500.00 move between
-- accounts would inflate both totals and corrupt net savings.
CREATE TABLE transfers (
    id                      uuid        NOT NULL,
    user_id                 uuid        NOT NULL,
    outgoing_transaction_id uuid        NOT NULL,
    incoming_transaction_id uuid        NOT NULL,
    detection_source        varchar(20) NOT NULL,
    detected_at             timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_transfers PRIMARY KEY (id),

    -- Both legs must belong to transfers.user_id, enforced by carrying user_id into
    -- both foreign keys. No direct FK to users is needed: it follows transitively.
    CONSTRAINT fk_transfers_outgoing FOREIGN KEY (outgoing_transaction_id, user_id)
        REFERENCES transactions (id, user_id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_transfers_incoming FOREIGN KEY (incoming_transaction_id, user_id)
        REFERENCES transactions (id, user_id) ON DELETE CASCADE ON UPDATE RESTRICT,

    CONSTRAINT ck_transfers_distinct_legs CHECK (outgoing_transaction_id <> incoming_transaction_id),
    CONSTRAINT ck_transfers_detection_source CHECK (detection_source IN ('RULE', 'USER')),

    -- A transaction can be a leg of at most one transfer.
    CONSTRAINT uq_transfers_outgoing UNIQUE (outgoing_transaction_id),
    CONSTRAINT uq_transfers_incoming UNIQUE (incoming_transaction_id)
);

CREATE INDEX ix_transfers_user ON transfers (user_id);

-- Deliberately NOT enforceable here, and therefore enforced in Transfer's factory with
-- unit tests: the legs must sit in different accounts, carry opposite signs and equal
-- magnitude in the same currency. All three are cross-row predicates.
