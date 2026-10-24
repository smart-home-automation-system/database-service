package cloud.cholewa.data.postgres;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//The migrations of the service on a real PostgreSQL. The schema every class here runs on was built
//by them, by the Flyway of the application, when the context started; the two rehearsals below
//run them once more in a schema of their own, stopping on the way to write the rows an earlier
//release would have left - a migration that only works on an empty table fails here, not on the
//database of the house.
class SchemaMigrationOnPostgresTest extends OnPostgres {

    @Autowired
    private Flyway flyway;

    @Test
    void should_have_applied_every_migration_of_the_service_to_an_empty_database() throws IOException {
        final int migrations = new PathMatchingResourcePatternResolver()
            .getResources("classpath:db/migration/V*.sql").length;

        final List<Map<String, Object>> applied = rows(
            "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL");

        assertThat(migrations).isGreaterThanOrEqualTo(15);
        assertThat(applied)
            .hasSize(migrations)
            .allSatisfy(migration -> assertThat(migration).containsEntry("success", true));
    }

    //the rehearsals drop schemas, and they take their connection from the Flyway of the
    //application: it has to be the one to the container
    @Test
    void should_run_the_flyway_of_the_application_on_the_container_and_nowhere_else() throws SQLException {
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(postgres().getJdbcUrl());
        }
    }

    //the services tell one duplicate from another by these names; renamed in a migration, a
    //duplicate would be answered as the bare "Duplicate Key" of the library
    @Test
    void should_have_the_unique_constraints_under_the_names_the_services_look_for() {
        final List<Object> indexes = rows("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'").stream()
            .map(index -> index.get("indexname"))
            .toList();

        assertThat(indexes).contains(
            "household_members_name_upper_uq",
            "household_members_phone_uq",
            "member_devices_mac_uq",
            "member_devices_member_name_uq",
            "devices_shelly_address_channel_uq",
            "devices_shelly_device_id_channel_uq",
            "devices_eaton_gateway_point_uq",
            "devices_bthome_address_uq",
            "push_subscriptions_endpoint_uq"
        );
    }

    //V9 to V12 over a registry as V8 left it: the phone moves to E.164, and the member becomes a
    //resident without rooms and without permissions, devices untouched
    @Test
    void should_carry_a_member_registered_before_the_phone_format_the_roles_and_the_rooms_through_the_migrations() {
        final String schema = "rehearsal_household";
        startOver(schema);
        migrate(schema, "8");
        execute("""
            INSERT INTO %1$s.household_members (created_at, name, phone) VALUES (now(), 'Anna', '505-602-702');
            INSERT INTO %1$s.member_devices (created_at, member_id, name, mac)
            SELECT now(), id, 'iPhone', 'aa:bb:cc:dd:ee:01' FROM %1$s.household_members""".formatted(schema));

        migrate(schema, "latest");

        assertThat(row("""
            SELECT m.phone, m.active, m.role, m.rooms = '{}' AS no_rooms, m.permissions = '{}' AS no_permissions,
                   d.mac
            FROM %1$s.household_members m JOIN %1$s.member_devices d ON d.member_id = m.id""".formatted(schema)))
            .containsEntry("phone", "+48505602702")
            .containsEntry("active", true)
            .containsEntry("role", "RESIDENT")
            .containsEntry("no_rooms", true)
            .containsEntry("no_permissions", true)
            .containsEntry("mac", "aa:bb:cc:dd:ee:01");
    }

    //V7 over a table that holds the same point of a gateway twice: the older row stays. Up to V7
    //and no further: the table is gone since V14
    @Test
    void should_keep_the_older_of_two_configurations_of_one_point_when_they_become_unique() {
        final String schema = "rehearsal_eaton";
        startOver(schema);
        migrate(schema, "6");
        execute("""
            INSERT INTO %1$s.eaton_devices (created_at, point, room, type, gateway) VALUES
                (now(), 7, 'OFFICE', 'LIGHT', 'LIGHTS'),
                (now(), 7, 'KITCHEN', 'DIMMER', 'LIGHTS'),
                (now(), 7, 'LOFT', 'BLINDS', 'BLINDS')""".formatted(schema));

        migrate(schema, "7");

        assertThat(rows("SELECT room FROM %s.eaton_devices ORDER BY id".formatted(schema)))
            .extracting(configuration -> configuration.get("room"))
            .containsExactly("OFFICE", "LOFT");
    }

    //V13 over the Eaton configuration as V12 left it: every configuration becomes a device of the
    //registry - bound to its point, named after its room, its kind and its point, a dimmer as a
    //light that dims - and the old table is left as it was for the version that still reads it
    //(V14 drops it, so this one stops at V13)
    @Test
    void should_move_the_eaton_configuration_into_the_registry_of_devices() {
        final String schema = "rehearsal_devices";
        startOver(schema);
        migrate(schema, "12");

        execute("""
            INSERT INTO %1$s.eaton_devices (created_at, updated_at, point, room, type, gateway) VALUES
                ('2024-01-05 10:00:00', NULL, 45, 'LIVING_ROOM', 'TEMPERATURE_SENSOR', 'BLINDS'),
                ('2024-01-06 10:00:00', '2024-02-01 08:00:00', 46, 'BATHROOM_DOWN', 'TEMPERATURE_SENSOR', 'BLINDS'),
                ('2024-01-07 10:00:00', NULL, 7, 'HALL_UP', 'DIMMER', 'LIGHTS'),
                ('2024-01-08 10:00:00', NULL, 8, 'KITCHEN', 'LIGHT', 'LIGHTS'),
                ('2024-01-09 10:00:00', NULL, 7, 'OFFICE', 'BLINDS', 'BLINDS')""".formatted(schema));

        migrate(schema, "13");

        assertThat(rows("""
            SELECT name || ' | ' || room || ' | ' || kind || ' | ' || COALESCE(light_kind, '-') || ' | '
                   || eaton_gateway || ' ' || eaton_point || ' | ' || vendor || ' ' || enabled AS device
            FROM %s.devices ORDER BY id""".formatted(schema)))
            .extracting(device -> device.get("device"))
            .containsExactly(
                "Living room temperature sensor 45 | LIVING_ROOM | TEMPERATURE_SENSOR | - | BLINDS 45 | EATON true",
                "Bathroom down temperature sensor 46 | BATHROOM_DOWN | TEMPERATURE_SENSOR | - | BLINDS 46 | EATON true",
                "Hall up light 7 | HALL_UP | LIGHT | DIMMER | LIGHTS 7 | EATON true",
                "Kitchen light 8 | KITCHEN | LIGHT | RELAY | LIGHTS 8 | EATON true",
                "Office blinds 7 | OFFICE | BLINDS | - | BLINDS 7 | EATON true"
            );

        //the times of the configuration travel with it
        assertThat(row("""
            SELECT to_char(created_at, 'YYYY-MM-DD HH24:MI') AS created, to_char(updated_at, 'YYYY-MM-DD HH24:MI') AS updated
            FROM %s.devices WHERE eaton_point = 46""".formatted(schema)))
            .containsEntry("created", "2024-01-06 10:00")
            .containsEntry("updated", "2024-02-01 08:00");

        //the version before this one reads eaton_devices while the two run side by side
        assertThat(row("SELECT count(*) AS configurations FROM %s.eaton_devices".formatted(schema)))
            .containsEntry("configurations", 5L);
    }

    //A configuration of the type OTHER has no kind in the registry. The migration stops on it
    //rather than guess - the house has none, and one that appears has to be decided by hand
    @Test
    void should_stop_on_a_configuration_the_registry_has_no_kind_for() {
        final String schema = "rehearsal_devices_other";
        startOver(schema);
        migrate(schema, "12");

        execute("""
            INSERT INTO %1$s.eaton_devices (created_at, point, room, type, gateway)
            VALUES (now(), 9, 'GARAGE', 'OTHER', 'LIGHTS')""".formatted(schema));

        assertThatThrownBy(() -> migrate(schema, "latest"))
            .isInstanceOf(FlywayException.class)
            .hasMessageContaining("V13__devices.sql")
            .hasMessageContaining("not-null constraint");
    }

    //V14 over the two tables as a rollout of V13 could leave them: the configurations V13 copied,
    //one of them corrected in the registry since, a device the registry got on its own - and two
    //configurations the version before V13 stored after the copy, which are in eaton_devices
    //alone. Those two move in, nothing that is there is touched or doubled, and the table goes
    @Test
    void should_copy_what_the_registry_lacks_before_the_table_of_the_eaton_configuration_is_dropped() {
        final String schema = "rehearsal_drop_eaton";
        startOver(schema);
        migrate(schema, "12");
        execute("""
            INSERT INTO %1$s.eaton_devices (created_at, point, room, type, gateway) VALUES
                ('2024-01-05 10:00:00', 45, 'LIVING_ROOM', 'TEMPERATURE_SENSOR', 'BLINDS'),
                ('2024-01-06 10:00:00', 46, 'BATHROOM_DOWN', 'TEMPERATURE_SENSOR', 'BLINDS')""".formatted(schema));
        migrate(schema, "13");

        execute("""
            UPDATE %1$s.devices SET room = 'LOFT', name = 'Loft sensor' WHERE eaton_point = 46;
            INSERT INTO %1$s.devices (created_at, name, room, kind, vendor, enabled, shelly_address, shelly_generation)
            VALUES (now(), 'Desk lamp', 'OFFICE', 'LIGHT', 'SHELLY', TRUE, '192.0.2.17', 2);
            INSERT INTO %1$s.eaton_devices (created_at, updated_at, point, room, type, gateway) VALUES
                ('2026-10-12 21:59:30', '2026-10-12 22:00:00', 47, 'OFFICE', 'TEMPERATURE_SENSOR', 'BLINDS'),
                ('2026-10-12 21:59:40', NULL, 45, 'HALL_DOWN', 'DIMMER', 'LIGHTS')""".formatted(schema));

        migrate(schema, "latest");

        assertThat(rows("""
            SELECT name || ' | ' || room || ' | ' || kind || ' | ' || COALESCE(light_kind, '-') || ' | '
                   || COALESCE(eaton_gateway || ' ' || eaton_point, shelly_address) || ' | ' || vendor || ' ' || enabled
                   AS device
            FROM %s.devices ORDER BY id""".formatted(schema)))
            .extracting(device -> device.get("device"))
            .containsExactly(
                "Living room temperature sensor 45 | LIVING_ROOM | TEMPERATURE_SENSOR | - | BLINDS 45 | EATON true",
                "Loft sensor | LOFT | TEMPERATURE_SENSOR | - | BLINDS 46 | EATON true",
                "Desk lamp | OFFICE | LIGHT | - | 192.0.2.17 | SHELLY true",
                "Office temperature sensor 47 | OFFICE | TEMPERATURE_SENSOR | - | BLINDS 47 | EATON true",
                "Hall down light 45 | HALL_DOWN | LIGHT | DIMMER | LIGHTS 45 | EATON true"
            );

        assertThat(row("""
            SELECT to_char(created_at, 'YYYY-MM-DD HH24:MI') AS created, to_char(updated_at, 'YYYY-MM-DD HH24:MI') AS updated
            FROM %s.devices WHERE eaton_point = 47""".formatted(schema)))
            .containsEntry("created", "2026-10-12 21:59")
            .containsEntry("updated", "2026-10-12 22:00");

        assertThat(row("SELECT to_regclass('%s.eaton_devices') IS NULL AS dropped".formatted(schema)))
            .containsEntry("dropped", true);
    }

    //the database of the house at the deploy: everything was copied by V13, nothing was added to
    //the old table since - V14 copies nothing and drops it
    @Test
    void should_drop_the_table_of_the_eaton_configuration_when_the_registry_has_all_of_it() {
        final String schema = "rehearsal_drop_eaton_plain";
        startOver(schema);
        migrate(schema, "12");
        execute("""
            INSERT INTO %1$s.eaton_devices (created_at, point, room, type, gateway)
            VALUES (now(), 45, 'GARDEN', 'TEMPERATURE_SENSOR', 'BLINDS')""".formatted(schema));

        migrate(schema, "latest");

        assertThat(row("SELECT count(*) AS devices FROM %s.devices".formatted(schema))).containsEntry("devices", 1L);
        assertThat(row("SELECT to_regclass('%s.eaton_devices') IS NULL AS dropped".formatted(schema)))
            .containsEntry("dropped", true);
    }

    //a configuration of the type OTHER stored after V13 stops V14 the way one stops V13 - and
    //the table it is in stays, for whoever has to decide what it is
    @Test
    void should_not_drop_the_table_over_a_configuration_the_registry_has_no_kind_for() {
        final String schema = "rehearsal_drop_eaton_other";
        startOver(schema);
        migrate(schema, "13");
        execute("""
            INSERT INTO %1$s.eaton_devices (created_at, point, room, type, gateway)
            VALUES (now(), 9, 'GARAGE', 'OTHER', 'LIGHTS')""".formatted(schema));

        assertThatThrownBy(() -> migrate(schema, "latest"))
            .isInstanceOf(FlywayException.class)
            .hasMessageContaining("V14__drop_eaton_devices.sql")
            .hasMessageContaining("not-null constraint");

        assertThat(row("SELECT count(*) AS configurations FROM %s.eaton_devices".formatted(schema)))
            .containsEntry("configurations", 1L);
    }

    //V16 (HAS-290) over the registry as it is: every device - blinds included - comes out with no
    //opening sensor, and a row the released version inserts afterwards, not naming the column, too
    @Test
    void should_give_every_device_no_opening_sensor_also_one_an_older_release_inserts() {
        final String schema = "rehearsal_opening_sensors";
        startOver(schema);
        migrate(schema, "15");
        execute("""
            INSERT INTO %1$s.devices (created_at, name, room, kind, vendor, enabled, eaton_gateway, eaton_point)
            VALUES (now(), 'Cinema windows', 'CINEMA', 'BLINDS', 'EATON', true, 'BLINDS', 20)""".formatted(schema));

        migrate(schema, "latest");
        execute("""
            INSERT INTO %1$s.devices (created_at, name, room, kind, vendor, enabled, eaton_gateway, eaton_point)
            VALUES (now(), 'Office windows', 'OFFICE', 'BLINDS', 'EATON', true, 'BLINDS', 21)""".formatted(schema));

        assertThat(rows("SELECT cardinality(opening_sensors) AS sensors FROM %s.devices".formatted(schema)))
            .extracting(device -> device.get("sensors"))
            .containsExactly(0, 0);
    }

    //V18 and V19 (HAS-272) over the registry as it is: a device of every other kind keeps no setting of
    //an appliance, and so does a row the released version inserts afterwards, not naming the columns;
    //a member of the household is untouched and has no push subscription
    @Test
    void should_leave_every_device_without_the_settings_of_an_appliance_also_one_an_older_release_inserts() {
        final String schema = "rehearsal_appliances";
        startOver(schema);
        migrate(schema, "17");
        execute("""
            INSERT INTO %1$s.devices (created_at, name, room, kind, vendor, enabled, eaton_gateway, eaton_point)
            VALUES (now(), 'Cinema windows', 'CINEMA', 'BLINDS', 'EATON', true, 'BLINDS', 20)""".formatted(schema));
        execute("""
            INSERT INTO %1$s.household_members (created_at, name, phone, active, role, rooms, permissions)
            VALUES (now(), 'Anna', '+48111222333', true, 'RESIDENT', '{}', '{}')""".formatted(schema));

        migrate(schema, "latest");
        execute("""
            INSERT INTO %1$s.devices (created_at, name, room, kind, vendor, enabled, eaton_gateway, eaton_point)
            VALUES (now(), 'Office windows', 'OFFICE', 'BLINDS', 'EATON', true, 'BLINDS', 21)""".formatted(schema));

        assertThat(rows("""
            SELECT num_nonnulls(appliance_running_above_watts, appliance_idle_below_watts, appliance_min_run_minutes,
                appliance_finished_after_minutes, appliance_reminder_every_minutes, appliance_max_reminders,
                appliance_quiet_from, appliance_quiet_to) AS settings
            FROM %s.devices""".formatted(schema)))
            .extracting(device -> device.get("settings"))
            .containsExactly(0, 0);
        assertThat(row("SELECT count(*) AS members FROM %s.household_members".formatted(schema)))
            .containsEntry("members", 1L);
        assertThat(row("SELECT count(*) AS subscriptions FROM %s.push_subscriptions".formatted(schema)))
            .containsEntry("subscriptions", 0L);
    }

    //the container outlives the test, and with it the schema of an earlier run in the same JVM
    private void startOver(final String schema) {
        execute("DROP SCHEMA IF EXISTS %s CASCADE".formatted(schema));
    }

    //The Flyway of the application once more, with nothing changed but the schema and the version
    //it stops at - so a rehearsal runs the migrations the way a start of the service does, whatever
    //spring.flyway.* comes to say. The migrations name no schema, so they run in any; the history
    //table of Flyway goes there too
    private void migrate(final String schema, final String target) {
        Flyway.configure()
            .configuration(flyway.getConfiguration())
            .schemas(schema)
            .defaultSchema(schema)
            .target(target)
            .load()
            .migrate();
    }
}
