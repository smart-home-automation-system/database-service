-- The Hikvision binding (HAS-445, smart-home-sdk 1.22.0): the door station at the gate, the kind INTERCOM of
-- the vendor HIKVISION, read by gate-service through the SDK bridge of the cluster and over ISAPI at the
-- address of the station, which names itself by the serial number of its System/deviceInfo. These columns
-- are the HikvisionBinding of the SDK model - a seventh binding next to those of V13, V20, V22 and V24, told
-- by the column it cannot do without, hikvision_serial_number. The kind GATE (the Shelly that pulses the
-- drive of the gate) is a Shelly device and needs no column.
--
-- Nullable on purpose: every existing row and every row the released version inserts during the rollout
-- (its entity does not name the columns) is of another kind and leaves them empty. That the binding
-- belongs to the kind INTERCOM and to the vendor HIKVISION only is DeviceRules', which says the same in
-- words before a write reaches these checks.

ALTER TABLE devices
    ADD COLUMN hikvision_address       VARCHAR(15),
    ADD COLUMN hikvision_serial_number VARCHAR(64);

-- exactly one binding, now of seven: a row the released version writes has one of the six and passes
-- this as it passed the check of V24
ALTER TABLE devices
    DROP CONSTRAINT devices_one_binding;

ALTER TABLE devices
    ADD CONSTRAINT devices_one_binding
        CHECK (num_nonnulls(shelly_address, eaton_gateway, bthome_address, inverter_serial_number, gree_mac,
                            blebox_device_id, hikvision_serial_number) = 1);

-- the binding is whole or absent
ALTER TABLE devices
    ADD CONSTRAINT devices_hikvision_binding_whole
        CHECK ((hikvision_serial_number IS NULL) = (hikvision_address IS NULL));

-- the binding of the intercoms, and of the vendor Hikvision: one goes with the other both ways
ALTER TABLE devices
    ADD CONSTRAINT devices_hikvision_of_intercoms
        CHECK ((kind = 'INTERCOM') = (hikvision_serial_number IS NOT NULL));

ALTER TABLE devices
    ADD CONSTRAINT devices_hikvision_vendor
        CHECK ((vendor = 'HIKVISION') = (hikvision_serial_number IS NOT NULL));

-- the bounds of the SDK model, for whatever gets past the API: the serial number as System/deviceInfo of the
-- station gives it
ALTER TABLE devices
    ADD CONSTRAINT devices_hikvision_format
        CHECK (hikvision_address ~ '^((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\.){3}(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])$'
            AND hikvision_serial_number ~ '^[A-Za-z0-9][A-Za-z0-9()-]*$'
            AND char_length(hikvision_serial_number) >= 9);

-- a station is one device: its serial number is its identity, and its address - reserved for it in DHCP -
-- is its own (two rows on one address would read one station as two)
ALTER TABLE devices
    ADD CONSTRAINT devices_hikvision_serial_number_uq UNIQUE (hikvision_serial_number);

ALTER TABLE devices
    ADD CONSTRAINT devices_hikvision_address_uq UNIQUE (hikvision_address);
