-- The registry of the devices of the house (HAS-222): what a device is, where it is and how it is
-- reached - the one table of devices from here on. A device is reached through exactly one of
-- three bindings - a Shelly by its address, an Eaton device by its data point on a gateway of the
-- AMX system, a Bluetooth sensor by its own address - kept as columns of the one row, with checks
-- that say which of them go together.
-- The enums are stored as the names of their constants (LIVING_ROOM, BLINDS), as everywhere here.
CREATE TABLE devices
(
    id                INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at        TIMESTAMP   NOT NULL,
    updated_at        TIMESTAMP,
    name              VARCHAR(50) NOT NULL,
    room              VARCHAR(30) NOT NULL,
    kind              VARCHAR(30) NOT NULL,
    vendor            VARCHAR(30) NOT NULL,
    enabled           BOOLEAN     NOT NULL,
    shelly_address    VARCHAR(15),
    shelly_generation INT,
    shelly_device_id  VARCHAR(50),
    shelly_channel    INT,
    eaton_gateway     VARCHAR(30),
    eaton_point       INT,
    bthome_address    VARCHAR(17),
    light_kind        VARCHAR(30),
    placement         VARCHAR(30),
    positioning       BOOLEAN
);

ALTER TABLE devices
    ADD CONSTRAINT devices_name_length CHECK (char_length(name) >= 3);

-- exactly one binding, told by the column each of them cannot do without
ALTER TABLE devices
    ADD CONSTRAINT devices_one_binding
        CHECK (num_nonnulls(shelly_address, eaton_gateway, bthome_address) = 1);

-- a binding is whole or absent
ALTER TABLE devices
    ADD CONSTRAINT devices_shelly_binding_whole
        CHECK ((shelly_address IS NULL) = (shelly_generation IS NULL)
            AND (shelly_address IS NOT NULL OR num_nonnulls(shelly_device_id, shelly_channel) = 0));

ALTER TABLE devices
    ADD CONSTRAINT devices_eaton_binding_whole
        CHECK ((eaton_gateway IS NULL) = (eaton_point IS NULL));

-- An Eaton device is reached through a gateway of the AMX system and nothing else is; and what
-- describes one kind only stays with that kind. The rest of "the binding fits the vendor and
-- the kind" is the service's to check when a device is stored.
ALTER TABLE devices
    ADD CONSTRAINT devices_eaton_vendor_binding
        CHECK ((vendor = 'EATON') = (eaton_gateway IS NOT NULL));

ALTER TABLE devices
    ADD CONSTRAINT devices_light_kind_of_a_light CHECK (light_kind IS NULL OR kind = 'LIGHT');

ALTER TABLE devices
    ADD CONSTRAINT devices_placement_of_blinds
        CHECK (num_nonnulls(placement, positioning) = 0 OR kind = 'BLINDS');

-- the bounds of the SDK models, for whatever gets past the API
ALTER TABLE devices
    ADD CONSTRAINT devices_shelly_address_format
        CHECK (shelly_address ~ '^((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\.){3}(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])$');

ALTER TABLE devices
    ADD CONSTRAINT devices_shelly_generation_range CHECK (shelly_generation BETWEEN 1 AND 4);

ALTER TABLE devices
    ADD CONSTRAINT devices_shelly_device_id_format CHECK (shelly_device_id ~ '^[A-Za-z0-9_-]{3,50}$');

ALTER TABLE devices
    ADD CONSTRAINT devices_shelly_channel_range CHECK (shelly_channel BETWEEN 0 AND 3);

ALTER TABLE devices
    ADD CONSTRAINT devices_eaton_point_range CHECK (eaton_point BETWEEN 1 AND 99);

ALTER TABLE devices
    ADD CONSTRAINT devices_bthome_address_format CHECK (bthome_address ~ '^([0-9a-f]{2}:){5}[0-9a-f]{2}$');

-- One device per channel of a Shelly, per data point of a gateway, per Bluetooth sensor. A Shelly
-- without a channel counts as one, too: a plain UNIQUE would let it in any number of times,
-- because two NULLs are never equal - hence the index on an expression.
CREATE UNIQUE INDEX devices_shelly_address_channel_uq
    ON devices (shelly_address, COALESCE(shelly_channel, -1))
    WHERE shelly_address IS NOT NULL;

-- The same for the id a Shelly reports over MQTT: the services find the device of an event by it,
-- and a device registered a second time under a new address would answer one event twice.
CREATE UNIQUE INDEX devices_shelly_device_id_channel_uq
    ON devices (shelly_device_id, COALESCE(shelly_channel, -1))
    WHERE shelly_device_id IS NOT NULL;

ALTER TABLE devices
    ADD CONSTRAINT devices_eaton_gateway_point_uq UNIQUE (eaton_gateway, eaton_point);

-- A Bluetooth sensor is one device, whichever gateway hears it: it is not bound to a gateway, and
-- a second gateway in range is a second way to the same sensor.
ALTER TABLE devices
    ADD CONSTRAINT devices_bthome_address_uq UNIQUE (bthome_address);

-- The Eaton configuration moves in: what eaton_devices held - a data point of a gateway, its room
-- and what it is - is a device of the registry with an Eaton binding. A configuration had no name,
-- so each gets one from its room, its kind and its point ("Living room temperature sensor 45").
-- A dimmer was a type of its own there and is a light that dims here. The type OTHER has no kind:
-- such a row makes kind NULL and stops the migration, on purpose - it has to be decided by hand.
-- eaton_devices itself stays, unread from this version on: the version before it still reads the
-- table while the two run side by side in a rollout. The next migration drops it.
INSERT INTO devices (created_at, updated_at, name, room, kind, vendor, enabled, eaton_gateway, eaton_point, light_kind)
SELECT configuration.created_at,
       configuration.updated_at,
       upper(left(configuration.room, 1)) || lower(replace(substr(configuration.room, 2), '_', ' '))
           || ' ' || lower(replace(configuration.kind, '_', ' ')) || ' ' || configuration.point,
       configuration.room,
       configuration.kind,
       'EATON',
       TRUE,
       configuration.gateway,
       configuration.point,
       configuration.light_kind
FROM (SELECT created_at,
             updated_at,
             room,
             gateway,
             point,
             CASE type
                 WHEN 'TEMPERATURE_SENSOR' THEN 'TEMPERATURE_SENSOR'
                 WHEN 'BLINDS' THEN 'BLINDS'
                 WHEN 'LIGHT' THEN 'LIGHT'
                 WHEN 'DIMMER' THEN 'LIGHT'
                 END AS kind,
             CASE type
                 WHEN 'LIGHT' THEN 'RELAY'
                 WHEN 'DIMMER' THEN 'DIMMER'
                 END AS light_kind
      FROM eaton_devices
      ORDER BY id) AS configuration;
