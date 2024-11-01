package cloud.cholewa.data.error.processor;

import cloud.cholewa.commons.error.model.ErrorMessage;
import cloud.cholewa.commons.error.model.Errors;
import cloud.cholewa.commons.error.processor.ExceptionProcessor;
import cloud.cholewa.data.error.CustomErrorDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.Collections;

@Slf4j
public class DeviceConfigurationNotFoundExceptionProcessor implements ExceptionProcessor {

    @Override
    public Errors apply(final Throwable throwable) {

        log.error(throwable.getLocalizedMessage());

        return Errors.builder()
            .httpStatus(HttpStatus.NOT_FOUND)
            .errors(Collections.singleton(
                ErrorMessage.builder()
                    .message(CustomErrorDescription.NOT_FOUND_DEVICE_CONFIGURATION.getDescription())
                    .details(throwable.getMessage())
                    .build()
            ))
            .build();
    }
}
