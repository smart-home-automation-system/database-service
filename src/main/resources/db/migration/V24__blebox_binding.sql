-- The BleBox binding (HAS-424 / HAS-425, smart-home-sdk 1.20.0): the air quality sensor outside the house,
-- the kind AIR_QUALITY_SENSOR of the vendor BLEBOX, polled by weather-service over the HTTP API of BleBox at
-- the address of the sensor, which names itself by the id of its GET /info. These columns are the
-- BleboxBinding of the SDK model - a sixth binding next to those of V13, V20 and V22, told by the column it
-- cannot do without, blebox_device_id.
--
-- Nullable on purpose: every existing row and every row the released version inserts during the rollout
-- (its entity does not name the columns) is of another kind and leaves them empty. That the binding
-- belongs to the kind AIR_QUALITY_SENSOR and to the vendor BLEBOX only is DeviceRules', which says the same
-- in words before a write reaches these checks.

ALTER TABLE devices
    ADD COLUMN blebox_address   VARCHAR(15),
    ADD COLUMN blebox_device_id VARCHAR(12);

-- exactly one binding, now of six: a row the released version writes has one of the five and passes
-- this as it passed the check of V22
ALTER TABLE devices
    DROP CONSTRAINT devices_one_binding;

ALTER TABLE devices
    ADD CONSTRAINT devices_one_binding
        CHECK (num_nonnulls(shelly_address, eaton_gateway, bthome_address, inverter_serial_number, gree_mac,
                            blebox_device_id) = 1);

-- the binding is whole or absent
ALTER TABLE devices
    ADD CONSTRAINT devices_blebox_binding_whole
        CHECK ((blebox_device_id IS NULL) = (blebox_address IS NULL));

-- the binding of the air quality sensors, and of the vendor BleBox: one goes with the other both ways
ALTER TABLE devices
    ADD CONSTRAINT devices_blebox_of_air_quality_sensors
        CHECK ((kind = 'AIR_QUALITY_SENSOR') = (blebox_device_id IS NOT NULL));

ALTER TABLE devices
    ADD CONSTRAINT devices_blebox_vendor
        CHECK ((vendor = 'BLEBOX') = (blebox_device_id IS NOT NULL));

-- the bounds of the SDK model, for whatever gets past the API: the id as GET /info of the device gives it
ALTER TABLE devices
    ADD CONSTRAINT devices_blebox_format
        CHECK (blebox_address ~ '^((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\.){3}(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])$'
            AND blebox_device_id ~ '^[0-9a-f]{12}$');

-- a sensor is one device: its id is its identity, and its address - reserved for it in DHCP - is its own
-- (two rows on one address would read one sensor as two)
ALTER TABLE devices
    ADD CONSTRAINT devices_blebox_device_id_uq UNIQUE (blebox_device_id);

ALTER TABLE devices
    ADD CONSTRAINT devices_blebox_address_uq UNIQUE (blebox_address);
