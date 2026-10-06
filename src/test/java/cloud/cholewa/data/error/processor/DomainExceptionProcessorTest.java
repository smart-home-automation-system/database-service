package cloud.cholewa.data.error.processor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cloud.cholewa.commons.error.model.ErrorMessage;
import cloud.cholewa.commons.error.model.Errors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import static cloud.cholewa.data.error.CustomErrorDescription.NOT_FOUND_DEVICE_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionProcessorTest {

    private static final RuntimeException EXCEPTION = new IllegalStateException("Device not found for point: 7");

    //the events themselves, not the console: the text there depends on the encoder the JVM has
    //installed (plain or logstash), the level of an event does not
    private final Logger logger = (Logger) LoggerFactory.getLogger(DomainExceptionProcessor.class);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureTheLog() {
        logged.start();
        logger.addAppender(logged);
    }

    @AfterEach
    void releaseTheLog() {
        logger.detachAppender(logged);
    }

    @Test
    void should_answer_with_the_status_the_description_and_the_name_of_the_cause() {
        final Errors errors = new DomainExceptionProcessor(HttpStatus.NOT_FOUND, NOT_FOUND_DEVICE_CONFIGURATION)
            .apply(EXCEPTION);

        assertThat(errors.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(errors.getErrors()).containsExactly(
            ErrorMessage.builder()
                .message("Device configuration not found")
                .details("Device not found for point: 7")
                .code("NOT_FOUND_DEVICE_CONFIGURATION")
                .build()
        );
    }

    //a 4xx at ERROR would feed the Grafana "Error log spike" rule for a mistake of the caller
    @Test
    void should_log_a_client_error_as_a_warning_without_a_stack_trace() {
        new DomainExceptionProcessor(HttpStatus.NOT_FOUND, NOT_FOUND_DEVICE_CONFIGURATION).apply(EXCEPTION);

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                .isEqualTo("Handled [IllegalStateException]: Device not found for point: 7");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void should_log_a_server_error_as_an_error_with_its_stack_trace() {
        new DomainExceptionProcessor(HttpStatus.INTERNAL_SERVER_ERROR, NOT_FOUND_DEVICE_CONFIGURATION)
            .apply(EXCEPTION);

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage())
                .isEqualTo("Handled [IllegalStateException]: Device not found for point: 7");
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    //what failed inside the service is not the caller's to read: the cause is named, the text stays in the log
    @Test
    void should_keep_the_exception_message_of_a_server_error_out_of_the_response() {
        final Errors errors = new DomainExceptionProcessor(HttpStatus.INTERNAL_SERVER_ERROR, NOT_FOUND_DEVICE_CONFIGURATION)
            .apply(EXCEPTION);

        assertThat(errors.getErrors()).containsExactly(
            ErrorMessage.builder()
                .message("Device configuration not found")
                .code("NOT_FOUND_DEVICE_CONFIGURATION")
                .build()
        );
    }
}
