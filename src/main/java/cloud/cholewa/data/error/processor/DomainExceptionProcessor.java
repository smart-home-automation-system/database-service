package cloud.cholewa.data.error.processor;

import cloud.cholewa.commons.error.model.ErrorId;
import cloud.cholewa.commons.error.model.ErrorMessage;
import cloud.cholewa.commons.error.model.Errors;
import cloud.cholewa.commons.error.processor.ExceptionProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.Collections;

//The one processor of the domain exceptions: they differ only in the status and in the cause they
//name, so both are parameters. The cause travels twice - its description as the message, for
//people, and its name as the code, for a caller that has to tell one error from another without
//parsing text (a 404 of the routing carries no code, "no such record" does).
//Nothing here is specific to this service - it takes any ErrorId - so it can move to
//cholewa-commons once a second service sends codes
@Slf4j
@RequiredArgsConstructor
public class DomainExceptionProcessor implements ExceptionProcessor {

    private final HttpStatus status;
    private final ErrorId cause;

    @Override
    public Errors apply(final Throwable throwable) {
        final boolean serverError = status.is5xxServerError();

        if (serverError) {
            //a failure of the service: the stack trace is what there is to diagnose it from
            log.error("Handled [{}]: {}", throwable.getClass().getSimpleName(), throwable.getMessage(), throwable);
        } else {
            //a 4xx is the caller's mistake: at ERROR it would feed the "Error log spike" rule for nothing
            log.warn("Handled [{}]: {}", throwable.getClass().getSimpleName(), throwable.getMessage());
        }

        return Errors.builder()
            .httpStatus(status)
            .errors(Collections.singleton(
                ErrorMessage.builder()
                    .message(cause.getDescription())
                    //the message of a 4xx is written for the caller; the one of a 5xx is whatever
                    //failed inside, and stays in the log
                    .details(serverError ? null : throwable.getMessage())
                    .code(ErrorId.codeOf(cause))
                    .build()
            ))
            .build();
    }
}
