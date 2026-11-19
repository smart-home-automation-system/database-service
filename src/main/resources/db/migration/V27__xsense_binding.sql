-- The X-Sense binding (HAS-457, smart-home-sdk 1.24.0): the smoke, CO and heat detectors of the house, the kinds
-- SMOKE_DETECTOR, CO_DETECTOR and HEAT_DETECTOR of the vendor XSENSE, read by safety-service from what their base
-- station publishes on MQTT - a detector under the serial of its base and the id the base gives it. These columns
-- are the XsenseBinding of the SDK model - an eighth binding next to those of V13, V20, V22, V24 and V25, told by
-- the column it cannot do without, xsense_station_serial. The base itself is not a registry device.
--
-- Nullable on purpose: every existing row and every row the released version inserts during the rollout (its
-- entity does not name the columns) is of another kind and leaves them empty. That the binding belongs to the
-- three detector kinds and to the vendor XSENSE only is DeviceRules', which says the same in words before a
-- write reaches these checks.

ALTER TABLE devices
    ADD COLUMN xsense_station_serial VARCHAR(13),
    ADD COLUMN xsense_device_id      VARCHAR(8);

-- exactly one binding, now of eight: a row the released version writes has one of the seven and passes this as
-- it passed the check of V25
ALTER TABLE devices
    DROP CONSTRAINT devices_one_binding;

ALTER TABLE devices
    ADD CONSTRAINT devices_one_binding
        CHECK (num_nonnulls(shelly_address, eaton_gateway, bthome_address, inverter_serial_number, gree_mac,
                            blebox_device_id, hikvision_serial_number, xsense_station_serial) = 1);

-- the binding is whole or absent
ALTER TABLE devices
    ADD CONSTRAINT devices_xsense_binding_whole
        CHECK ((xsense_station_serial IS NULL) = (xsense_device_id IS NULL));

-- the binding of the detectors, and of the vendor X-Sense: one goes with the other both ways
ALTER TABLE devices
    ADD CONSTRAINT devices_xsense_of_detectors
        CHECK ((kind IN ('SMOKE_DETECTOR', 'CO_DETECTOR', 'HEAT_DETECTOR')) = (xsense_station_serial IS NOT NULL));

ALTER TABLE devices
    ADD CONSTRAINT devices_xsense_vendor
        CHECK ((vendor = 'XSENSE') = (xsense_station_serial IS NOT NULL));

-- the bounds of the SDK model, for whatever gets past the API: the client id of the base on the broker, the id the
-- base gives a detector
ALTER TABLE devices
    ADD CONSTRAINT devices_xsense_format
        CHECK (xsense_station_serial ~ '^SBS50[0-9A-F]{8}$'
            AND xsense_device_id ~ '^[0-9A-F]{8}$');

-- a detector is one device: the base publishes it under the pair, so two rows of one pair would read one detector
-- as two. Two bases may give the same id, so the id alone is no identity
ALTER TABLE devices
    ADD CONSTRAINT devices_xsense_detector_uq UNIQUE (xsense_station_serial, xsense_device_id);
