package cloud.cholewa.data.device.api;

import cloud.cholewa.data.device.service.DeviceConfigurationService;
import cloud.cholewa.home.model.DeviceConfiguration;
import cloud.cholewa.home.model.DeviceConfigurationRequest;
import cloud.cholewa.home.model.EatonConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = DeviceConfigurationController.class)
class DeviceConfigurationControllerTest {

    @Autowired
    private WebTestClient client;

    @MockBean
    private DeviceConfigurationService service;

    @Test
    void should_return_ok_when_device_configuration_valid() {
        Mockito.when(service.addDeviceConfiguration(Mockito.any()))
            .thenReturn(Mono.empty());

        client.post()
            .uri("/device/configuration")
            .body(BodyInserters.fromValue(DeviceConfigurationRequest.builder()
                .roomName("test")
                .iotVendor("test")
                .deviceType("test")
                .deviceConfiguration(DeviceConfiguration.builder()
                    .eatonConfiguration(EatonConfiguration.builder().build())
                    .build())
                .build()))
            .exchange()
            .expectStatus().isOk()
            .expectBody(Void.class);
    }
}