-- Baseline schema, matching what Hibernate's ddl-auto=update had already
-- created (exported directly from the JPA mappings via
-- jakarta.persistence.schema-generation, then hand-tidied: named
-- constraints instead of Hibernate's auto-generated hash names, since
-- ddl-auto=validate only checks tables/columns, not constraint names).
--
-- On a fresh database (CI, Testcontainers, a new dev machine) Flyway runs
-- this for real. Against the existing local dev DB, it's applied via
-- baselining instead (see spring.flyway.baseline-on-migrate), since that
-- schema already matches this exactly.

create sequence "user_seq" start with 1 increment by 50;
create sequence "journeys_seq" start with 1 increment by 50;
create sequence "booking_seq" start with 1 increment by 50;

create table "user" (
    "user_id" bigint not null,
    "password" varchar(255),
    "username" varchar(255),
    primary key ("user_id")
);

create table "journeys" (
    "journey_id" bigint not null,
    "city_name" varchar(255),
    "tickets_remaining" integer not null,
    "version" integer,
    primary key ("journey_id")
);

create table "booking" (
    "booking_id" bigint not null,
    "idempotency_key" varchar(100),
    "is_cancelled" boolean not null,
    "no_of_tickets_booked" integer not null,
    "journey_journey_id" bigint,
    "user_user_id" bigint,
    primary key ("booking_id"),
    constraint "uk_booking_idempotency_key" unique ("idempotency_key")
);

alter table "booking" add constraint "fk_booking_journey" foreign key ("journey_journey_id") references "journeys";
alter table "booking" add constraint "fk_booking_user" foreign key ("user_user_id") references "user";
