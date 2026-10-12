package cloud.cholewa.data.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(migrations).isGreaterThanOrEqualTo(13);
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
            "eaton_devices_point_gateway_uq",
            "devices_shelly_address_channel_uq",
            "devices_eaton_gateway_point_uq",
            "devices_bthome_gateway_address_uq"
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

    //V7 over a table that holds the same point of a gateway twice: the older row stays
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

        migrate(schema, "latest");

        assertThat(rows("SELECT room FROM %s.eaton_devices ORDER BY id".formatted(schema)))
            .extracting(configuration -> configuration.get("room"))
            .containsExactly("OFFICE", "LOFT");
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
