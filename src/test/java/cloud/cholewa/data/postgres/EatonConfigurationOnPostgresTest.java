package cloud.cholewa.data.postgres;

import cloud.cholewa.data.device.eaton.model.EatonDeviceConfigurationEntity;
import cloud.cholewa.data.device.eaton.repository.EatonDeviceConfigurationRepository;
import cloud.cholewa.data.device.eaton.service.EatonDeviceConfigurationService;
import cloud.cholewa.data.error.DeviceConfigurationExistsException;
import cloud.cholewa.data.error.DeviceConfigurationNotFoundException;
import cloud.cholewa.home.model.EatonConfigurationResponse;
import cloud.cholewa.home.model.EatonDeviceConfiguration;
import cloud.cholewa.home.model.EatonGatewayType;
import cloud.cholewa.home.model.RoomName;
import cloud.cholewa.home.model.SmartDeviceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

//The Eaton configuration on a real PostgreSQL - the lookup amx-service makes for every datagram.
class EatonConfigurationOnPostgresTest extends OnPostgres {

    @Autowired
    private EatonDeviceConfigurationService eatonDeviceConfigurationService;

    @Autowired
    private EatonDeviceConfigurationRepository eatonDeviceConfigurationRepository;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void should_store_a_configuration_with_its_enums_as_the_names_of_their_constants() {
        add(7, EatonGatewayType.LIGHTS, RoomName.LIVING_ROOM, SmartDeviceType.TEMPERATURE_SENSOR);

        assertThat(row("SELECT point, room, type, gateway, created_at IS NOT NULL AS created FROM eaton_devices"))
            .containsEntry("room", "LIVING_ROOM")
            .containsEntry("type", "TEMPERATURE_SENSOR")
            .containsEntry("gateway", "LIGHTS")
            .containsEntry("created", true)
            .satisfies(stored -> assertThat(((Number) stored.get("point")).intValue()).isEqualTo(7));
    }

    @Test
    void should_find_a_configuration_by_its_point_and_gateway() {
        add(7, EatonGatewayType.LIGHTS, RoomName.LIVING_ROOM, SmartDeviceType.LIGHT);
        add(7, EatonGatewayType.BLINDS, RoomName.OFFICE, SmartDeviceType.BLINDS);
        add(8, EatonGatewayType.LIGHTS, RoomName.KITCHEN, SmartDeviceType.DIMMER);

        eatonDeviceConfigurationService.get(7, "blinds").as(StepVerifier::create)
            .expectNext(EatonConfigurationResponse.builder()
                .point(7)
                .room(RoomName.OFFICE)
                .type(SmartDeviceType.BLINDS)
                .build())
            .verifyComplete();
    }

    @Test
    void should_report_a_point_nothing_is_configured_for() {
        add(7, EatonGatewayType.LIGHTS, RoomName.LIVING_ROOM, SmartDeviceType.LIGHT);

        eatonDeviceConfigurationService.get(7, "blinds").as(StepVerifier::create)
            .verifyError(DeviceConfigurationNotFoundException.class);
    }

    @Test
    void should_refuse_a_second_configuration_of_a_point_on_its_gateway() {
        add(7, EatonGatewayType.LIGHTS, RoomName.LIVING_ROOM, SmartDeviceType.LIGHT);

        eatonDeviceConfigurationService.add(configuration(7, EatonGatewayType.LIGHTS, RoomName.OFFICE, SmartDeviceType.DIMMER))
            .as(StepVerifier::create)
            .verifyError(DeviceConfigurationExistsException.class);

        assertThat(row("SELECT count(*) AS configurations FROM eaton_devices")).containsEntry("configurations", 1L);
    }

    //two requests that race past the check of the service are stopped by the constraint of V7, and
    //the service knows it by its name in the message
    @Test
    void should_name_the_unique_constraint_when_the_database_refuses_a_second_configuration() {
        add(7, EatonGatewayType.LIGHTS, RoomName.LIVING_ROOM, SmartDeviceType.LIGHT);

        eatonDeviceConfigurationRepository.save(entity(7)).as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("eaton_devices_point_gateway_uq"));
    }

    //the range is checked at the API; the schema repeats it (V7) for whatever gets past
    @Test
    void should_refuse_a_point_outside_the_range_of_the_schema() {
        eatonDeviceConfigurationRepository.save(entity(0)).as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("eaton_devices_point_range"));
    }

    @Test
    void should_serve_a_configuration_and_the_code_of_a_missing_one_through_the_api() {
        add(7, EatonGatewayType.LIGHTS, RoomName.LIVING_ROOM, SmartDeviceType.LIGHT);

        webTestClient.get()
            .uri("/device/configuration/eaton?point=7&gateway=lights")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.point").isEqualTo(7)
            .jsonPath("$.room").isEqualTo("living room")
            .jsonPath("$.type").isEqualTo("light");

        //the code amx-service tells an unknown data point from a broken route by
        webTestClient.get()
            .uri("/device/configuration/eaton?point=8&gateway=lights")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("NOT_FOUND_DEVICE_CONFIGURATION");
    }

    private void add(final int point, final EatonGatewayType gateway, final RoomName room, final SmartDeviceType type) {
        eatonDeviceConfigurationService.add(configuration(point, gateway, room, type)).as(StepVerifier::create)
            .verifyComplete();
    }

    private static EatonDeviceConfiguration configuration(
        final int point,
        final EatonGatewayType gateway,
        final RoomName room,
        final SmartDeviceType type
    ) {
        return EatonDeviceConfiguration.builder().point(point).gateway(gateway).room(room).type(type).build();
    }

    private static EatonDeviceConfigurationEntity entity(final int point) {
        return EatonDeviceConfigurationEntity.builder()
            .createdAt(LocalDateTime.of(2026, 10, 11, 12, 0))
            .point(point)
            .room(RoomName.OFFICE)
            .type(SmartDeviceType.LIGHT)
            .gateway(EatonGatewayType.LIGHTS)
            .build();
    }
}
