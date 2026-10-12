package cloud.cholewa.data.postgres;

import cloud.cholewa.data.device.eaton.service.EatonDeviceConfigurationService;
import cloud.cholewa.data.device.registry.service.DeviceRegistryService;
import cloud.cholewa.data.error.DeviceConfigurationExistsException;
import cloud.cholewa.data.error.DeviceConfigurationNotFoundException;
import cloud.cholewa.home.model.DeviceKind;
import cloud.cholewa.home.model.EatonBinding;
import cloud.cholewa.home.model.EatonConfigurationResponse;
import cloud.cholewa.home.model.EatonDeviceConfiguration;
import cloud.cholewa.home.model.EatonGatewayType;
import cloud.cholewa.home.model.LightKind;
import cloud.cholewa.home.model.RoomName;
import cloud.cholewa.home.model.SmartDevice;
import cloud.cholewa.home.model.SmartDeviceType;
import cloud.cholewa.home.model.SmartVendor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

//The Eaton configuration on a real PostgreSQL - the lookup amx-service makes for every datagram.
//Since V13 a configuration is a row of the device registry; the API of the configuration is the
//one it always was, and these tests are what says so.
class EatonConfigurationOnPostgresTest extends OnPostgres {

    @Autowired
    private EatonDeviceConfigurationService eatonDeviceConfigurationService;

    @Autowired
    private DeviceRegistryService deviceRegistryService;

    @Autowired
    private WebTestClient webTestClient;

    //a configuration is a device of the registry: an Eaton one, switched on, with a name of its own
    @Test
    void should_store_a_configuration_as_a_device_with_its_enums_as_the_names_of_their_constants() {
        add(7, EatonGatewayType.LIGHTS, RoomName.LIVING_ROOM, SmartDeviceType.TEMPERATURE_SENSOR);

        assertThat(row("""
            SELECT name, room, kind, vendor, enabled, eaton_gateway, eaton_point, light_kind,
                   created_at IS NOT NULL AS created FROM devices"""))
            .containsEntry("name", "Living room temperature sensor 7")
            .containsEntry("room", "LIVING_ROOM")
            .containsEntry("kind", "TEMPERATURE_SENSOR")
            .containsEntry("vendor", "EATON")
            .containsEntry("enabled", true)
            .containsEntry("eaton_gateway", "LIGHTS")
            .containsEntry("light_kind", null)
            .containsEntry("created", true)
            .satisfies(stored -> assertThat(((Number) stored.get("eaton_point")).intValue()).isEqualTo(7));

        //and nothing is written to the table the configurations used to live in - which nothing
        //here empties any more either
        assertThat(row("SELECT count(*) AS configurations FROM eaton_devices")).containsEntry("configurations", 0L);
    }

    //the two types of a light are one kind of the registry, told apart by how the light is driven
    @Test
    void should_store_a_dimmer_as_a_light_that_dims_and_answer_it_as_a_dimmer() {
        add(8, EatonGatewayType.LIGHTS, RoomName.KITCHEN, SmartDeviceType.DIMMER);
        add(9, EatonGatewayType.LIGHTS, RoomName.KITCHEN, SmartDeviceType.LIGHT);

        assertThat(rows("SELECT kind, light_kind FROM devices ORDER BY eaton_point"))
            .extracting(device -> device.get("kind") + "/" + device.get("light_kind"))
            .containsExactly("LIGHT/DIMMER", "LIGHT/RELAY");

        eatonDeviceConfigurationService.get(8, "lights").as(StepVerifier::create)
            .assertNext(configuration -> assertThat(configuration.getType()).isEqualTo(SmartDeviceType.DIMMER))
            .verifyComplete();
        eatonDeviceConfigurationService.get(9, "lights").as(StepVerifier::create)
            .assertNext(configuration -> assertThat(configuration.getType()).isEqualTo(SmartDeviceType.LIGHT))
            .verifyComplete();
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

    //one table: what the configuration API registers, the registry serves - and the other way round
    @Test
    void should_serve_a_configuration_through_the_registry_and_a_device_of_the_registry_as_a_configuration() {
        add(7, EatonGatewayType.LIGHTS, RoomName.KITCHEN, SmartDeviceType.DIMMER);
        execute("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, eaton_gateway, eaton_point,
                                 placement, positioning)
            VALUES (now(), 'Office window', 'OFFICE', 'BLINDS', 'EATON', FALSE, 'BLINDS', 44, 'EXTERNAL', FALSE)""");

        final List<SmartDevice> devices = deviceRegistryService.find(null, "kitchen", null).block(PATIENCE);

        assertThat(devices).singleElement().satisfies(device -> {
            assertThat(device.getName()).isEqualTo("Kitchen light 7");
            assertThat(device.getKind()).isEqualTo(DeviceKind.LIGHT);
            assertThat(device.getLightKind()).isEqualTo(LightKind.DIMMER);
            assertThat(device.getVendor()).isEqualTo(SmartVendor.EATON);
            assertThat(device.getEnabled()).isTrue();
            assertThat(device.getEaton())
                .isEqualTo(EatonBinding.builder().gateway(EatonGatewayType.LIGHTS).point(7).build());
        });

        //a device registered by its name and switched off is found by its point all the same:
        //amx-service has to read the datagram of a device whatever the services do with it
        eatonDeviceConfigurationService.get(44, "blinds").as(StepVerifier::create)
            .expectNext(EatonConfigurationResponse.builder()
                .point(44)
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

        assertThat(row("SELECT count(*) AS devices FROM devices")).containsEntry("devices", 1L);
    }

    @Test
    void should_serve_a_configuration_and_the_code_of_a_missing_one_through_the_api() {
        webTestClient.post()
            .uri("/device/configuration/eaton")
            .bodyValue(configuration(7, EatonGatewayType.LIGHTS, RoomName.LIVING_ROOM, SmartDeviceType.LIGHT))
            .exchange()
            .expectStatus().isCreated();

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

        //a second one for the point is the conflict it always was
        webTestClient.post()
            .uri("/device/configuration/eaton")
            .bodyValue(configuration(7, EatonGatewayType.LIGHTS, RoomName.OFFICE, SmartDeviceType.LIGHT))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("CONFIGURATION_EXIST");

        //and a type the registry has no kind for is a bad request
        webTestClient.post()
            .uri("/device/configuration/eaton")
            .bodyValue(configuration(9, EatonGatewayType.LIGHTS, RoomName.OFFICE, SmartDeviceType.OTHER))
            .exchange()
            .expectStatus().isBadRequest();
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
}
