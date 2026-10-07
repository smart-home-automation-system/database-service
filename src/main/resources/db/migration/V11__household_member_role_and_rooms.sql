-- What the web dashboard shows a member: their role and their rooms (HAS-192, smart-home-sdk 1.4.0).
--
-- role: the name of the MemberRole constant, as every enum in this database is stored (eaton_devices).
-- The default is for the rows that exist today - every member so far becomes a resident - and for
-- nothing else: Spring Data writes every column of an entity, so the service sets the role itself.
--
-- rooms: the names of the RoomName constants, in the order they were given - the order they are shown
-- in. An array column and not a table of its own: the rooms of a member are read and replaced only
-- as a whole, and the order comes for free. Which names exist is not repeated here - the SDK enum is
-- the list, and a CHECK would need a migration for every new room; for the same reason the elements
-- are TEXT, with no length a longer name could run into. That a room is listed once is
-- checked by the service, which can say so in its answer (a CHECK cannot look for duplicates without
-- a function); the schema only refuses a NULL among them.

ALTER TABLE household_members
    ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'RESIDENT';

ALTER TABLE household_members
    ADD CONSTRAINT household_members_role_known CHECK (role IN ('ADMIN', 'RESIDENT'));

ALTER TABLE household_members
    ADD COLUMN rooms TEXT[] NOT NULL DEFAULT '{}';

ALTER TABLE household_members
    ADD CONSTRAINT household_members_rooms_no_null CHECK (array_position(rooms, NULL) IS NULL);
