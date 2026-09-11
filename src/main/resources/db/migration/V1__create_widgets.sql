-- Story 2.1: widgets table. Application-generated UUIDs (AD-8) -- no default
-- on id, the app always supplies one. Immutable once merged: Flyway checksum
-- validation (validate-on-migrate: true) fails startup on any edit.
CREATE TABLE widgets (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
