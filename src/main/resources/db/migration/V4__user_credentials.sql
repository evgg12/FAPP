-- V4 gives users a password so they can authenticate.
--
-- Only the hash is stored, and only ever the hash: the column is sized for a modern
-- encoder's output including its {id} prefix, so the algorithm a hash was produced with
-- travels with it and can be upgraded later without a migration.
--
-- Nullable on purpose. A user created before this migration, or created by an internal
-- path that sets no credential, has no password and therefore cannot sign in — which is
-- the safe answer. Making it NOT NULL would have required inventing a hash for existing
-- rows, and a known hash is worse than no hash.

ALTER TABLE users ADD COLUMN password_hash varchar(100);

-- Absent is meaningful; blank is not.
ALTER TABLE users ADD CONSTRAINT ck_users_password_hash
    CHECK (password_hash IS NULL OR btrim(password_hash) <> '');
