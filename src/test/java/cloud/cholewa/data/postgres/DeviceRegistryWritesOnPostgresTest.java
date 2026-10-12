package cloud.cholewa.data.postgres;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

//The writes of the device registry on a real PostgreSQL, through the API and with the JSON a
//client sends: a device of every binding, the four ways a binding can be taken, a device the
//registry refuses, and what a replacement, a switch and a removal leave of a row. The unique
//indexes and the checks of V13 are what answers here - nothing of them is mocked.
class DeviceRegistryWritesOnPostgresTest extends OnPostgres {

    private static final String SHELLY_BLINDS = """
        {"name": "Office window", "room": "office", "kind": "blinds", "vendor": "shelly", "enabled": true,
         "shelly": {"address": "192.0.2.17", "generation": 2, "deviceId": "shellyplus2pm-a1b2c3d4e5f6", "channel": 0},
         "placement": "external", "positioning": true}""";

    private static final String EATON_LIGHT = """
        {"name": "Kitchen ceiling", "room": "kitchen", "kind": "light", "vendor": "eaton", "enabled": false,
         "eaton": {"gateway": "lights", "point": 44}, "lightKind": "dimmer"}""";

    private static final String BTHOME_WEATHER = """
        {"name": "Garden weather", "room": "garden", "kind": "weather station", "vendor": "other", "enabled": true,
         "bthome": {"address": "7c:c6:b6:00:00:01"}}""";

    @Autowired
    private WebTestClient webTestClient;

    //the rows hold the names of the constants, one binding each, and the id is the database's -
    //whatever id the payload carried
    @Test
    void should_register_a_device_of_every_binding_and_answer_it_with_the_id_it_got() {
        final long blinds = added(SHELLY_BLINDS);
        final long light = added(EATON_LIGHT);
        final long weather = added(BTHOME_WEATHER.replace("{\"name\"", "{\"id\": 999999, \"name\""));

        assertThat(weather).isNotEqualTo(999999L);
        assertThat(List.of(blinds, light, weather)).doesNotHaveDuplicates();

        assertThat(stored(blinds))
            .containsEntry("name", "Office window")
            .containsEntry("room", "OFFICE")
            .containsEntry("kind", "BLINDS")
            .containsEntry("vendor", "SHELLY")
            .containsEntry("enabled", true)
            .containsEntry("shelly_address", "192.0.2.17")
            .containsEntry("shelly_generation", 2)
            .containsEntry("shelly_device_id", "shellyplus2pm-a1b2c3d4e5f6")
            .containsEntry("shelly_channel", 0)
            .containsEntry("eaton_gateway", null)
            .containsEntry("bthome_address", null)
            .containsEntry("placement", "EXTERNAL")
            .containsEntry("positioning", true)
            .containsEntry("updated_at", null);
        assertThat(stored(blinds).get("created_at")).isNotNull();
        assertThat(stored(light))
            .containsEntry("kind", "LIGHT")
            .containsEntry("vendor", "EATON")
            .containsEntry("enabled", false)
            .containsEntry("eaton_gateway", "LIGHTS")
            .containsEntry("eaton_point", 44)
            .containsEntry("light_kind", "DIMMER")
            .containsEntry("shelly_address", null);
        assertThat(stored(weather))
            .containsEntry("kind", "WEATHER_STATION")
            .containsEntry("vendor", "OTHER")
            .containsEntry("bthome_address", "7c:c6:b6:00:00:01");

        //and what was answered is what a read answers
        webTestClient.get()
            .uri("/device/registry/{id}", blinds)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(blinds)
            .jsonPath("$.shelly.channel").isEqualTo(0)
            .jsonPath("$.placement").isEqualTo("external");
    }

    //the four unique indexes of V13, each answered by what was taken
    @Test
    void should_refuse_a_binding_that_is_taken_and_say_which_one() {
        added(SHELLY_BLINDS);
        added(EATON_LIGHT);
        added(BTHOME_WEATHER);

        //the same channel of the same Shelly, under another MQTT id
        conflict(SHELLY_BLINDS.replace("shellyplus2pm-a1b2c3d4e5f6", "shellyplus2pm-000000000000"),
            "A device is already registered for the Shelly at [192.0.2.17], channel [0]");
        //the same channel of the same MQTT id, under another address
        conflict(SHELLY_BLINDS.replace("192.0.2.17", "192.0.2.99"),
            "A device is already registered for the Shelly with the id [shellyplus2pm-a1b2c3d4e5f6], channel [0]");
        conflict(EATON_LIGHT.replace("Kitchen ceiling", "Kitchen spots"),
            "A device is already registered for the point [44] of the Eaton gateway [lights]");
        conflict("""
                {"name": "Garden gate", "room": "garden", "kind": "opening sensor", "vendor": "shelly", "enabled": true,
                 "bthome": {"address": "7c:c6:b6:00:00:01"}}""",
            "A device is already registered for the Bluetooth address [7c:c6:b6:00:00:01]");

        assertThat(count()).isEqualTo(3);
    }

    //a Shelly without a channel is one device as a whole; another channel of a Shelly, and the
    //same point on the other gateway, are other devices
    @Test
    void should_take_a_shelly_without_a_channel_once_and_what_only_looks_taken() {
        final String gateway = """
            {"name": "Hall gateway", "room": "hall down", "kind": "bthome gateway", "vendor": "shelly", "enabled": true,
             "shelly": {"address": "192.0.2.30", "generation": 3, "deviceId": "shellyblugwg3-aabbccddeeff"}}""";
        added(gateway);
        added(SHELLY_BLINDS);
        added(EATON_LIGHT);

        conflict(gateway.replace("shellyblugwg3-aabbccddeeff", "shellyblugwg3-000000000000"),
            "A device is already registered for the Shelly at [192.0.2.30]");

        added(SHELLY_BLINDS.replace("\"channel\": 0", "\"channel\": 1"));
        added(EATON_LIGHT.replace("\"lights\"", "\"blinds\""));

        assertThat(count()).isEqualTo(5);
    }

    //what the model lets through and the registry does not: 400 with the code of an invalid
    //device, worded by the rule - never by the name of a check of the schema
    @Test
    void should_refuse_a_device_whose_binding_does_not_fit_and_store_nothing() {
        invalid(EATON_LIGHT.replace("\"vendor\": \"eaton\"", "\"vendor\": \"shelly\""),
            "A device of the vendor [shelly] is not reached through the binding [eaton]");
        invalid(BTHOME_WEATHER.replace("\"bthome\": {\"address\": \"7c:c6:b6:00:00:01\"}",
                "\"eaton\": {\"gateway\": \"lights\", \"point\": 7}").replace("\"other\"", "\"eaton\""),
            "A device of the kind [weather station] is not reached through the binding [eaton], only through: bthome");
        invalid(SHELLY_BLINDS.replace("\"placement\"", "\"lightKind\": \"relay\", \"placement\""),
            "lightKind describes a light, not a device of the kind [blinds]");
        invalid(EATON_LIGHT.replace("\"lightKind\": \"dimmer\"", "\"positioning\": false"),
            "placement and positioning describe blinds, not a device of the kind [light]");
        invalid("""
                {"name": "Nowhere", "room": "office", "kind": "light", "vendor": "shelly", "enabled": true}""",
            "A device is reached through exactly one binding - shelly, eaton or bthome - and this one has none");
        invalid(SHELLY_BLINDS.replace("\"placement\"", "\"bthome\": {\"address\": \"7c:c6:b6:00:00:02\"}, \"placement\""),
            "A device is reached through exactly one binding - shelly, eaton or bthome - and this one has shelly and bthome");

        assertThat(count()).isZero();
    }

    //What the model itself refuses is a bad request before the registry is asked: no code - and
    //not the answer of the schema either, which would be the same 400 without a code had a
    //bound of the model gone missing
    @Test
    void should_refuse_what_the_model_of_the_sdk_refuses() {
        for (final String body : List.of(
            EATON_LIGHT.replace("Kitchen ceiling", "K"),
            EATON_LIGHT.replace("\"enabled\": false,", ""),
            EATON_LIGHT.replace("\"point\": 44", "\"point\": 100"),
            SHELLY_BLINDS.replace("192.0.2.17", "192.0.2.300"),
            SHELLY_BLINDS.replace("\"generation\": 2, ", ""),
            SHELLY_BLINDS.replace("shellyplus2pm-a1b2c3d4e5f6", "shelly.plus/2pm"),
            SHELLY_BLINDS.replace("\"channel\": 0", "\"channel\": 4"),
            BTHOME_WEATHER.replace("7c:c6:b6:00:00:01", "7C:C6:B6:00:00:01"),
            BTHOME_WEATHER.replace("weather station", "thermostat")
        )) {
            webTestClient.post()
                .uri("/device/registry")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").doesNotExist()
                .jsonPath("$.errors[0].message").value(message ->
                    assertThat(message).isIn("Missing or invalid parameter", "Malformed request body"));
        }

        assertThat(count()).isZero();
    }

    //PUT is the whole device: an Eaton light becomes a Shelly one in another room, switched on,
    //and nothing of the Eaton binding or of the way it was driven stays. The row keeps its id
    //and the time it was registered
    @Test
    void should_replace_a_device_whole_and_keep_its_id_and_the_time_it_was_registered() {
        final long id = added(EATON_LIGHT);
        final Map<String, Object> before = stored(id);

        webTestClient.put()
            .uri("/device/registry/{id}", id)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"id": 999999, "name": "Kitchen spots", "room": "living room", "kind": "light", "vendor": "shelly",
                 "enabled": true, "shelly": {"address": "192.0.2.18", "generation": 1}}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(id)
            .jsonPath("$.name").isEqualTo("Kitchen spots")
            .jsonPath("$.room").isEqualTo("living room")
            .jsonPath("$.enabled").isEqualTo(true)
            .jsonPath("$.shelly.address").isEqualTo("192.0.2.18")
            .jsonPath("$.eaton").doesNotExist()
            .jsonPath("$.lightKind").doesNotExist();

        final Map<String, Object> after = stored(id);
        assertThat(count()).isEqualTo(1);
        assertThat(after)
            .containsEntry("created_at", before.get("created_at"))
            .containsEntry("name", "Kitchen spots")
            .containsEntry("room", "LIVING_ROOM")
            .containsEntry("kind", "LIGHT")
            .containsEntry("vendor", "SHELLY")
            .containsEntry("enabled", true)
            .containsEntry("shelly_address", "192.0.2.18")
            .containsEntry("shelly_generation", 1)
            .containsEntry("shelly_device_id", null)
            .containsEntry("shelly_channel", null)
            .containsEntry("eaton_gateway", null)
            .containsEntry("eaton_point", null)
            .containsEntry("light_kind", null);
        assertThat(after.get("updated_at")).isNotNull();
    }

    //a device replaced by itself with another name: its own binding is not "taken"
    @Test
    void should_replace_a_device_that_keeps_its_binding() {
        final long id = added(SHELLY_BLINDS);

        webTestClient.put()
            .uri("/device/registry/{id}", id)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(SHELLY_BLINDS.replace("Office window", "Office door"))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("Office door");
    }

    @Test
    void should_refuse_a_replacement_of_another_kind_one_the_rules_refuse_and_one_onto_a_taken_binding() {
        final long light = added(EATON_LIGHT);
        added(SHELLY_BLINDS);
        final Map<String, Object> before = stored(light);

        replaced(light, SHELLY_BLINDS.replace("192.0.2.17", "192.0.2.50").replace("a1b2c3d4e5f6", "000000000000"))
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("INVALID_DEVICE")
            .jsonPath("$.errors[0].details").isEqualTo(
                "The kind of a device cannot be changed: device " + light + " is of the kind [light]."
                    + " Remove it and register the new one");

        replaced(light, EATON_LIGHT.replace("\"lightKind\": \"dimmer\"", "\"placement\": \"internal\""))
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("INVALID_DEVICE");

        //the channel of the Shelly the blinds hang on
        replaced(light, """
            {"name": "Kitchen ceiling", "room": "kitchen", "kind": "light", "vendor": "shelly", "enabled": false,
             "shelly": {"address": "192.0.2.17", "generation": 2, "channel": 0}}""")
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("DEVICE_CONFLICT")
            .jsonPath("$.errors[0].details").isEqualTo(
                "A device is already registered for the Shelly at [192.0.2.17], channel [0]");

        assertThat(stored(light)).isEqualTo(before);
    }

    @Test
    void should_switch_a_device_off_and_on_and_leave_the_rest_of_its_row() {
        final long id = added(SHELLY_BLINDS);
        final Map<String, Object> before = stored(id);

        webTestClient.post()
            .uri("/device/registry/{id}/disable", id)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(id)
            .jsonPath("$.enabled").isEqualTo(false)
            .jsonPath("$.shelly.deviceId").isEqualTo("shellyplus2pm-a1b2c3d4e5f6");

        final Map<String, Object> off = stored(id);
        assertThat(off).containsEntry("enabled", false);
        assertThat(off.get("updated_at")).isNotNull();
        assertThat(withoutTheSwitch(off)).isEqualTo(withoutTheSwitch(before));

        webTestClient.post()
            .uri("/device/registry/{id}/enable", id)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.enabled").isEqualTo(true);

        assertThat(stored(id)).containsEntry("enabled", true);
        assertThat(withoutTheSwitch(stored(id))).isEqualTo(withoutTheSwitch(before));
    }

    @Test
    void should_remove_a_device_and_report_one_that_is_not_there_to_every_write() {
        final long id = added(SHELLY_BLINDS);
        final long other = added(EATON_LIGHT);

        webTestClient.delete()
            .uri("/device/registry/{id}", id)
            .exchange()
            .expectStatus().isNoContent()
            .expectBody().isEmpty();

        assertThat(rows("SELECT id FROM devices")).extracting(device -> ((Number) device.get("id")).longValue())
            .containsExactly(other);

        webTestClient.delete().uri("/device/registry/{id}", id).exchange()
            .expectStatus().isNotFound()
            .expectBody().jsonPath("$.errors[0].code").isEqualTo("NOT_FOUND_DEVICE");
        webTestClient.post().uri("/device/registry/{id}/enable", id).exchange()
            .expectStatus().isNotFound()
            .expectBody().jsonPath("$.errors[0].code").isEqualTo("NOT_FOUND_DEVICE");
        webTestClient.post().uri("/device/registry/{id}/disable", id).exchange()
            .expectStatus().isNotFound()
            .expectBody().jsonPath("$.errors[0].code").isEqualTo("NOT_FOUND_DEVICE");
        replaced(id, SHELLY_BLINDS)
            .expectStatus().isNotFound()
            .expectBody().jsonPath("$.errors[0].code").isEqualTo("NOT_FOUND_DEVICE");

        //the binding of a removed device is free again
        added(SHELLY_BLINDS);
    }

    //One table: what the registry writes is what amx-service reads for a datagram. A room
    //corrected here is the room of the next reading; a device removed here is a data point
    //nothing is configured for; and the two APIs refuse each other's points
    @Test
    void should_change_what_the_eaton_configuration_answers_for_the_point_of_a_device() {
        final String sensor = """
            {"name": "Office temperature", "room": "office", "kind": "temperature sensor", "vendor": "eaton",
             "enabled": true, "eaton": {"gateway": "blinds", "point": 47}}""";
        final long id = added(sensor);

        webTestClient.get().uri("/device/configuration/eaton?point=47&gateway=blinds").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.room").isEqualTo("office")
            .jsonPath("$.type").isEqualTo("temperature sensor");

        replaced(id, sensor.replace("\"room\": \"office\"", "\"room\": \"loft\"")).expectStatus().isOk();

        webTestClient.get().uri("/device/configuration/eaton?point=47&gateway=blinds").exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.room").isEqualTo("loft");

        //the point is the registry's: the configuration API cannot register it a second time
        webTestClient.post().uri("/device/configuration/eaton")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"point": 47, "gateway": "blinds", "room": "garage", "type": "temperature sensor"}""")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.errors[0].code").isEqualTo("CONFIGURATION_EXIST");

        //and a point the configuration API registered is taken for the registry
        webTestClient.post().uri("/device/configuration/eaton")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"point": 48, "gateway": "blinds", "room": "tobi", "type": "temperature sensor"}""")
            .exchange()
            .expectStatus().isCreated();
        conflict(sensor.replace("47", "48"),
            "A device is already registered for the point [48] of the Eaton gateway [blinds]");

        webTestClient.delete().uri("/device/registry/{id}", id).exchange().expectStatus().isNoContent();

        webTestClient.get().uri("/device/configuration/eaton?point=47&gateway=blinds").exchange()
            .expectStatus().isNotFound()
            .expectBody().jsonPath("$.errors[0].code").isEqualTo("NOT_FOUND_DEVICE_CONFIGURATION");
    }

    //the id the registry answered with
    private long added(final String body) {
        final Map<?, ?> device = webTestClient.post()
            .uri("/device/registry")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Map.class)
            .returnResult()
            .getResponseBody();

        assertThat(device).isNotNull();
        return ((Number) device.get("id")).longValue();
    }

    private WebTestClient.ResponseSpec replaced(final long id, final String body) {
        return webTestClient.put()
            .uri("/device/registry/{id}", id)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange();
    }

    private void conflict(final String body, final String details) {
        webTestClient.post()
            .uri("/device/registry")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("DEVICE_CONFLICT")
            .jsonPath("$.errors[0].message").isEqualTo("Device conflict")
            .jsonPath("$.errors[0].details").isEqualTo(details);
    }

    private void invalid(final String body, final String details) {
        webTestClient.post()
            .uri("/device/registry")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("INVALID_DEVICE")
            .jsonPath("$.errors[0].message").isEqualTo("Invalid device")
            .jsonPath("$.errors[0].details").isEqualTo(details);
    }

    private Map<String, Object> stored(final long id) {
        return row("SELECT * FROM devices WHERE id = " + id);
    }

    private long count() {
        return (Long) row("SELECT count(*) AS devices FROM devices").get("devices");
    }

    private static Map<String, Object> withoutTheSwitch(final Map<String, Object> device) {
        final Map<String, Object> rest = new HashMap<>(device);
        rest.remove("enabled");
        rest.remove("updated_at");
        return rest;
    }
}
