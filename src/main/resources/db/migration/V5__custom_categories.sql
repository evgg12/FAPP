-- Lets a user file a transaction under a name of their own, without loosening the
-- taxonomy for everything else.
--
-- The enum stays closed and gains one member, CUSTOM, which is the only value that
-- carries a free-text label. Analytics can therefore still prove that a category
-- breakdown accounts for every row: a custom transaction groups by its label, and
-- everything else groups by a value the CHECK constraint fixes.

ALTER TABLE transactions
    ADD COLUMN custom_category varchar(40);

ALTER TABLE transactions
    DROP CONSTRAINT ck_transactions_category;

ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_category CHECK (category IN (
        'GROCERIES', 'RESTAURANTS', 'TRANSPORT', 'SUBSCRIPTIONS', 'BILLS', 'SHOPPING',
        'ENTERTAINMENT', 'INCOME', 'TRANSFER', 'SAVINGS', 'CUSTOM', 'UNCATEGORISED'
    ));

-- A label exists exactly when the category is CUSTOM, and is never blank. Written as an
-- equality between two booleans so neither half can drift from the other.
ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_custom_category CHECK (
        (category = 'CUSTOM') = (custom_category IS NOT NULL)
        AND (custom_category IS NULL OR btrim(custom_category) <> '')
    );

CREATE INDEX ix_transactions_user_custom_category
    ON transactions (user_id, custom_category)
    WHERE custom_category IS NOT NULL;
