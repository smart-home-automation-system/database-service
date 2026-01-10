package cloud.cholewa.data.device.eaton.api;

import cloud.cholewa.data.device.eaton.service.EatonDeviceConfigurationService;
import cloud.cholewa.data.error.DeviceConfigurationNotFoundException;
import cloud.cholewa.data.error.InvalidDeviceConfigurationException;
import cloud.cholewa.home.model.EatonConfigurationResponse;
import cloud.cholewa.home.model.EatonDeviceConfiguration;
import cloud.cholewa.home.model.EatonGatewayType;
import cloud.cholewa.home.model.RoomName;
import cloud.cholewa.home.model.SmartDeviceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = EatonDeviceConfigurationController.class)
class EatonDeviceConfigurationControllerTest {

    private static final EatonDeviceConfiguration EATON_DEVICE_CONFIGURATION = EatonDeviceConfiguration.builder()
        .point(1).type(SmartDeviceType.BLINDS).gateway(EatonGatewayType.BLINDS).room(RoomName.LIVING_ROOM).build();

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private EatonDeviceConfigurationService eatonDeviceConfigurationService;

    @Test
    void should_add_device_configuration() {
        when(eatonDeviceConfigurationService.add(any())).thenReturn(Mono.empty());

        webTestClient.post()
            .uri("/device/configuration")
            .body(BodyInserters.fromValue(EATON_DEVICE_CONFIGURATION))
            .exchange()
            .expectStatus().isOk()
            .expectBody(Void.class);
    }

    @Test
    void should_return_error_when_adding_device_configuration_fails() {
        when(eatonDeviceConfigurationService.add(any()))
            .thenReturn(Mono.error(new InvalidDeviceConfigurationException("test")));

        webTestClient.post()
            .uri("/device/configuration")
            .body(BodyInserters.fromValue(EATON_DEVICE_CONFIGURATION))
            .exchange()
            .expectStatus().is5xxServerError();
    }

    @Test
    void should_get_eaton_device_configuration() {
        when(eatonDeviceConfigurationService.get(anyInt(), anyString()))
            .thenReturn(Mono.just(EatonConfigurationResponse.builder().build()));

        webTestClient.get()
            .uri("/device/configuration/eaton?dataPoint=1&gateway=blinds")
            .exchange()
            .expectStatus().isOk()
            .expectBody(EatonConfigurationResponse.class);
    }

    @Test
    void should_return_error_when_getting_eaton_device_configuration_fails() {
        when(eatonDeviceConfigurationService.get(anyInt(), anyString()))
            .thenReturn(Mono.error(new DeviceConfigurationNotFoundException("test")));

        webTestClient.get()
            .uri("/device/configuration/eaton?dataPoint=1&gateway=blinds")
            .exchange()
            .expectStatus().is5xxServerError();
    }
}
