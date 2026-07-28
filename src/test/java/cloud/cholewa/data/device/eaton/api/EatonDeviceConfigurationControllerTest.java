package cloud.cholewa.data.device.eaton.api;

import cloud.cholewa.data.config.ExceptionHandlerConfig;
import cloud.cholewa.data.device.eaton.service.EatonDeviceConfigurationService;
import cloud.cholewa.data.error.DeviceConfigurationNotFoundException;
import cloud.cholewa.data.error.InvalidDeviceConfigurationException;
import cloud.cholewa.home.model.EatonConfigurationResponse;
import cloud.cholewa.home.model.EatonDeviceConfiguration;
import cloud.cholewa.home.model.EatonGatewayType;
import cloud.cholewa.home.model.RoomName;
import cloud.cholewa.home.model.SmartDeviceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Stream;

import static cloud.cholewa.home.model.EatonGatewayType.LIGHTS;
import static cloud.cholewa.home.model.RoomName.BEDROOM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Import(ExceptionHandlerConfig.class)
@WebFluxTest(controllers = EatonDeviceConfigurationController.class)
class EatonDeviceConfigurationControllerTest {

    private static final EatonDeviceConfiguration EATON_DEVICE_CONFIGURATION =
        EatonDeviceConfiguration.builder()
            .point(1)
            .type(SmartDeviceType.BLINDS)
            .gateway(EatonGatewayType.BLINDS)
            .room(RoomName.LIVING_ROOM)
            .build();

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private EatonDeviceConfigurationService eatonDeviceConfigurationService;

    @Test
    void should_add_device_configuration() {
        when(eatonDeviceConfigurationService.add(any())).thenReturn(Mono.empty());

        webTestClient.post()
            .uri("/device/configuration/eaton")
            .body(BodyInserters.fromValue(EATON_DEVICE_CONFIGURATION))
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Void.class);
    }

    @Test
    void should_return_error_when_adding_device_configuration_fails() {
        when(eatonDeviceConfigurationService.add(any()))
            .thenReturn(Mono.error(new InvalidDeviceConfigurationException("test")));

        webTestClient.post()
            .uri("/device/configuration/eaton")
            .body(BodyInserters.fromValue(EATON_DEVICE_CONFIGURATION))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void should_get_eaton_device_configuration() {
        when(eatonDeviceConfigurationService.get(anyInt(), anyString()))
            .thenReturn(Mono.just(EatonConfigurationResponse.builder().build()));

        webTestClient.get()
            .uri("/device/configuration/eaton?point=1&gateway=blinds")
            .exchange()
            .expectStatus().isOk()
            .expectBody(EatonConfigurationResponse.class);
    }

    @Test
    void should_return_error_when_getting_eaton_device_configuration_fails() {
        when(eatonDeviceConfigurationService.get(anyInt(), anyString()))
            .thenReturn(Mono.error(new DeviceConfigurationNotFoundException("test")));

        webTestClient.get()
            .uri("/device/configuration/eaton?point=1&gateway=blinds")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void should_return_bad_request_when_configuration_violates_unique_constraint() {
        when(eatonDeviceConfigurationService.add(any()))
            .thenReturn(Mono.error(new DuplicateKeyException("test")));

        webTestClient.post()
            .uri("/device/configuration/eaton")
            .body(BodyInserters.fromValue(EATON_DEVICE_CONFIGURATION))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errors[0].message").isEqualTo("Invalid device configuration")
            .jsonPath("$.errors[0].details").isEqualTo("Configuration exist in database");
    }

    @Test
    void should_return_bad_request_when_unknown_gateway() {
        when(eatonDeviceConfigurationService.get(anyInt(), anyString()))
            .thenReturn(Mono.error(new InvalidDeviceConfigurationException("Unknown Eaton gateway: garden")));

        webTestClient.get()
            .uri("/device/configuration/eaton?point=1&gateway=garden")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errors[0].message").isEqualTo("Invalid device configuration")
            .jsonPath("$.errors[0].details").isEqualTo("Unknown Eaton gateway: garden");
    }

    @Test
    void should_return_bad_request_when_body_missing() {
        webTestClient.post()
            .uri("/device/configuration/eaton")
            .body(BodyInserters.empty())
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errors[0].message").isEqualTo("Missing request body");

        verifyNoInteractions(eatonDeviceConfigurationService);
    }

    @Test
    void should_return_bad_request_when_all_fields_missing() {
        webTestClient.post()
            .uri("/device/configuration/eaton")
            .body(BodyInserters.fromValue(EatonDeviceConfiguration.builder().build()))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errors.length()").isEqualTo(4)
            .jsonPath("$.errors[*].details")
            .value((List<String> details) -> assertThat(details).containsExactlyInAnyOrder(
                "point",
                "type",
                "gateway",
                "room"
            ));

        verifyNoInteractions(eatonDeviceConfigurationService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideInvalidEatonDeviceConfiguration")
    void should_return_bad_request_when_invalid_configuration(
        final String name,
        final EatonDeviceConfiguration invalidConfiguration,
        final String details
    ) {
        webTestClient.post()
            .uri("/device/configuration/eaton")
            .body(BodyInserters.fromValue(invalidConfiguration))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errors[0].message").isEqualTo("Missing or invalid parameter")
            .jsonPath("$.errors[0].details").isEqualTo(details);

        verifyNoInteractions(eatonDeviceConfigurationService);
    }

    private static Stream<Arguments> provideInvalidEatonDeviceConfiguration() {
        return Stream.of(
            Arguments.of(
                "no point",
                EatonDeviceConfiguration.builder().type(SmartDeviceType.BLINDS).gateway(LIGHTS).room(BEDROOM).build(),
                "point"
            ),
            Arguments.of(
                "no gateway",
                EatonDeviceConfiguration.builder().point(1).type(SmartDeviceType.BLINDS).room(BEDROOM).build(),
                "gateway"
            ),
            Arguments.of(
                "no type",
                EatonDeviceConfiguration.builder().point(1).gateway(LIGHTS).room(BEDROOM).build(),
                "type"
            ),
            Arguments.of(
                "no room",
                EatonDeviceConfiguration.builder().point(1).type(SmartDeviceType.BLINDS).gateway(LIGHTS).build(),
                "room"
            ),
            Arguments.of(
                "point less than 1",
                EatonDeviceConfiguration.builder().point(0).type(SmartDeviceType.BLINDS).gateway(LIGHTS).room(BEDROOM).build(),
                "point"
            ),
            Arguments.of(
                "point greater than 99",
                EatonDeviceConfiguration.builder().point(100).type(SmartDeviceType.BLINDS).gateway(LIGHTS).room(BEDROOM).build(),
                "point"
            )
        );
    }
}
