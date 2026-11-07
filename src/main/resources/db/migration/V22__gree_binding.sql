-- The Gree binding (HAS-377 / HAS-378, smart-home-sdk 1.18.0): the air conditioners of the house, the
-- kind AIR_CONDITIONER of the vendor GREE, driven by aircon-service over the protocol of Gree - by unicast
-- UDP to the address of the unit, which names itself by its MAC in every answer. These columns are the
-- GreeBinding of the SDK model - a fifth binding next to those of V13 and V20, told by the column it
-- cannot do without, gree_mac. The key of a unit is never stored: aircon-service binds it at its start.
--
-- Nullable on purpose: every existing row and every row the released version inserts during the rollout
-- (its entity does not name the columns) is of another kind and leaves them empty. That the binding
-- belongs to the kind AIR_CONDITIONER and to the vendor GREE only is DeviceRules', which says the same in
-- words before a write reaches these checks.

ALTER TABLE devices
    ADD COLUMN gree_address VARCHAR(15),
    ADD COLUMN gree_mac     VARCHAR(12);

-- exactly one binding, now of five: a row the released version writes has one of the four and passes
-- this as it passed the check of V20
ALTER TABLE devices
    DROP CONSTRAINT devices_one_binding;

ALTER TABLE devices
    ADD CONSTRAINT devices_one_binding
        CHECK (num_nonnulls(shelly_address, eaton_gateway, bthome_address, inverter_serial_number, gree_mac) = 1);

-- the binding is whole or absent
ALTER TABLE devices
    ADD CONSTRAINT devices_gree_binding_whole
        CHECK ((gree_mac IS NULL) = (gree_address IS NULL));

-- the binding of the air conditioners, and of the vendor Gree: one goes with the other both ways
ALTER TABLE devices
    ADD CONSTRAINT devices_gree_of_air_conditioners
        CHECK ((kind = 'AIR_CONDITIONER') = (gree_mac IS NOT NULL));

ALTER TABLE devices
    ADD CONSTRAINT devices_gree_vendor
        CHECK ((vendor = 'GREE') = (gree_mac IS NOT NULL));

-- the bounds of the SDK model, for whatever gets past the API: the MAC as the unit gives it
ALTER TABLE devices
    ADD CONSTRAINT devices_gree_format
        CHECK (gree_address ~ '^((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\.){3}(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])$'
            AND gree_mac ~ '^[0-9a-f]{12}$');

-- a unit is one device: its MAC is its identity, and its address - reserved for it in DHCP - is its own
-- (two rows on one address would send the commands of one unit to the other)
ALTER TABLE devices
    ADD CONSTRAINT devices_gree_mac_uq UNIQUE (gree_mac);

ALTER TABLE devices
    ADD CONSTRAINT devices_gree_address_uq UNIQUE (gree_address);
