CREATE TABLE eaton_devices
(
    id                      INT PRIMARY KEY NOT NULL UNIQUE GENERATED ALWAYS AS IDENTITY,
    created_at              TIMESTAMP       NOT NULL,
    updated_at              TIMESTAMP,
    point                   NUMERIC(2)      NOT NULL,
    room                    VARCHAR(20)     NOT NULL,
    type                    VARCHAR(50)     NOT NULL,
    gateway                 VARCHAR(10)     NOT NULL
);
