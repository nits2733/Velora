-- V9 declared both token_hash columns as CHAR(64); the entities map them as
-- @Column(length = 64), which Hibernate expects to be VARCHAR, so schema validation
-- refused to start against a freshly migrated database.
--
-- VARCHAR is the correct type regardless: CHAR blank-pads every value to the full
-- width, and a padded hash would not match the digest computed at lookup time.
-- Both tables are empty at this point - V9 created them - so this rewrites nothing.

ALTER TABLE refresh_tokens
    ALTER COLUMN token_hash TYPE VARCHAR(64);

ALTER TABLE password_reset_tokens
    ALTER COLUMN token_hash TYPE VARCHAR(64);
