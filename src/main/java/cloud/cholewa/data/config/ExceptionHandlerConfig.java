package cloud.cholewa.data.config;

import cloud.cholewa.commons.error.GlobalErrorExceptionHandler;
import cloud.cholewa.data.error.DeviceConfigurationExistsException;
import cloud.cholewa.data.error.DeviceConfigurationNotFoundException;
import cloud.cholewa.data.error.HouseholdException;
import cloud.cholewa.data.error.HouseholdMemberNotFoundException;
import cloud.cholewa.data.error.InvalidDeviceConfigurationException;
import cloud.cholewa.data.error.MemberDeviceException;
import cloud.cholewa.data.error.MemberDeviceNotFoundException;
import cloud.cholewa.data.error.processor.DomainExceptionProcessor;
import cloud.cholewa.data.error.processor.InvalidRequestParameterProcessor;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webflux.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Map;

import static cloud.cholewa.data.error.CustomErrorDescription.CONFIGURATION_EXIST;
import static cloud.cholewa.data.error.CustomErrorDescription.DEVICE_EXIST;
import static cloud.cholewa.data.error.CustomErrorDescription.HOUSEHOLD_CONFLICT;
import static cloud.cholewa.data.error.CustomErrorDescription.NOT_FOUND_DEVICE_CONFIGURATION;
import static cloud.cholewa.data.error.CustomErrorDescription.NOT_FOUND_HOUSEHOLD_MEMBER;
import static cloud.cholewa.data.error.CustomErrorDescription.NOT_FOUND_MEMBER_DEVICE;
import static cloud.cholewa.data.error.CustomErrorDescription.UNKNOWN_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Configuration
public class ExceptionHandlerConfig {

    @Bean
    @Order(-2)
    public GlobalErrorExceptionHandler globalErrorExceptionHandler(
        final ErrorAttributes errorAttributes,
        final WebProperties webProperties,
        final ApplicationContext applicationContext,
        final ServerCodecConfigurer serverCodecConfigurer
    ) {
        GlobalErrorExceptionHandler globalErrorExceptionHandler = new GlobalErrorExceptionHandler(
            errorAttributes, webProperties.getResources(), applicationContext, serverCodecConfigurer
        );

        //a domain exception is a status and a named cause; the name is the code of the response.
        //No own entry for DuplicateKeyException: every known duplicate is mapped to a domain exception
        //by its service, and anything else falls to the cholewa-commons default (409 "Duplicate Key")
        globalErrorExceptionHandler.withCustomErrorProcessor(
            Map.ofEntries(
                Map.entry(InvalidDeviceConfigurationException.class, new DomainExceptionProcessor(BAD_REQUEST, UNKNOWN_GATEWAY)),
                Map.entry(DeviceConfigurationNotFoundException.class, new DomainExceptionProcessor(NOT_FOUND, NOT_FOUND_DEVICE_CONFIGURATION)),
                Map.entry(DeviceConfigurationExistsException.class, new DomainExceptionProcessor(CONFLICT, CONFIGURATION_EXIST)),
                Map.entry(HouseholdMemberNotFoundException.class, new DomainExceptionProcessor(NOT_FOUND, NOT_FOUND_HOUSEHOLD_MEMBER)),
                Map.entry(HouseholdException.class, new DomainExceptionProcessor(CONFLICT, HOUSEHOLD_CONFLICT)),
                Map.entry(MemberDeviceException.class, new DomainExceptionProcessor(CONFLICT, DEVICE_EXIST)),
                Map.entry(MemberDeviceNotFoundException.class, new DomainExceptionProcessor(NOT_FOUND, NOT_FOUND_MEMBER_DEVICE)),
                //constraints on query parameters and path variables; without it the cholewa-commons
                //default answers a bare "Validation failure". Not a named cause, so no code
                Map.entry(HandlerMethodValidationException.class, new InvalidRequestParameterProcessor())
            )
        );

        return globalErrorExceptionHandler;
    }
}
