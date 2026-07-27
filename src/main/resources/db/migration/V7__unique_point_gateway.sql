DELETE FROM eaton_devices a
    USING eaton_devices b
WHERE a.point = b.point
  AND a.gateway = b.gateway
  AND a.id > b.id;

ALTER TABLE eaton_devices
    ADD CONSTRAINT eaton_devices_point_gateway_uq UNIQUE (point, gateway);

ALTER TABLE eaton_devices
    ADD CONSTRAINT eaton_devices_point_range CHECK (point BETWEEN 1 AND 99);
