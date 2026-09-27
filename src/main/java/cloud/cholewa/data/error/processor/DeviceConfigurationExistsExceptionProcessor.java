package cloud.cholewa.data.error.processor;

import cloud.cholewa.commons.error.model.ErrorMessage;
import cloud.cholewa.commons.error.model.Errors;
import cloud.cholewa.commons.error.processor.ExceptionProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.Collections;

@Slf4j
public class DeviceConfigurationExistsExceptionProcessor implements ExceptionProcessor {

    @Override
    public Errors apply(final Throwable throwable) {
        log.warn("Handled [{}]: {}", throwable.getClass().getSimpleName(), throwable.getMessage());

        return Errors.builder()
            .httpStatus(HttpStatus.CONFLICT)
            .errors(Collections.singleton(
                ErrorMessage.builder()
                    .message("Device configuration already exists")
                    .details(throwable.getMessage())
                    .build()
            ))
            .build();
    }
}
