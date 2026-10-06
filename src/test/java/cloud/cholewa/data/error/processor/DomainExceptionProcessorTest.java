package cloud.cholewa.data.error.processor;

import cloud.cholewa.commons.error.model.ErrorMessage;
import cloud.cholewa.commons.error.model.Errors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;

import static cloud.cholewa.data.error.CustomErrorDescription.NOT_FOUND_DEVICE_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class DomainExceptionProcessorTest {

    private static final RuntimeException EXCEPTION = new IllegalStateException("Device not found for point: 7");

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
    void should_log_a_client_error_as_a_warning(final CapturedOutput output) {
        new DomainExceptionProcessor(HttpStatus.NOT_FOUND, NOT_FOUND_DEVICE_CONFIGURATION).apply(EXCEPTION);

        assertThat(output.getOut())
            .containsPattern("WARN .*Handled \\[IllegalStateException]: Device not found for point: 7")
            .doesNotContain("ERROR");
    }

    @Test
    void should_log_a_server_error_as_an_error(final CapturedOutput output) {
        new DomainExceptionProcessor(HttpStatus.INTERNAL_SERVER_ERROR, NOT_FOUND_DEVICE_CONFIGURATION)
            .apply(EXCEPTION);

        assertThat(output.getOut())
            .containsPattern("ERROR .*Handled \\[IllegalStateException]: Device not found for point: 7");
    }
}
