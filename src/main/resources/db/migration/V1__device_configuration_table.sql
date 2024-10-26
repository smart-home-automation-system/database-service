CREATE TABLE device_configuration
(
    id                      INT PRIMARY KEY NOT NULL UNIQUE GENERATED ALWAYS AS IDENTITY,
    created_at              TIMESTAMP       NOT NULL,
    updated_at              TIMESTAMP,
    name                    VARCHAR(20)     NOT NULL,
    iot_type                VARCHAR(20)     NOT NULL,
    data_point              NUMERIC(2)
);