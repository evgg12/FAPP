-- V3 adds savings goals: a target amount, a date to reach it by, and how far along the
-- user is.
--
-- Progress is a stored figure the user maintains rather than something derived from a
-- designated savings account. That is what the specification describes ("Target: 8,000,
-- Current amount: 2,350"), and it keeps a goal independent of whether the user happens
-- to have a separate account for it. Projections are calculated from transaction history
-- by the simulator; nothing about a goal's progress is inferred here.
--
-- One currency per goal, held once: the target and the current amount are necessarily in
-- the same currency, so there is a single column rather than two that could disagree.

CREATE TABLE savings_goals (
    id             uuid          NOT NULL,
    user_id        uuid          NOT NULL,
    name           varchar(100)  NOT NULL,
    target_amount  numeric(19,4) NOT NULL,
    current_amount numeric(19,4) NOT NULL,
    currency       char(3)       NOT NULL,
    target_date    date          NOT NULL,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_savings_goals PRIMARY KEY (id),
    CONSTRAINT fk_savings_goals_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,

    CONSTRAINT ck_savings_goals_name CHECK (btrim(name) <> ''),
    -- A goal of nothing is not a goal.
    CONSTRAINT ck_savings_goals_target CHECK (target_amount > 0),
    -- Saved nothing yet is fine; owing the goal money is not.
    CONSTRAINT ck_savings_goals_current CHECK (current_amount >= 0),
    CONSTRAINT ck_savings_goals_currency CHECK (currency ~ '^[A-Z]{3}$'),

    -- Two goals called "Car Fund" would be indistinguishable to the person who set them.
    CONSTRAINT uq_savings_goals_user_name UNIQUE (user_id, name)
);

CREATE INDEX ix_savings_goals_user ON savings_goals (user_id, target_date);
