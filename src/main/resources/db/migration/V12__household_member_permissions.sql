-- What a member may do in the web dashboard beyond their role (HAS-202, smart-home-sdk 1.6.0).
--
-- permissions: the names of the MemberPermission constants, as every enum in this database is
-- stored. An array column like the rooms (V11), for the same reasons: the permissions of a member
-- are read and replaced only as a whole, which names exist is the SDK enum's to say - a CHECK would
-- need a migration for every new permission - and the elements are TEXT. That a permission is
-- listed once is checked by the service, which can say so in its answer; the schema only refuses a
-- NULL among them. Nobody has a permission until one is granted: every member so far gets none.

ALTER TABLE household_members
    ADD COLUMN permissions TEXT[] NOT NULL DEFAULT '{}';

ALTER TABLE household_members
    ADD CONSTRAINT household_members_permissions_no_null CHECK (array_position(permissions, NULL) IS NULL);
