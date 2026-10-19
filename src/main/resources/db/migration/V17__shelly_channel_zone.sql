-- The zone of a presence sensor as its channel (HAS-260, smart-home-sdk 1.12.0). A Shelly Presence
-- Gen4 reports whether somebody is there per zone, each a dynamic component of an id from 200 on
-- (presencezone:200 is the main zone of every Presence of the house); the registry stores the zone
-- a presence sensor stands for as its channel. 0 to 3 stays the output of a relay, a dimmer or a
-- cover, of any kind; 200 to 299 is a zone and belongs to a presence sensor alone. DeviceRules says
-- the same in words before a write reaches this check.
--
-- The name stays, the bound widens: a release that does not know the zones never writes one, and
-- everything it may write is still taken.

ALTER TABLE devices
    DROP CONSTRAINT devices_shelly_channel_range;

ALTER TABLE devices
    ADD CONSTRAINT devices_shelly_channel_range
        CHECK (shelly_channel BETWEEN 0 AND 3
            OR (shelly_channel BETWEEN 200 AND 299 AND kind = 'PRESENCE_SENSOR'));
