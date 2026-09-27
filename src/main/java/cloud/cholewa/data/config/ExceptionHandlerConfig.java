package cloud.cholewa.data.config;

import cloud.cholewa.commons.error.GlobalErrorExceptionHandler;
import cloud.cholewa.data.error.DeviceConfigurationExistsException;
import cloud.cholewa.data.error.DeviceConfigurationNotFoundException;
import cloud.cholewa.data.error.HouseholdException;
import cloud.cholewa.data.error.HouseholdMemberNotFoundException;
import cloud.cholewa.data.error.InvalidDeviceConfigurationException;
import cloud.cholewa.data.error.MemberDeviceException;
import cloud.cholewa.data.error.MemberDeviceNotFoundException;
import cloud.cholewa.data.error.processor.DeviceConfigurationNotFoundExceptionProcessor;
import cloud.cholewa.data.error.processor.DeviceConfigurationExistsExceptionProcessor;
import cloud.cholewa.data.error.processor.HouseholdExceptionProcessor;
import cloud.cholewa.data.error.processor.HouseholdMemberNotFoundExceptionProcessor;
import cloud.cholewa.data.error.processor.InvalidDeviceConfigurationExceptionProcessor;
import cloud.cholewa.data.error.processor.MemberDeviceExceptionProcessor;
import cloud.cholewa.data.error.processor.MemberDeviceNotFoundExceptionProcessor;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webflux.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;

import java.util.Map;

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

        globalErrorExceptionHandler.withCustomErrorProcessor(
            Map.ofEntries(
                Map.entry(
                    InvalidDeviceConfigurationException.class,
                    new InvalidDeviceConfigurationExceptionProcessor()
                ),
                Map.entry(
                    DeviceConfigurationNotFoundException.class,
                    new DeviceConfigurationNotFoundExceptionProcessor()
                ),
                //no own entry for DuplicateKeyException: every known duplicate is mapped to a domain exception
                //by its service, and anything else falls to the cholewa-commons default (409 "Duplicate Key")
                Map.entry(DeviceConfigurationExistsException.class, new DeviceConfigurationExistsExceptionProcessor()),
                Map.entry(
                    HouseholdMemberNotFoundException.class,
                    new HouseholdMemberNotFoundExceptionProcessor()
                ),
                Map.entry(HouseholdException.class, new HouseholdExceptionProcessor()),
                Map.entry(MemberDeviceException.class, new MemberDeviceExceptionProcessor()),
                Map.entry(MemberDeviceNotFoundException.class, new MemberDeviceNotFoundExceptionProcessor())
            )
        );

        return globalErrorExceptionHandler;
    }
}
