-- V10 lets a user feature more than one savings goal at a time.
--
-- The partial unique index from V7 enforced "at most one featured goal per user" as a
-- database guarantee. That constraint no longer holds, so it is dropped; the featured
-- column itself is unchanged.

DROP INDEX uq_savings_goals_user_featured;
