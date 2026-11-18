-- The opening sensors of a gate (HAS-471, smart-home-sdk 1.23.0): a gate with the BLU Door/Window sensor on it is
-- a garage gate whose state gate-service knows; a gate without one is the driveway gate. The same column as the
-- sensors on the window of blinds (V16) - only the check of the kinds that may carry the list changes.
--
-- The released version neither writes a list on a gate nor reads one: its rules keep the list to blinds, and
-- every row it wrote passes the new check as it passed the old one, so the two run side by side in the rollout.
-- That each id is a registered opening sensor and that a sensor in use is not removed stays the service's.

ALTER TABLE devices
    DROP CONSTRAINT devices_opening_sensors_of_blinds;

ALTER TABLE devices
    ADD CONSTRAINT devices_opening_sensors_of_blinds_and_gates
        CHECK (cardinality(opening_sensors) = 0 OR kind IN ('BLINDS', 'GATE'));
