-- Backing columns for the shared Auditable @MappedSuperclass (createdAt/updatedAt).
-- Existing rows get "now" as their created/updated timestamp since no real
-- history is available to backfill; acceptable for retrofitting audit
-- columns onto a table with no prior audit trail.
--
-- "timestamp with time zone" (not Postgres's "timestamptz" shorthand) so
-- this also runs unmodified against H2 in the test suite.
alter table "user" add column "created_at" timestamp with time zone not null default now();
alter table "user" add column "updated_at" timestamp with time zone not null default now();

alter table "journeys" add column "created_at" timestamp with time zone not null default now();
alter table "journeys" add column "updated_at" timestamp with time zone not null default now();

alter table "booking" add column "created_at" timestamp with time zone not null default now();
alter table "booking" add column "updated_at" timestamp with time zone not null default now();
