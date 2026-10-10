package cloud.cholewa.data.postgres;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.DockerClientFactory;

import static org.assertj.core.api.Assertions.assertThat;

//The tests on PostgreSQL are skipped where there is no Docker, so that a build on such a machine
//stays green. On a build server that kindness would be a hole: a runner without a Docker would
//skip every one of them and report success. GitHub Actions sets CI=true, and there this test
//fails instead.
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class DockerInCiTest {

    @Test
    void should_have_a_docker_for_the_tests_on_postgres() {
        assertThat(DockerClientFactory.instance().isDockerAvailable())
            .as("no Docker on a build server: the tests on PostgreSQL were skipped")
            .isTrue();
    }
}
