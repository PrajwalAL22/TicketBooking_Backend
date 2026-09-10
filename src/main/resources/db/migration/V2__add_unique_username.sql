-- UserServiceImpl.register does a check-then-insert on username, which is
-- not race-safe on its own (two concurrent registrations with the same
-- username could both pass the check). This DB-level constraint is the
-- actual safety net; the application-level check just gives a friendlier
-- error message in the common case.
alter table "user" add constraint "uk_user_username" unique ("username");
