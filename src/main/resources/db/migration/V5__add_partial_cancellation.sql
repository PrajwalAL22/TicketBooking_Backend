-- Supports cancelling only some of a booking's tickets. noOfTicketsBooked stays
-- the original booked count (history preserved); active tickets are
-- booked - cancelled, and is_cancelled means "fully cancelled" (cancelled = booked).
alter table "booking" add column "no_of_tickets_cancelled" integer not null default 0;

-- Bookings cancelled before this migration were always cancelled in full.
update "booking" set "no_of_tickets_cancelled" = "no_of_tickets_booked" where "is_cancelled" = true;
