package cloud.cholewa.data.postgres;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

//The tests that touch a database: the whole application - its own configuration of Flyway, the pooled
//connection of cholewa-commons, the repositories, mappers, services and the error mapping, nothing
//mocked - on a PostgreSQL that lives in Docker for the length of the build (Testcontainers). Flyway
//is switched off in the test profile and switched on here, so the migrations of the service build
//the schema, from an empty database, when the context starts.
//
//One container and one context for every class that extends this one: the container is started by
//the first of them and never stopped (Testcontainers removes it when the JVM ends). A @Container
//field would stop it after each class, and Spring would hand the next class the cached context
//with a pool that points at a port nobody listens on.
//
//Without a Docker the classes are skipped, not failed - the build of a machine without one stays
//green and says so in its count of skipped tests. DockerInCiTest makes sure that never happens
//where it would go unnoticed.
@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
abstract class OnPostgres {

    //A query that gets no answer must fail its test, not hold the build until the build server
    //gives up. Far above anything a statement takes here (milliseconds), far below that
    protected static final Duration PATIENCE = Duration.ofSeconds(30);

    private static PostgreSQLContainer container;

    @Autowired
    protected DatabaseClient databaseClient;

    //Every address the application could connect to is given here, the one of Flyway included, so
    //nothing of the machine - an environment variable, an env file of the IDE - can point a test at
    //a database that is not the container. The container speaks no SSL, hence the mode: the
    //default of the library is REQUIRE
    @DynamicPropertySource
    static void connectToTheContainer(final DynamicPropertyRegistry registry) {
        final PostgreSQLContainer postgres = postgres();

        registry.add("database.host", postgres::getHost);
        registry.add("database.port", () -> postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT));
        registry.add("database.name", postgres::getDatabaseName);
        registry.add("database.username", postgres::getUsername);
        registry.add("database.password", postgres::getPassword);
        registry.add("database.ssl-mode", () -> "disable");

        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    //Started on first use, which is after JUnit has decided that the class runs at all. A start
    //that fails is tried again by the next class and reports its own reason each time - a static
    //initializer would report it once and a NoClassDefFoundError ever after.
    //The major version of the rehearsals of V11 and V12; alpine for the size of the download
    static synchronized PostgreSQLContainer postgres() {
        if (container == null) {
            final PostgreSQLContainer starting = new PostgreSQLContainer("postgres:17-alpine");
            starting.start();
            container = starting;
        }
        return container;
    }

    //the limit of every StepVerifier of these classes, theirs alone: given back after each class,
    //the other tests of the build keep the default
    @BeforeAll
    static void boundEveryVerification() {
        StepVerifier.setDefaultTimeout(PATIENCE);
    }

    @AfterAll
    static void unboundTheVerifications() {
        StepVerifier.resetDefaultTimeout();
    }

    //the devices go with their members (ON DELETE CASCADE)
    @BeforeEach
    void emptyTheTables() {
        execute("DELETE FROM household_members");
        execute("DELETE FROM eaton_devices");
        execute("DELETE FROM devices");
    }

    protected void execute(final String sql) {
        databaseClient.sql(sql).then().as(StepVerifier::create).verifyComplete();
    }

    protected List<Map<String, Object>> rows(final String sql) {
        return databaseClient.sql(sql).fetch().all().collectList().block(PATIENCE);
    }

    protected Map<String, Object> row(final String sql) {
        final List<Map<String, Object>> rows = rows(sql);
        if (rows.size() != 1) {
            throw new AssertionError("Expected one row, got " + rows.size() + " for: " + sql);
        }
        return rows.getFirst();
    }
}
