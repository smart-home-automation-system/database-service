-- The registry of the devices of the house (HAS-222): what a device is, where it is and how it is
-- reached. A device is reached through exactly one of three bindings - a Shelly by its address, an
-- Eaton device by its data point on a gateway, a Bluetooth sensor through the gateway that hears
-- it - kept as columns of the one table, with checks that say which of them go together.
-- The enums are stored as the names of their constants (LIVING_ROOM, BLINDS), as everywhere here.
CREATE TABLE devices
(
    id                       INT PRIMARY KEY NOT NULL UNIQUE GENERATED ALWAYS AS IDENTITY,
    created_at               TIMESTAMP       NOT NULL,
    updated_at               TIMESTAMP,
    name                     VARCHAR(50)     NOT NULL,
    room                     VARCHAR(30)     NOT NULL,
    kind                     VARCHAR(30)     NOT NULL,
    vendor                   VARCHAR(30)     NOT NULL,
    enabled                  BOOLEAN         NOT NULL,
    shelly_address           VARCHAR(15),
    shelly_generation        INT,
    shelly_device_id         VARCHAR(50),
    shelly_channel           INT,
    eaton_gateway            VARCHAR(30),
    eaton_point              INT,
    bthome_gateway_device_id VARCHAR(50),
    bthome_address           VARCHAR(17),
    light_kind               VARCHAR(30),
    placement                VARCHAR(30),
    positioning              BOOLEAN
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

ALTER TABLE devices
    ADD CONSTRAINT devices_bthome_binding_whole
        CHECK ((bthome_address IS NULL) = (bthome_gateway_device_id IS NULL));

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
    ADD CONSTRAINT devices_bthome_gateway_device_id_format
        CHECK (bthome_gateway_device_id ~ '^[A-Za-z0-9_-]{3,50}$');

ALTER TABLE devices
    ADD CONSTRAINT devices_bthome_address_format CHECK (bthome_address ~ '^([0-9a-f]{2}:){5}[0-9a-f]{2}$');

-- One device per channel of a Shelly, per data point of a gateway, per sensor of a gateway. A
-- Shelly without a channel counts as one, too: a plain UNIQUE would let it in any number of
-- times, because two NULLs are never equal - hence the index on an expression.
CREATE UNIQUE INDEX devices_shelly_address_channel_uq
    ON devices (shelly_address, COALESCE(shelly_channel, -1))
    WHERE shelly_address IS NOT NULL;

ALTER TABLE devices
    ADD CONSTRAINT devices_eaton_gateway_point_uq UNIQUE (eaton_gateway, eaton_point);

ALTER TABLE devices
    ADD CONSTRAINT devices_bthome_gateway_address_uq UNIQUE (bthome_gateway_device_id, bthome_address);
