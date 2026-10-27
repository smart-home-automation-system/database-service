package cloud.cholewa.data.postgres;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private static final String SETTINGS = """
        {"runningAboveWatts": 20, "idleBelowWatts": 3, "minRunMinutes": 5, "finishedAfterMinutes": 10,
         "reminderEveryMinutes": 30, "maxReminders": 0, "quietFrom": "22:00", "quietTo": "07:05"}""";

    private static final String PLUG = """
        {"name": "Dishwasher", "room": "kitchen", "kind": "appliance", "vendor": "shelly", "enabled": true,
         "shelly": {"address": "192.0.2.64", "generation": 1, "channel": 0}""";

    private static final String APPLIANCE = PLUG + ", \"appliance\": " + SETTINGS + "}";

    private static final String SOLAX = """
        {"name": "Inverter", "room": "boiler", "kind": "inverter", "vendor": "other", "enabled": true,
         "inverter": {"brand": "solax", "serialNumber": "H1234567890ABC", "address": "192.0.2.120",
                      "registrationNumber": "SABCDEFGHI", "batterySerialNumber": "B1234567890ABC"}}""";

    private static final String SOFAR = """
        {"name": "Inverter", "room": "garage", "kind": "inverter", "vendor": "other", "enabled": true,
         "inverter": {"brand": "sofar", "serialNumber": "S1234567890ABCDEF", "address": "192.0.2.121",
                      "loggerSerial": 1234567890}}""";

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
        invalid(SHELLY_BLINDS.replace("\"deviceId\": \"shellyplus2pm-a1b2c3d4e5f6\", ", ""),
            "A Shelly of generation 2 needs its deviceId - the id it reports under over MQTT");
        invalid("""
                {"name": "Nowhere", "room": "office", "kind": "light", "vendor": "shelly", "enabled": true}""",
            "A device is reached through exactly one binding - shelly, eaton, bthome or inverter - and this one has none");
        invalid(SHELLY_BLINDS.replace("\"placement\"", "\"bthome\": {\"address\": \"7c:c6:b6:00:00:02\"}, \"placement\""),
            "A device is reached through exactly one binding - shelly, eaton, bthome or inverter - and this one has shelly and bthome");
        //HAS-260: the model takes 0 to 299, the registry an output or the zone of a presence sensor
        invalid(SHELLY_BLINDS.replace("\"channel\": 0", "\"channel\": 4"),
            "The channel of a Shelly is an output (0 to 3) or the zone of a presence sensor (200 to 299), not 4");
        invalid(SHELLY_BLINDS.replace("\"channel\": 0", "\"channel\": 200"),
            "The channel 200 is a zone, which only a device of the kind [presence sensor] has, not one of the kind [blinds]");

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
            SHELLY_BLINDS.replace("\"channel\": 0", "\"channel\": 300"),
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
             "shelly": {"address": "192.0.2.17", "generation": 2, "deviceId": "shellyplus2pm-000000000000",
                        "channel": 0}}""")
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

    //HAS-290, V16: the sensors of the window of blinds as a BIGINT[] of their ids, in the order
    //sent; each one has to be a registered opening sensor, and one that blinds still name stays
    @Test
    void should_link_blinds_to_the_opening_sensors_of_their_window_and_keep_a_sensor_in_use() {
        final long left = added(sensor("Cinema window left", "7c:c6:b6:00:00:11"));
        final long right = added(sensor("Cinema window right", "7c:c6:b6:00:00:12"));
        final long light = added(EATON_LIGHT);
        final long blinds = added(SHELLY_BLINDS.replace("\"positioning\": true}",
            "\"positioning\": true, \"openingSensors\": [" + right + ", " + left + "]}"));

        assertThat(stored(blinds)).containsEntry("opening_sensors", right + "," + left);
        assertThat(stored(light)).containsEntry("opening_sensors", "");
        webTestClient.get().uri("/device/registry/{id}", blinds).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.openingSensors.length()").isEqualTo(2)
            .jsonPath("$.openingSensors[0]").isEqualTo(right);
        //blinds without a sensor answer without the list
        webTestClient.get().uri("/device/registry/{id}", light).exchange()
            .expectBody().jsonPath("$.openingSensors").doesNotExist();

        invalid(SHELLY_BLINDS.replace("192.0.2.17", "192.0.2.18").replace("a1b2c3d4e5f6", "a1b2c3d4e5f7")
                .replace("\"positioning\": true}", "\"positioning\": true, \"openingSensors\": [" + light + ", 999999]}"),
            "openingSensors name devices that are no registered opening sensor: [" + light + ", 999999]");

        webTestClient.delete().uri("/device/registry/{id}", left).exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("DEVICE_CONFLICT")
            .jsonPath("$.errors[0].details").isEqualTo("The device " + left + " is the opening sensor of the blinds "
                + blinds + " [Office window] - remove it from their openingSensors first");

        //a replacement without the list removes the link, and the sensor may go
        replaced(blinds, SHELLY_BLINDS).expectStatus().isOk();
        assertThat(stored(blinds)).containsEntry("opening_sensors", "");
        webTestClient.delete().uri("/device/registry/{id}", left).exchange().expectStatus().isNoContent();
    }

    //what gets past the API is still kept to blinds and free of a NULL by the checks of V16
    @Test
    void should_keep_opening_sensors_to_blinds_in_the_schema_too() {
        final long light = added(EATON_LIGHT);

        databaseClient.sql("UPDATE devices SET opening_sensors = '{1}' WHERE id = " + light).then()
            .as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error).hasMessageContaining("devices_opening_sensors_of_blinds"));
        final long blinds = added(SHELLY_BLINDS);
        databaseClient.sql("UPDATE devices SET opening_sensors = '{1,NULL}' WHERE id = " + blinds).then()
            .as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error).hasMessageContaining("devices_opening_sensors_no_null"));
    }

    private static String sensor(final String name, final String address) {
        return """
            {"name": "%s", "room": "cinema", "kind": "opening sensor", "vendor": "shelly", "enabled": true,
             "bthome": {"address": "%s"}}""".formatted(name, address);
    }

    //the id the registry answered with
    //An appliance (HAS-272): the Shelly plug it runs on with its eight settings, the quiet times a TIME
    //of the house and HH:mm again in what is answered
    @Test
    void should_register_an_appliance_with_its_settings_and_answer_them_as_they_were_sent() {
        final long plug = added(APPLIANCE);

        assertThat(stored(plug))
            .containsEntry("kind", "APPLIANCE")
            .containsEntry("shelly_generation", 1)
            .containsEntry("appliance_running_above_watts", 20)
            .containsEntry("appliance_idle_below_watts", 3)
            .containsEntry("appliance_min_run_minutes", 5)
            .containsEntry("appliance_finished_after_minutes", 10)
            .containsEntry("appliance_reminder_every_minutes", 30)
            .containsEntry("appliance_max_reminders", 0)
            .containsEntry("appliance_quiet_from", LocalTime.of(22, 0))
            .containsEntry("appliance_quiet_to", LocalTime.of(7, 5));

        webTestClient.get()
            .uri("/device/registry/{id}", plug)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.kind").isEqualTo("appliance")
            .jsonPath("$.appliance.runningAboveWatts").isEqualTo(20)
            .jsonPath("$.appliance.maxReminders").isEqualTo(0)
            .jsonPath("$.appliance.quietFrom").isEqualTo("22:00")
            .jsonPath("$.appliance.quietTo").isEqualTo("07:05");
        //and found by its kind, which is how appliance-service will read it
        webTestClient.get()
            .uri("/device/registry?kind=appliance")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].id").isEqualTo(plug);
    }

    //a replacement changes the settings whole; there is no half of them to keep
    @Test
    void should_replace_the_settings_of_an_appliance() {
        final long plug = added(APPLIANCE);

        replaced(plug, APPLIANCE.replace("\"maxReminders\": 0", "\"maxReminders\": 4").replace("\"07:05\"", "\"06:30\""))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.appliance.maxReminders").isEqualTo(4)
            .jsonPath("$.appliance.quietTo").isEqualTo("06:30");

        assertThat(stored(plug))
            .containsEntry("appliance_max_reminders", 4)
            .containsEntry("appliance_quiet_to", LocalTime.of(6, 30));
    }

    //refused in words by DeviceRules, before any CHECK of V18 answers with its name
    @Test
    void should_refuse_an_appliance_the_registry_does_not_take_and_say_why() {
        invalid(PLUG + "}", "A device of the kind [appliance] needs its appliance settings");
        invalid(APPLIANCE.replace("\"idleBelowWatts\": 3", "\"idleBelowWatts\": 20"),
            "idleBelowWatts (20) has to be below runningAboveWatts (20)");
        invalid(SHELLY_BLINDS.replace("\"positioning\": true}", "\"positioning\": true, \"appliance\": " + SETTINGS + "}"),
            "appliance describes an appliance, not a device of the kind [blinds]");

        assertThat(count()).isZero();
    }

    //what the model refuses on its own: a quiet time that is no HH:mm, a bound of a setting
    @Test
    void should_refuse_settings_the_model_does_not_take_without_a_bare_data_integrity_answer() {
        for (final String body : List.of(
            APPLIANCE.replace("\"22:00\"", "\"22:00:00\""),
            APPLIANCE.replace("\"22:00\"", "\"24:00\""),
            APPLIANCE.replace("\"runningAboveWatts\": 20", "\"runningAboveWatts\": 4"),
            APPLIANCE.replace("\"maxReminders\": 0", "\"maxReminders\": 11"))) {
            webTestClient.post()
                .uri("/device/registry")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].message").value(message -> assertThat(message).isNotEqualTo("Data integrity violation"));
        }

        assertThat(count()).isZero();
    }

    //the schema keeps the eight together and to appliances, for a row written past the API
    @Test
    void should_refuse_half_of_the_settings_and_settings_on_another_kind_in_the_schema() {
        assertThatThrownBy(() -> execute("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, shelly_address, shelly_generation,
                appliance_running_above_watts)
            VALUES (now(), 'Dishwasher', 'KITCHEN', 'APPLIANCE', 'SHELLY', true, '192.0.2.64', 1, 20)"""))
            .hasMessageContaining("devices_appliance_of_appliances");
        assertThatThrownBy(() -> execute("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, shelly_address, shelly_generation,
                appliance_running_above_watts, appliance_idle_below_watts, appliance_min_run_minutes,
                appliance_finished_after_minutes, appliance_reminder_every_minutes, appliance_max_reminders,
                appliance_quiet_from, appliance_quiet_to)
            VALUES (now(), 'Kitchen lamp', 'KITCHEN', 'LIGHT', 'SHELLY', true, '192.0.2.65', 1,
                20, 3, 5, 10, 30, 4, '22:00', '07:00')"""))
            .hasMessageContaining("devices_appliance_of_appliances");
        assertThatThrownBy(() -> execute("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, shelly_address, shelly_generation,
                appliance_running_above_watts, appliance_idle_below_watts, appliance_min_run_minutes,
                appliance_finished_after_minutes, appliance_reminder_every_minutes, appliance_max_reminders,
                appliance_quiet_from, appliance_quiet_to)
            VALUES (now(), 'Dishwasher', 'KITCHEN', 'APPLIANCE', 'SHELLY', true, '192.0.2.64', 1,
                20, 30, 5, 10, 30, 4, '22:00', '07:00')"""))
            .hasMessageContaining("devices_appliance_idle_below_running");
        assertThatThrownBy(() -> execute("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, shelly_address, shelly_generation,
                appliance_running_above_watts, appliance_idle_below_watts, appliance_min_run_minutes,
                appliance_finished_after_minutes, appliance_reminder_every_minutes, appliance_max_reminders,
                appliance_quiet_from, appliance_quiet_to)
            VALUES (now(), 'Dishwasher', 'KITCHEN', 'APPLIANCE', 'SHELLY', true, '192.0.2.64', 1,
                20, 3, 5, 10, 30, 4, '22:00:30', '07:00')"""))
            .hasMessageContaining("devices_appliance_range");

        assertThat(count()).isZero();
    }

    //An inverter (HAS-326): its binding becomes the six columns of V20 and is answered as it was sent;
    //found by its kind, which is how energy-service will read it
    @Test
    void should_register_an_inverter_with_its_binding_and_answer_it_as_it_was_sent() {
        final long inverter = added(SOLAX);

        assertThat(stored(inverter))
            .containsEntry("kind", "INVERTER")
            .containsEntry("vendor", "OTHER")
            .containsEntry("inverter_brand", "SOLAX")
            .containsEntry("inverter_serial_number", "H1234567890ABC")
            .containsEntry("inverter_address", "192.0.2.120")
            .containsEntry("inverter_registration_number", "SABCDEFGHI")
            .containsEntry("inverter_logger_serial", null)
            .containsEntry("inverter_battery_serial_number", "B1234567890ABC")
            .containsEntry("shelly_address", null);

        webTestClient.get()
            .uri("/device/registry?kind=inverter")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].id").isEqualTo(inverter)
            .jsonPath("$[0].kind").isEqualTo("inverter")
            .jsonPath("$[0].inverter.brand").isEqualTo("solax")
            .jsonPath("$[0].inverter.serialNumber").isEqualTo("H1234567890ABC")
            .jsonPath("$[0].inverter.registrationNumber").isEqualTo("SABCDEFGHI")
            .jsonPath("$[0].inverter.loggerSerial").doesNotExist()
            .jsonPath("$[0].shelly").doesNotExist();
    }

    //the logger serial is four bytes: the largest of them is stored and answered whole
    @Test
    void should_register_a_sofar_with_the_largest_logger_serial() {
        final long inverter = added(SOFAR.replace("1234567890", "4294967295"));

        assertThat(stored(inverter)).containsEntry("inverter_logger_serial", 4294967295L);
        webTestClient.get()
            .uri("/device/registry/{id}", inverter)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.inverter.loggerSerial").isEqualTo(4294967295L);
    }

    //the serial number is the identity of an inverter: a second one is refused by the index of V20
    @Test
    void should_refuse_an_inverter_registered_twice_by_its_serial_number() {
        added(SOLAX);

        conflict(SOLAX.replace("\"name\": \"Inverter\"", "\"name\": \"Inverter again\""),
            "A device is already registered for the inverter with the serial number [H1234567890ABC]");
    }

    //refused in words by DeviceRules, before any CHECK of V20 answers with its name
    @Test
    void should_refuse_an_inverter_the_registry_does_not_take_and_say_why() {
        invalid(SOLAX.replace("\"vendor\": \"other\"", "\"vendor\": \"shelly\""),
            "A device of the vendor [shelly] is not reached through the binding [inverter]");
        invalid(SOFAR.replace("\"loggerSerial\": 1234567890", "\"registrationNumber\": \"SABCDEFGHI\""),
            "registrationNumber is the one of a SolaX dongle, not of an inverter of the brand [sofar]");
        invalid(BTHOME_WEATHER.replace("\"bthome\": {\"address\": \"7c:c6:b6:00:00:01\"}",
                "\"inverter\": {\"brand\": \"solax\", \"serialNumber\": \"H1234567890ABC\"}"),
            "A device of the kind [weather station] is not reached through the binding [inverter], only through: bthome");

        assertThat(count()).isZero();
    }

    //the schema keeps the binding to inverters of no named vendor and to the fields of its brand, for a
    //row written past the API
    @Test
    void should_refuse_an_inverter_binding_the_schema_does_not_take() {
        assertThatThrownBy(() -> execute("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, shelly_address, shelly_generation)
            VALUES (now(), 'Inverter', 'BOILER', 'INVERTER', 'SHELLY', true, '192.0.2.120', 1)"""))
            .hasMessageContaining("devices_inverter_of_inverters");
        assertThatThrownBy(() -> execute("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, inverter_brand, inverter_serial_number)
            VALUES (now(), 'Inverter', 'BOILER', 'INVERTER', 'SHELLY', true, 'SOLAX', 'H1234567890ABC')"""))
            .hasMessageContaining("devices_inverter_vendor");
        assertThatThrownBy(() -> execute("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, inverter_brand, inverter_serial_number,
                inverter_logger_serial)
            VALUES (now(), 'Inverter', 'BOILER', 'INVERTER', 'OTHER', true, 'SOLAX', 'H1234567890ABC', 1234567890)"""))
            .hasMessageContaining("devices_inverter_brand_fields");
        assertThatThrownBy(() -> execute("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, inverter_brand, inverter_serial_number)
            VALUES (now(), 'Inverter', 'BOILER', 'INVERTER', 'OTHER', true, 'SOLAX', 'h1234567890abc')"""))
            .hasMessageContaining("devices_inverter_format");
        assertThatThrownBy(() -> execute("""
            INSERT INTO devices (created_at, name, room, kind, vendor, enabled, inverter_serial_number)
            VALUES (now(), 'Inverter', 'BOILER', 'INVERTER', 'OTHER', true, 'H1234567890ABC')"""))
            .hasMessageContaining("devices_inverter_binding_whole");

        assertThat(count()).isZero();
    }

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

    //the array written out: a Long[] read back is equal to nothing but itself
    private Map<String, Object> stored(final long id) {
        final Map<String, Object> device = new HashMap<>(
            row("SELECT *, array_to_string(opening_sensors, ',') AS sensors FROM devices WHERE id = " + id));
        device.put("opening_sensors", device.remove("sensors"));
        return device;
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
