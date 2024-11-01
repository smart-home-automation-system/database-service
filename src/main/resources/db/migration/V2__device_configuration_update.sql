ALTER TABLE device_configuration RENAME COLUMN iot_type TO iot_vendor;
ALTER TABLE device_configuration RENAME COLUMN name TO room_name;
ALTER TABLE device_configuration ADD COLUMN eaton_gateway VARCHAR(10);