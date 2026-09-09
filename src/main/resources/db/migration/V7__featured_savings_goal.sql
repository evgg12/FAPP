-- V7 lets a user mark one savings goal as featured, for the dashboard to highlight.
--
-- Featuring is explicit: nothing here selects a goal automatically, so a user who has
-- never marked one sees none featured. The partial unique index is what makes "at most
-- one featured goal per user" a database guarantee rather than something the service has
-- to get right under concurrency.

ALTER TABLE savings_goals ADD COLUMN featured boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX uq_savings_goals_user_featured ON savings_goals (user_id) WHERE featured;
