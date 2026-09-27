CREATE TABLE household_members
(
    id                      INT PRIMARY KEY NOT NULL UNIQUE GENERATED ALWAYS AS IDENTITY,
    created_at              TIMESTAMP       NOT NULL,
    updated_at              TIMESTAMP,
    name                    VARCHAR(50)     NOT NULL,
    phone                   VARCHAR(11)     NOT NULL,
    active                  BOOLEAN         NOT NULL DEFAULT TRUE
);

ALTER TABLE household_members
    ADD CONSTRAINT household_members_name_uq UNIQUE (name);

ALTER TABLE household_members
    ADD CONSTRAINT household_members_phone_uq UNIQUE (phone);

ALTER TABLE household_members
    ADD CONSTRAINT household_members_phone_format CHECK (phone ~ '^[0-9]{3}-[0-9]{3}-[0-9]{3}$');

CREATE TABLE member_devices
(
    id                      INT PRIMARY KEY NOT NULL UNIQUE GENERATED ALWAYS AS IDENTITY,
    created_at              TIMESTAMP       NOT NULL,
    updated_at              TIMESTAMP,
    member_id               INT             NOT NULL,
    name                    VARCHAR(50)     NOT NULL,
    mac                     VARCHAR(17)     NOT NULL
);

ALTER TABLE member_devices
    ADD CONSTRAINT member_devices_member_fk FOREIGN KEY (member_id)
        REFERENCES household_members (id) ON DELETE CASCADE;

ALTER TABLE member_devices
    ADD CONSTRAINT member_devices_mac_uq UNIQUE (mac);

ALTER TABLE member_devices
    ADD CONSTRAINT member_devices_mac_format CHECK (mac ~ '^([0-9a-f]{2}:){5}[0-9a-f]{2}$');

ALTER TABLE member_devices
    ADD CONSTRAINT member_devices_member_name_uq UNIQUE (member_id, name);
