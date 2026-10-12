package cloud.cholewa.data.device.registry.api;

import cloud.cholewa.data.config.ExceptionHandlerConfig;
import cloud.cholewa.data.device.registry.service.DeviceRegistryService;
import cloud.cholewa.data.error.DeviceNotFoundException;
import cloud.cholewa.home.model.BlindsPlacement;
import cloud.cholewa.home.model.DeviceKind;
import cloud.cholewa.home.model.EatonBinding;
import cloud.cholewa.home.model.EatonGatewayType;
import cloud.cholewa.home.model.LightKind;
import cloud.cholewa.home.model.RoomName;
import cloud.cholewa.home.model.ShellyBinding;
import cloud.cholewa.home.model.SmartDevice;
import cloud.cholewa.home.model.SmartVendor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Import(ExceptionHandlerConfig.class)
@WebFluxTest(controllers = DeviceRegistryController.class)
class DeviceRegistryControllerTest {

    private static final SmartDevice BLINDS = SmartDevice.builder()
        .id(17L)
        .name("Office window")
        .room(RoomName.OFFICE)
        .kind(DeviceKind.BLINDS)
        .enabled(true)
        .vendor(SmartVendor.SHELLY)
        .shelly(ShellyBinding.builder()
            .address("192.0.2.17")
            .generation(2)
            .deviceId("shellyplus2pm-a1b2c3d4e5f6")
            .channel(0)
            .build())
        .placement(BlindsPlacement.EXTERNAL)
        .positioning(true)
        .build();

    private static final SmartDevice LIGHT = SmartDevice.builder()
        .id(18L)
        .name("Kitchen ceiling")
        .room(RoomName.KITCHEN)
        .kind(DeviceKind.LIGHT)
        .enabled(false)
        .vendor(SmartVendor.EATON)
        .eaton(EatonBinding.builder().gateway(EatonGatewayType.LIGHTS).point(44).build())
        .lightKind(LightKind.DIMMER)
        .build();

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DeviceRegistryService deviceRegistryService;

    //The JSON a reader gets, written out: the values of the enums and not the names of their
    //constants, the id, and a binding per device - the other two are not there at all
    @Test
    void should_return_the_devices_as_the_sdk_writes_them() {
        when(deviceRegistryService.find(null, null, null)).thenReturn(Mono.just(List.of(BLINDS, LIGHT)));

        webTestClient.get()
            .uri("/device/registry")
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("""
                [
                  {"id": 17, "name": "Office window", "room": "office", "kind": "blinds", "enabled": true,
                   "vendor": "shelly",
                   "shelly": {"address": "192.0.2.17", "generation": 2,
                              "deviceId": "shellyplus2pm-a1b2c3d4e5f6", "channel": 0},
                   "placement": "external", "positioning": true},
                  {"id": 18, "name": "Kitchen ceiling", "room": "kitchen", "kind": "light", "enabled": false,
                   "vendor": "eaton",
                   "eaton": {"gateway": "lights", "point": 44},
                   "lightKind": "dimmer"}
                ]""", JsonCompareMode.STRICT);
    }

    @Test
    void should_pass_the_filters_to_the_service_as_they_were_sent() {
        when(deviceRegistryService.find(any(), any(), any())).thenReturn(Mono.just(List.of()));

        webTestClient.get()
            .uri(uri -> uri.path("/device/registry")
                .queryParam("kind", "opening sensor")
                .queryParam("room", "living room")
                .queryParam("enabled", "true")
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("[]");

        verify(deviceRegistryService).find("opening sensor", "living room", true);
    }

    @Test
    void should_answer_an_empty_list_when_nothing_is_registered() {
        when(deviceRegistryService.find(null, null, null)).thenReturn(Mono.just(List.of()));

        webTestClient.get()
            .uri("/device/registry")
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("[]");
    }

    //what the service answers a filter it cannot read with: 400, no code - not a named cause
    @Test
    void should_answer_bad_request_for_a_filter_the_service_refuses() {
        when(deviceRegistryService.find("thermostat", null, null))
            .thenReturn(Mono.error(new ServerWebInputException("Unknown device kind: thermostat")));

        webTestClient.get()
            .uri("/device/registry?kind=thermostat")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errors[0].code").doesNotExist();
    }

    @Test
    void should_answer_bad_request_for_enabled_that_is_not_a_boolean() {
        webTestClient.get()
            .uri("/device/registry?enabled=maybe")
            .exchange()
            .expectStatus().isBadRequest();

        verifyNoInteractions(deviceRegistryService);
    }

    @Test
    void should_return_one_device_by_its_id() {
        when(deviceRegistryService.get(18L)).thenReturn(Mono.just(LIGHT));

        webTestClient.get()
            .uri("/device/registry/18")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(18)
            .jsonPath("$.eaton.point").isEqualTo(44)
            .jsonPath("$.shelly").doesNotExist()
            .jsonPath("$.bthome").doesNotExist();
    }

    //the code a caller tells "no such device" from a broken route by
    @Test
    void should_answer_not_found_with_its_code_for_a_device_that_is_not_registered() {
        when(deviceRegistryService.get(99L)).thenReturn(Mono.error(DeviceNotFoundException.forId(99L)));

        webTestClient.get()
            .uri("/device/registry/99")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("NOT_FOUND_DEVICE")
            .jsonPath("$.errors[0].message").isEqualTo("Device not found")
            .jsonPath("$.errors[0].details").isEqualTo("Device not found for id: 99");
    }

    @Test
    void should_answer_bad_request_for_an_id_that_is_not_a_number() {
        webTestClient.get()
            .uri("/device/registry/lamp")
            .exchange()
            .expectStatus().isBadRequest();

        verifyNoInteractions(deviceRegistryService);
    }
}
