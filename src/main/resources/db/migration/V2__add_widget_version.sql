-- Optimistic locking for widgets: Hibernate increments `version` on every
-- update and rejects a write whose version no longer matches, so two
-- concurrent updates cannot silently overwrite each other. Existing rows start
-- at 0. Immutable once merged (Flyway checksum validation).
ALTER TABLE widgets ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
