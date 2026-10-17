-- The window sensors of blinds (HAS-290, smart-home-sdk 1.11.0): the ids of the devices of the kind
-- opening sensor on the window one blinds cover - two for a window with two sashes, none for a
-- window without a sensor. The link belongs to the blinds, not to the room.
--
-- An array column like the rooms and the permissions of a member (V11, V12): the sensors of blinds
-- are read and replaced only with the whole device, and the order is the client's. That each id is
-- a registered opening sensor, listed once, and that a sensor in use is not removed is checked by
-- the service, which can say so in words; the schema only keeps the list to blinds and refuses a
-- NULL among the ids. The default gives every existing device - and every row an older release
-- inserts during the rollout, which does not name the column - an empty list.

ALTER TABLE devices
    ADD COLUMN opening_sensors BIGINT[] NOT NULL DEFAULT '{}';

ALTER TABLE devices
    ADD CONSTRAINT devices_opening_sensors_of_blinds
        CHECK (cardinality(opening_sensors) = 0 OR kind = 'BLINDS');

ALTER TABLE devices
    ADD CONSTRAINT devices_opening_sensors_no_null CHECK (array_position(opening_sensors, NULL) IS NULL);
