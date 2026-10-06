package cloud.cholewa.data.error.processor;

import cloud.cholewa.commons.error.model.ErrorId;
import cloud.cholewa.commons.error.model.ErrorMessage;
import cloud.cholewa.commons.error.model.Errors;
import cloud.cholewa.commons.error.processor.ExceptionProcessor;
import cloud.cholewa.data.error.CustomErrorDescription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.Collections;

//The one processor of the domain exceptions: they differ only in the status and in the cause they
//name, so both are parameters. The cause travels twice - its description as the message, for
//people, and its name as the code, for a caller that has to tell one error from another without
//parsing text (a 404 of the routing carries no code, "no such record" does).
@Slf4j
@RequiredArgsConstructor
public class DomainExceptionProcessor implements ExceptionProcessor {

    private final HttpStatus status;
    private final CustomErrorDescription description;

    @Override
    public Errors apply(final Throwable throwable) {
        //a 4xx is the caller's mistake: at ERROR it would feed the "Error log spike" rule for nothing
        if (status.is5xxServerError()) {
            log.error("Handled [{}]: {}", throwable.getClass().getSimpleName(), throwable.getMessage());
        } else {
            log.warn("Handled [{}]: {}", throwable.getClass().getSimpleName(), throwable.getMessage());
        }

        return Errors.builder()
            .httpStatus(status)
            .errors(Collections.singleton(
                ErrorMessage.builder()
                    .message(description.getDescription())
                    .details(throwable.getMessage())
                    .code(ErrorId.codeOf(description))
                    .build()
            ))
            .build();
    }
}
