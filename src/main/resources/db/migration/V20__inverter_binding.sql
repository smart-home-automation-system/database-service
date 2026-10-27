-- The inverter binding (HAS-326, smart-home-sdk 1.15.0): the photovoltaic inverters of the house, the
-- kind INVERTER, read by energy-service through the dongle of a SOLAX, the Solarman logger of a SOFAR
-- or a cloud. These columns are the InverterBinding of the SDK model - a fourth binding next to the
-- three of V13, told by the column it cannot do without, inverter_serial_number.
--
-- Nullable on purpose: every existing row and every row the released version inserts during the
-- rollout (its entity does not name the columns) is of another kind and leaves them empty. That the
-- binding belongs to the kind INVERTER and to the vendor OTHER only, and which field is of which
-- brand, is DeviceRules', which says the same in words before a write reaches these checks.

ALTER TABLE devices
    ADD COLUMN inverter_brand                 VARCHAR(30),
    ADD COLUMN inverter_serial_number         VARCHAR(40),
    ADD COLUMN inverter_address               VARCHAR(15),
    ADD COLUMN inverter_registration_number   VARCHAR(20),
    ADD COLUMN inverter_logger_serial         BIGINT,
    ADD COLUMN inverter_battery_serial_number VARCHAR(40);

-- exactly one binding, now of four: a row the released version writes has one of the three and
-- passes this as it passed the check of V13
ALTER TABLE devices
    DROP CONSTRAINT devices_one_binding;

ALTER TABLE devices
    ADD CONSTRAINT devices_one_binding
        CHECK (num_nonnulls(shelly_address, eaton_gateway, bthome_address, inverter_serial_number) = 1);

-- the binding is whole or absent, and it is the binding of an inverter of no named vendor
ALTER TABLE devices
    ADD CONSTRAINT devices_inverter_binding_whole
        CHECK ((inverter_serial_number IS NULL) = (inverter_brand IS NULL)
            AND (inverter_serial_number IS NOT NULL OR num_nonnulls(
                inverter_address, inverter_registration_number, inverter_logger_serial,
                inverter_battery_serial_number) = 0));

ALTER TABLE devices
    ADD CONSTRAINT devices_inverter_of_inverters
        CHECK ((kind = 'INVERTER') = (inverter_serial_number IS NOT NULL));

ALTER TABLE devices
    ADD CONSTRAINT devices_inverter_vendor
        CHECK (inverter_serial_number IS NULL OR vendor = 'OTHER');

-- the registration number is the one of a SolaX dongle, the logger serial the one of the Solarman
-- logger of a SOFAR
ALTER TABLE devices
    ADD CONSTRAINT devices_inverter_brand_fields
        CHECK ((inverter_registration_number IS NULL OR inverter_brand = 'SOLAX')
            AND (inverter_logger_serial IS NULL OR inverter_brand = 'SOFAR'));

-- the bounds of the SDK model, for whatever gets past the API
ALTER TABLE devices
    ADD CONSTRAINT devices_inverter_format
        CHECK (inverter_serial_number ~ '^[A-Z0-9]{5,40}$'
            AND inverter_address ~ '^((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\.){3}(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])$'
            AND inverter_registration_number ~ '^[A-Z0-9]{5,20}$'
            AND inverter_logger_serial BETWEEN 1 AND 4294967295
            AND inverter_battery_serial_number ~ '^[A-Z0-9]{5,40}$');

-- an inverter is one device: its serial number is its identity
ALTER TABLE devices
    ADD CONSTRAINT devices_inverter_serial_number_uq UNIQUE (inverter_serial_number);
