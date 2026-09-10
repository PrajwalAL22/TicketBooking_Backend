create sequence "outbox_event_seq" start with 1 increment by 50;

create table "outbox_event" (
    "id" bigint not null,
    "aggregate_type" varchar(255) not null,
    "aggregate_id" bigint not null,
    "event_type" varchar(255) not null,
    "payload" text not null,
    "created_at" timestamp with time zone not null,
    "published_at" timestamp with time zone,
    primary key ("id")
);

-- Speeds up the relay's "find unpublished rows" poll as the table grows.
-- A partial index (WHERE published_at IS NULL) would be tighter on real
-- Postgres, but H2 (used in tests) doesn't support partial indexes, and
-- keeping one migration file portable across both beats a Postgres-only
-- optimization here.
create index "idx_outbox_event_published_at" on "outbox_event" ("published_at");
