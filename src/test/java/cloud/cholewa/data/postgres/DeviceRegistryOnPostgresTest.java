package cloud.cholewa.data.postgres;

import cloud.cholewa.data.device.registry.service.DeviceRegistryService;
import cloud.cholewa.data.error.DeviceNotFoundException;
import cloud.cholewa.home.model.BlindsPlacement;
import cloud.cholewa.home.model.BthomeBinding;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

//The device registry on a real PostgreSQL: the reads every device service starts from, and the
//table they read (V13). The service of this task only reads, so the rows are written here with
//plain SQL - which is also what shows that a row written by hand, or by the writes to come, is
//read the way the model promises.
class DeviceRegistryOnPostgresTest extends OnPostgres {

    private static final String SHELLY_BLINDS = """
        INSERT INTO devices (created_at, name, room, kind, vendor, enabled, shelly_address, shelly_generation,
                             shelly_device_id, shelly_channel, placement, positioning)
        VALUES (now(), 'Office window', 'OFFICE', 'BLINDS', 'SHELLY', TRUE, '192.0.2.17', 2,
                'shellyplus2pm-a1b2c3d4e5f6', 0, 'EXTERNAL', TRUE)""";

    private static final String EATON_LIGHT = """
        INSERT INTO devices (created_at, name, room, kind, vendor, enabled, eaton_gateway, eaton_point, light_kind)
        VALUES (now(), 'Kitchen ceiling', 'KITCHEN', 'LIGHT', 'EATON', FALSE, 'LIGHTS', 44, 'DIMMER')""";

    private static final String BTHOME_WEATHER = """
        INSERT INTO devices (created_at, name, room, kind, vendor, enabled, bthome_address)
        VALUES (now(), 'Garden weather', 'GARDEN', 'WEATHER_STATION', 'SHELLY', TRUE, '7c:c6:b6:00:00:01')""";

    @Autowired
    private DeviceRegistryService deviceRegistryService;

    @Autowired
    private WebTestClient webTestClient;

    //the rows hold the names of the constants; the model comes out whole, with the one binding
    //the row has and the id the database gave it
    @Test
    void should_read_a_device_of_every_binding_from_its_row() {
        execute(SHELLY_BLINDS);
        execute(EATON_LIGHT);
        execute(BTHOME_WEATHER);

        //read outside a reactive chain: the ids are looked up with blocking statements
        final List<SmartDevice> devices = deviceRegistryService.find(null, null, null).block(PATIENCE);

        assertThat(devices).containsExactly(
            SmartDevice.builder()
                .id(idOf("Garden weather"))
                .name("Garden weather")
                .room(RoomName.GARDEN)
                .kind(DeviceKind.WEATHER_STATION)
                .vendor(SmartVendor.SHELLY)
                .enabled(true)
                .bthome(BthomeBinding.builder().address("7c:c6:b6:00:00:01").build())
                .build(),
            SmartDevice.builder()
                .id(idOf("Kitchen ceiling"))
                .name("Kitchen ceiling")
                .room(RoomName.KITCHEN)
                .kind(DeviceKind.LIGHT)
                .vendor(SmartVendor.EATON)
                .enabled(false)
                .eaton(EatonBinding.builder().gateway(EatonGatewayType.LIGHTS).point(44).build())
                .lightKind(LightKind.DIMMER)
                .build(),
            SmartDevice.builder()
                .id(idOf("Office window"))
                .name("Office window")
                .room(RoomName.OFFICE)
                .kind(DeviceKind.BLINDS)
                .vendor(SmartVendor.SHELLY)
                .enabled(true)
                .shelly(ShellyBinding.builder()
                    .address("192.0.2.17")
                    .generation(2)
                    .deviceId("shellyplus2pm-a1b2c3d4e5f6")
                    .channel(0)
                    .build())
                .placement(BlindsPlacement.EXTERNAL)
                .positioning(true)
                .build()
        );
    }

    //each filter alone and all of them together: the criteria are built from what was asked for,
    //and an enum in them has to reach the database as the name of its constant
    @Test
    void should_filter_by_kind_room_and_enabled() {
        execute(SHELLY_BLINDS);
        execute(EATON_LIGHT);
        execute(BTHOME_WEATHER);

        assertThat(namesOf("light", null, null)).containsExactly("Kitchen ceiling");
        assertThat(namesOf(null, "office", null)).containsExactly("Office window");
        assertThat(namesOf(null, null, false)).containsExactly("Kitchen ceiling");
        assertThat(namesOf(null, null, true)).containsExactly("Garden weather", "Office window");
        assertThat(namesOf("weather station", "garden", true)).containsExactly("Garden weather");
        assertThat(namesOf("blinds", "garden", null)).isEmpty();
    }

    @Test
    void should_answer_an_empty_list_from_an_empty_registry() {
        assertThat(namesOf(null, null, null)).isEmpty();
    }

    @Test
    void should_find_a_device_by_its_id_and_report_one_that_is_not_there() {
        execute(EATON_LIGHT);
        final long id = idOf("Kitchen ceiling");

        deviceRegistryService.get(id).as(StepVerifier::create)
            .assertNext(device -> assertThat(device.getEaton().getPoint()).isEqualTo(44))
            .verifyComplete();

        deviceRegistryService.get(id + 1).as(StepVerifier::create)
            .verifyError(DeviceNotFoundException.class);
    }

    @Test
    void should_serve_the_registry_and_the_code_of_a_missing_device_through_the_api() {
        execute(SHELLY_BLINDS);
        execute(EATON_LIGHT);

        webTestClient.get()
            .uri(uri -> uri.path("/device/registry").queryParam("kind", "blinds").build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].name").isEqualTo("Office window")
            .jsonPath("$[0].room").isEqualTo("office")
            .jsonPath("$[0].kind").isEqualTo("blinds")
            .jsonPath("$[0].shelly.deviceId").isEqualTo("shellyplus2pm-a1b2c3d4e5f6")
            .jsonPath("$[0].placement").isEqualTo("external")
            .jsonPath("$[0].eaton").doesNotExist()
            .jsonPath("$[0].bthome").doesNotExist();

        webTestClient.get()
            .uri("/device/registry/{id}", idOf("Kitchen ceiling") + 100)
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("NOT_FOUND_DEVICE");

        webTestClient.get()
            .uri("/device/registry?room=attic")
            .exchange()
            .expectStatus().isBadRequest();
    }

    //--- the table: what V13 lets in. The writes of the registry (HAS-223) will meet these as
    //--- errors of the database; here they are named once, by the constraint

    @Test
    void should_refuse_a_device_without_a_binding_and_one_with_two() {
        refused("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled)
            VALUES (now(), 'Nowhere', 'OFFICE', 'LIGHT', 'SHELLY', TRUE)""", "devices_one_binding");

        refused("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, shelly_address, shelly_generation,
                                 bthome_address)
            VALUES (now(), 'Both', 'OFFICE', 'LIGHT', 'SHELLY', TRUE, '192.0.2.17', 2, '7c:c6:b6:00:00:01')""",
            "devices_one_binding");
    }

    @Test
    void should_refuse_half_a_binding() {
        refused("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, shelly_address)
            VALUES (now(), 'No generation', 'OFFICE', 'LIGHT', 'SHELLY', TRUE, '192.0.2.17')""",
            "devices_shelly_binding_whole");

        refused("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, eaton_gateway, eaton_point,
                                 shelly_channel)
            VALUES (now(), 'A channel of nothing', 'OFFICE', 'LIGHT', 'EATON', TRUE, 'LIGHTS', 7, 1)""",
            "devices_shelly_binding_whole");

        refused("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, eaton_gateway)
            VALUES (now(), 'No point', 'OFFICE', 'LIGHT', 'EATON', TRUE, 'LIGHTS')""",
            "devices_eaton_binding_whole");
    }

    @Test
    void should_refuse_what_the_models_of_the_sdk_would_refuse() {
        refused(shelly("'192.0.2.256'", "2", "NULL", "NULL"), "devices_shelly_address_format");
        refused(shelly("'192.0.2.17'", "5", "NULL", "NULL"), "devices_shelly_generation_range");
        refused(shelly("'192.0.2.17'", "2", "'shelly.plus2pm'", "NULL"), "devices_shelly_device_id_format");
        refused(shelly("'192.0.2.17'", "2", "NULL", "4"), "devices_shelly_channel_range");

        refused("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, eaton_gateway, eaton_point)
            VALUES (now(), 'Point 100', 'OFFICE', 'LIGHT', 'EATON', TRUE, 'LIGHTS', 100)""",
            "devices_eaton_point_range");

        refused("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, bthome_address)
            VALUES (now(), 'Upper case', 'OFFICE', 'OPENING_SENSOR', 'SHELLY', TRUE, '7C:C6:B6:00:00:01')""",
            "devices_bthome_address_format");

        refused("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, eaton_gateway, eaton_point)
            VALUES (now(), 'ab', 'OFFICE', 'LIGHT', 'EATON', TRUE, 'LIGHTS', 7)""", "devices_name_length");
    }

    //what the schema can say of "the binding fits the vendor and the kind" without other rows
    @Test
    void should_refuse_an_eaton_device_on_another_binding_and_what_belongs_to_another_kind() {
        refused("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, shelly_address, shelly_generation)
            VALUES (now(), 'Eaton on a Shelly', 'OFFICE', 'LIGHT', 'EATON', TRUE, '192.0.2.17', 2)""",
            "devices_eaton_vendor_binding");

        refused("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, eaton_gateway, eaton_point)
            VALUES (now(), 'Shelly on a point', 'OFFICE', 'LIGHT', 'SHELLY', TRUE, 'LIGHTS', 7)""",
            "devices_eaton_vendor_binding");

        refused("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, eaton_gateway, eaton_point,
                                 light_kind)
            VALUES (now(), 'Dimmed blinds', 'OFFICE', 'BLINDS', 'EATON', TRUE, 'BLINDS', 7, 'DIMMER')""",
            "devices_light_kind_of_a_light");

        refused("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, eaton_gateway, eaton_point,
                                 placement)
            VALUES (now(), 'A light outside', 'OFFICE', 'LIGHT', 'EATON', TRUE, 'LIGHTS', 7, 'EXTERNAL')""",
            "devices_placement_of_blinds");

        refused("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, eaton_gateway, eaton_point,
                                 positioning)
            VALUES (now(), 'A light at half', 'OFFICE', 'LIGHT', 'EATON', TRUE, 'LIGHTS', 7, FALSE)""",
            "devices_placement_of_blinds");
    }

    //a relay with several outputs is a device per channel - the address alone is not unique
    @Test
    void should_take_every_channel_of_one_shelly_once() {
        execute(shelly("'192.0.2.17'", "2", "'shellypro4pm-a1b2c3d4e5f6'", "0"));
        execute(shelly("'192.0.2.17'", "2", "'shellypro4pm-a1b2c3d4e5f6'", "1"));

        duplicate(shelly("'192.0.2.17'", "2", "'shellypro4pm-a1b2c3d4e5f6'", "1"), "devices_shelly_address_channel_uq");

        assertThat(row("SELECT count(*) AS devices FROM devices")).containsEntry("devices", 2L);
    }

    //The id a Shelly reports over MQTT is what a service finds the device of an event by: the
    //same id and channel under a second address would be two devices for one event
    @Test
    void should_take_a_channel_of_one_mqtt_id_once_whatever_the_address() {
        execute(shelly("'192.0.2.17'", "2", "'shellypro4pm-a1b2c3d4e5f6'", "0"));

        duplicate(shelly("'192.0.2.99'", "2", "'shellypro4pm-a1b2c3d4e5f6'", "0"), "devices_shelly_device_id_channel_uq");
    }

    //Gen1 devices report nothing over MQTT and have no id: any number of them may be without one
    @Test
    void should_take_any_number_of_shellies_without_an_mqtt_id() {
        execute(shelly("'192.0.2.20'", "1", "NULL", "NULL"));
        execute(shelly("'192.0.2.21'", "1", "NULL", "NULL"));

        assertThat(row("SELECT count(*) AS devices FROM devices")).containsEntry("devices", 2L);
    }

    //A Shelly without a channel is unique by its address alone. A plain UNIQUE (address, channel)
    //would not see it - two NULLs are never equal - and the same plug could be registered twice
    @Test
    void should_take_a_shelly_without_a_channel_once() {
        execute(shelly("'192.0.2.18'", "1", "NULL", "NULL"));

        duplicate(shelly("'192.0.2.18'", "1", "NULL", "NULL"), "devices_shelly_address_channel_uq");
    }

    //the device that is a gateway and one of its own channels are two devices of the registry
    @Test
    void should_take_a_shelly_without_a_channel_next_to_a_channel_of_the_same_shelly() {
        execute(shelly("'192.0.2.19'", "3", "'shelly1minig3-a1b2c3d4e5f6'", "NULL"));
        execute(shelly("'192.0.2.19'", "3", "'shelly1minig3-a1b2c3d4e5f6'", "0"));

        assertThat(row("SELECT count(*) AS devices FROM devices")).containsEntry("devices", 2L);
    }

    @Test
    void should_take_an_eaton_point_of_a_gateway_once_and_the_same_point_of_the_other_gateway() {
        execute(EATON_LIGHT);
        execute("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, eaton_gateway, eaton_point)
            VALUES (now(), 'Kitchen blinds', 'KITCHEN', 'BLINDS', 'EATON', TRUE, 'BLINDS', 44)""");

        duplicate("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, eaton_gateway, eaton_point)
            VALUES (now(), 'Second light', 'OFFICE', 'LIGHT', 'EATON', TRUE, 'LIGHTS', 44)""",
            "devices_eaton_gateway_point_uq");
    }

    //A Bluetooth sensor is one device, whichever gateway hears it: the row names no gateway, and
    //the address is all that tells two sensors apart - in another room, of another kind, it is
    //still the same sensor
    @Test
    void should_take_a_bluetooth_sensor_once() {
        execute(BTHOME_WEATHER);

        duplicate("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, bthome_address)
            VALUES (now(), 'The same sensor', 'OFFICE', 'OPENING_SENSOR', 'SHELLY', TRUE, '7c:c6:b6:00:00:01')""",
            "devices_bthome_address_uq");
    }

    private List<String> namesOf(final String kind, final String room, final Boolean enabled) {
        return deviceRegistryService.find(kind, room, enabled).block(PATIENCE).stream().map(SmartDevice::getName).toList();
    }

    private long idOf(final String name) {
        return ((Number) row("SELECT id FROM devices WHERE name = '" + name + "'").get("id")).longValue();
    }

    private void refused(final String insert, final String constraint) {
        databaseClient.sql(insert).then().as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint));
    }

    private void duplicate(final String insert, final String constraint) {
        databaseClient.sql(insert).then().as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining(constraint));
    }

    private static String shelly(final String address, final String generation, final String deviceId, final String channel) {
        return """
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, shelly_address, shelly_generation,
                                 shelly_device_id, shelly_channel)
            VALUES (now(), 'A Shelly', 'OFFICE', 'LIGHT', 'SHELLY', TRUE, %s, %s, %s, %s)"""
            .formatted(address, generation, deviceId, channel);
    }
}
