package cloud.cholewa.data.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import java.time.Duration;

@Configuration
@EnableR2dbcRepositories
@RequiredArgsConstructor
@EnableConfigurationProperties(DatabaseProperties.class)
public class DbConfig {

    private static final int POOL_INITIAL_SIZE = 2;
    private static final int POOL_MAX_SIZE = 6;
    private static final Duration POOL_MAX_ACQUIRE_TIME = Duration.ofSeconds(10);
    private static final Duration POOL_MAX_IDLE_TIME = Duration.ofMinutes(5);

    private final DatabaseProperties databaseProperties;

    //destroyMethod is explicit: the inferred close() returns a cold Publisher nobody subscribes to,
    //so the pooled connections would survive every shutdown
    @Bean(destroyMethod = "dispose")
    ConnectionFactory connectionFactory() {
        ConnectionFactory connectionFactory = ConnectionFactories.get(ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "postgresql")
            .option(ConnectionFactoryOptions.HOST, databaseProperties.host())
            .option(ConnectionFactoryOptions.PORT, databaseProperties.port())
            .option(ConnectionFactoryOptions.DATABASE, databaseProperties.name())
            .option(ConnectionFactoryOptions.USER, databaseProperties.username())
            .option(ConnectionFactoryOptions.PASSWORD, databaseProperties.password())
            .option(Option.valueOf("sslMode"), "REQUIRE")
            .build()
        );

        //R2dbcAutoConfiguration backs off once this bean exists, so without the pool every query -
        //and every /actuator/health call, which the health indicator answers with its own connection -
        //opens a physical connection to a managed database that allows 22 of them in total
        return new ConnectionPool(ConnectionPoolConfiguration.builder(connectionFactory)
            .name("database-service")
            .initialSize(POOL_INITIAL_SIZE)
            .maxSize(POOL_MAX_SIZE)
            .maxAcquireTime(POOL_MAX_ACQUIRE_TIME)
            .maxIdleTime(POOL_MAX_IDLE_TIME)
            .build()
        );
    }
}
