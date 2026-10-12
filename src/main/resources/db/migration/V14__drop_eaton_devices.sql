-- eaton_devices goes (HAS-223). Since V13 the Eaton configuration lives in devices and nothing
-- reads the old table; it was left in place only for the version before V13, which read it while
-- the two ran side by side in a rollout.
-- That version could still write to it, though: a configuration it stored after V13 had copied
-- the table - during that rollout, or after a rollback to it - is in eaton_devices alone. So what
-- devices lacks is copied first, told by the gateway and the point, with the mapping and the
-- wording of V13: a dimmer is a light that dims, the name is the room, the kind and the point.
-- A row of the type OTHER has no kind and stops the migration here as it would have there.
INSERT INTO devices (created_at, updated_at, name, room, kind, vendor, enabled, eaton_gateway, eaton_point, light_kind)
SELECT configuration.created_at,
       configuration.updated_at,
       upper(left(configuration.room, 1)) || lower(replace(substr(configuration.room, 2), '_', ' '))
           || ' ' || lower(replace(configuration.kind, '_', ' ')) || ' ' || configuration.point,
       configuration.room,
       configuration.kind,
       'EATON',
       TRUE,
       configuration.gateway,
       configuration.point,
       configuration.light_kind
FROM (SELECT created_at,
             updated_at,
             room,
             gateway,
             point,
             CASE type
                 WHEN 'TEMPERATURE_SENSOR' THEN 'TEMPERATURE_SENSOR'
                 WHEN 'BLINDS' THEN 'BLINDS'
                 WHEN 'LIGHT' THEN 'LIGHT'
                 WHEN 'DIMMER' THEN 'LIGHT'
                 END AS kind,
             CASE type
                 WHEN 'LIGHT' THEN 'RELAY'
                 WHEN 'DIMMER' THEN 'DIMMER'
                 END AS light_kind
      FROM eaton_devices
      WHERE NOT EXISTS (SELECT 1
                        FROM devices
                        WHERE devices.eaton_gateway = eaton_devices.gateway
                          AND devices.eaton_point = eaton_devices.point)
      ORDER BY id) AS configuration;

DROP TABLE eaton_devices;
