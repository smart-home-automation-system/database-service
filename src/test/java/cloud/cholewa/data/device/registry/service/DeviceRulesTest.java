package cloud.cholewa.data.device.registry.service;

import cloud.cholewa.data.error.InvalidDeviceException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//What the registry asks of a device beyond the SDK model: one binding, of the vendor and of the
//kind, and nothing of another kind.
class DeviceRulesTest {

    private static final ShellyBinding SHELLY = ShellyBinding.builder()
        .address("192.0.2.17").generation(2).deviceId("shellyplus1-a1b2c3d4e5f6").build();
    private static final EatonBinding EATON = EatonBinding.builder().gateway(EatonGatewayType.LIGHTS).point(44).build();
    private static final BthomeBinding BTHOME = BthomeBinding.builder().address("7c:c6:b6:00:00:01").build();

    //every pair of a kind and a binding the house may have, with the vendor the binding is of
    @ParameterizedTest
    @CsvSource({
        "BLINDS, SHELLY, shelly",
        "BLINDS, EATON, eaton",
        "LIGHT, SHELLY, shelly",
        "LIGHT, EATON, eaton",
        "OPENING_SENSOR, SHELLY, bthome",
        "OPENING_SENSOR, OTHER, bthome",
        "OPENING_SENSOR, EATON, eaton",
        "MOTION_SENSOR, SHELLY, bthome",
        "MOTION_SENSOR, SHELLY, shelly",
        "PRESENCE_SENSOR, SHELLY, shelly",
        "TEMPERATURE_SENSOR, EATON, eaton",
        "TEMPERATURE_SENSOR, SHELLY, shelly",
        "TEMPERATURE_SENSOR, OTHER, bthome",
        "WEATHER_STATION, SHELLY, bthome",
        "WEATHER_STATION, OTHER, bthome",
        "BTHOME_GATEWAY, SHELLY, shelly",
        "PUSH_BUTTON, EATON, eaton",
        "PUSH_BUTTON, SHELLY, shelly",
        "MOTION_SENSOR, EATON, eaton"
    })
    void should_take_a_device_whose_binding_fits_its_vendor_and_its_kind(
        final DeviceKind kind, final SmartVendor vendor, final String binding
    ) {
        assertThatCode(() -> DeviceRules.check(device(kind, vendor, binding).build())).doesNotThrowAnyException();
    }

    @Test
    void should_refuse_a_device_without_a_binding() {
        assertThatThrownBy(() -> DeviceRules.check(device(DeviceKind.LIGHT, SmartVendor.SHELLY, "").build()))
            .isInstanceOf(InvalidDeviceException.class)
            .hasMessageContaining("exactly one binding")
            .hasMessageEndingWith("this one has none");
    }

    @Test
    void should_refuse_a_device_with_two_bindings_and_name_them() {
        final SmartDevice device = device(DeviceKind.LIGHT, SmartVendor.SHELLY, "shelly").eaton(EATON).build();

        assertThatThrownBy(() -> DeviceRules.check(device))
            .isInstanceOf(InvalidDeviceException.class)
            .hasMessageEndingWith("this one has shelly and eaton");
    }

    //an Eaton device is reached through a gateway of the AMX system, and nothing else is; the
    //binding of a Shelly is a Shelly's
    @ParameterizedTest
    @CsvSource({
        "EATON, shelly",
        "EATON, bthome",
        "SHELLY, eaton",
        "OTHER, eaton",
        "OTHER, shelly"
    })
    void should_refuse_a_binding_that_is_not_of_the_vendor(final SmartVendor vendor, final String binding) {
        assertThatThrownBy(() -> DeviceRules.check(device(DeviceKind.TEMPERATURE_SENSOR, vendor, binding).build()))
            .isInstanceOf(InvalidDeviceException.class)
            .hasMessage("A device of the vendor [" + vendor.getValue() + "] is not reached through the binding ["
                + binding + "]");
    }

    @ParameterizedTest
    @CsvSource({
        "BLINDS, SHELLY, bthome, 'shelly, eaton'",
        "LIGHT, OTHER, bthome, 'shelly, eaton'",
        "WEATHER_STATION, SHELLY, shelly, bthome",
        "WEATHER_STATION, EATON, eaton, bthome",
        "BTHOME_GATEWAY, SHELLY, bthome, shelly",
        "BTHOME_GATEWAY, EATON, eaton, shelly",
        "PUSH_BUTTON, SHELLY, bthome, 'shelly, eaton'"
    })
    void should_refuse_a_binding_the_kind_is_not_reached_through_and_say_which_it_is(
        final DeviceKind kind, final SmartVendor vendor, final String binding, final String possible
    ) {
        assertThatThrownBy(() -> DeviceRules.check(device(kind, vendor, binding).build()))
            .isInstanceOf(InvalidDeviceException.class)
            .hasMessage("A device of the kind [" + kind.getValue() + "] is not reached through the binding ["
                + binding + "], only through: " + possible);
    }

    @ParameterizedTest
    @EnumSource(value = DeviceKind.class, names = {"LIGHT", "WEATHER_STATION", "BTHOME_GATEWAY"}, mode = EnumSource.Mode.EXCLUDE)
    void should_refuse_the_way_a_light_is_driven_on_anything_but_a_light(final DeviceKind kind) {
        final SmartDevice device = device(kind, SmartVendor.EATON, "eaton").lightKind(LightKind.DIMMER).build();

        assertThatThrownBy(() -> DeviceRules.check(device))
            .isInstanceOf(InvalidDeviceException.class)
            .hasMessage("lightKind describes a light, not a device of the kind [" + kind.getValue() + "]");
    }

    @Test
    void should_refuse_the_placement_and_the_positioning_of_blinds_on_a_light() {
        final SmartDevice placed = device(DeviceKind.LIGHT, SmartVendor.EATON, "eaton")
            .placement(BlindsPlacement.EXTERNAL).build();
        final SmartDevice positioned = device(DeviceKind.LIGHT, SmartVendor.EATON, "eaton").positioning(false).build();

        assertThatThrownBy(() -> DeviceRules.check(placed))
            .isInstanceOf(InvalidDeviceException.class)
            .hasMessage("placement and positioning describe blinds, not a device of the kind [light]");
        assertThatThrownBy(() -> DeviceRules.check(positioned)).isInstanceOf(InvalidDeviceException.class);
    }

    //"a" and one emoji: three units of a string, which is what the model counts, and two
    //characters, which is what the schema counts - refused here, in words, before the schema is
    @Test
    void should_refuse_a_name_of_fewer_characters_than_the_schema_takes() {
        final SmartDevice device = device(DeviceKind.LIGHT, SmartVendor.SHELLY, "shelly").name("a😀").build();

        assertThatThrownBy(() -> DeviceRules.check(device))
            .isInstanceOf(InvalidDeviceException.class)
            .hasMessage("The name of a device has at least 3 characters");
    }

    //a Shelly that reports over MQTT is found by the id it reports under: without it the device
    //could be driven and never heard
    @ParameterizedTest
    @CsvSource({"2", "3", "4"})
    void should_refuse_a_shelly_that_reports_over_mqtt_without_the_id_it_reports_under(final int generation) {
        final SmartDevice device = device(DeviceKind.LIGHT, SmartVendor.SHELLY, "")
            .shelly(ShellyBinding.builder().address("192.0.2.17").generation(generation).channel(0).build())
            .build();

        assertThatThrownBy(() -> DeviceRules.check(device))
            .isInstanceOf(InvalidDeviceException.class)
            .hasMessage("A Shelly of generation " + generation + " needs its deviceId - the id it reports under over MQTT");
    }

    //the zone of a Presence Gen4 is its channel (HAS-260): presencezone:200 is the main zone
    @ParameterizedTest
    @CsvSource({"200", "299"})
    void should_take_a_zone_as_the_channel_of_a_presence_sensor(final int channel) {
        final SmartDevice device = device(DeviceKind.PRESENCE_SENSOR, SmartVendor.SHELLY, "")
            .shelly(ShellyBinding.builder().address("192.0.2.17").generation(4).deviceId("shellypresence-a1b2c3d4e5f6")
                .channel(channel).build())
            .build();

        assertThatCode(() -> DeviceRules.check(device)).doesNotThrowAnyException();
    }

    //an output is a channel of any kind, the presence sensor included
    @ParameterizedTest
    @EnumSource(value = DeviceKind.class, names = {"LIGHT", "PRESENCE_SENSOR"})
    void should_take_an_output_as_the_channel_of_any_kind(final DeviceKind kind) {
        final SmartDevice device = device(kind, SmartVendor.SHELLY, "")
            .shelly(ShellyBinding.builder().address("192.0.2.17").generation(2).deviceId("shellypro4pm-a1b2c3d4e5f6")
                .channel(3).build())
            .build();

        assertThatCode(() -> DeviceRules.check(device)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"4", "199"})
    void should_refuse_a_channel_that_is_neither_an_output_nor_a_zone(final int channel) {
        final SmartDevice device = device(DeviceKind.PRESENCE_SENSOR, SmartVendor.SHELLY, "")
            .shelly(ShellyBinding.builder().address("192.0.2.17").generation(4).deviceId("shellypresence-a1b2c3d4e5f6")
                .channel(channel).build())
            .build();

        assertThatThrownBy(() -> DeviceRules.check(device))
            .isInstanceOf(InvalidDeviceException.class)
            .hasMessage("The channel of a Shelly is an output (0 to 3) or the zone of a presence sensor (200 to 299), not "
                + channel);
    }

    @ParameterizedTest
    @EnumSource(value = DeviceKind.class, names = {"LIGHT", "MOTION_SENSOR", "BTHOME_GATEWAY"})
    void should_refuse_a_zone_as_the_channel_of_another_kind(final DeviceKind kind) {
        final SmartDevice device = device(kind, SmartVendor.SHELLY, "")
            .shelly(ShellyBinding.builder().address("192.0.2.17").generation(4).deviceId("shellypresence-a1b2c3d4e5f6")
                .channel(200).build())
            .build();

        assertThatThrownBy(() -> DeviceRules.check(device))
            .isInstanceOf(InvalidDeviceException.class)
            .hasMessage("The channel 200 is a zone, which only a device of the kind [presence sensor] has, not one of the kind ["
                + kind.getValue() + "]");
    }

    //a Gen1 device does not report over MQTT and has no such id to give
    @Test
    void should_take_a_shelly_of_the_first_generation_without_an_id() {
        final SmartDevice device = device(DeviceKind.LIGHT, SmartVendor.SHELLY, "")
            .shelly(ShellyBinding.builder().address("192.0.2.17").generation(1).build())
            .build();

        assertThatCode(() -> DeviceRules.check(device)).doesNotThrowAnyException();
    }

    //none of the three is asked for: a light may come without the way it is driven, blinds
    //without their placement - "not known" is an answer the model allows
    @Test
    void should_take_a_light_and_blinds_with_and_without_what_describes_them() {
        assertThatCode(() -> {
            DeviceRules.check(device(DeviceKind.LIGHT, SmartVendor.SHELLY, "shelly").build());
            DeviceRules.check(device(DeviceKind.LIGHT, SmartVendor.SHELLY, "shelly").lightKind(LightKind.RGBW).build());
            DeviceRules.check(device(DeviceKind.BLINDS, SmartVendor.SHELLY, "shelly").build());
            DeviceRules.check(device(DeviceKind.BLINDS, SmartVendor.SHELLY, "shelly")
                .placement(BlindsPlacement.INTERNAL).positioning(true).build());
        }).doesNotThrowAnyException();
    }

    //the window of blinds may have two sensors (two sashes) or none; that each id is an opening
    //sensor of the registry is the service's to ask
    @Test
    void should_take_blinds_with_the_opening_sensors_of_their_window() {
        assertThatCode(() -> {
            DeviceRules.check(device(DeviceKind.BLINDS, SmartVendor.EATON, "eaton").openingSensors(List.of(31L, 30L)).build());
            DeviceRules.check(device(DeviceKind.BLINDS, SmartVendor.EATON, "eaton").openingSensors(List.of()).build());
            DeviceRules.check(device(DeviceKind.BLINDS, SmartVendor.EATON, "eaton").openingSensors(null).build());
            //an empty list is no list, whatever the kind - it is what every payload starts with
            DeviceRules.check(device(DeviceKind.LIGHT, SmartVendor.EATON, "eaton").openingSensors(List.of()).build());
        }).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = DeviceKind.class, names = "BLINDS", mode = EnumSource.Mode.EXCLUDE)
    void should_refuse_opening_sensors_on_anything_but_blinds(final DeviceKind kind) {
        final String binding = kind == DeviceKind.WEATHER_STATION ? "bthome" : "shelly";
        final SmartDevice device = device(kind, SmartVendor.SHELLY, binding).openingSensors(List.of(31L)).build();

        assertThatThrownBy(() -> DeviceRules.check(device))
            .isInstanceOf(InvalidDeviceException.class)
            .hasMessageContaining("openingSensors");
    }

    //the model lets a null item through: @Min says nothing of a null
    @Test
    void should_refuse_a_null_among_the_opening_sensors() {
        final SmartDevice blinds = device(DeviceKind.BLINDS, SmartVendor.EATON, "eaton")
            .openingSensors(Arrays.asList(31L, null)).build();

        assertThatThrownBy(() -> DeviceRules.check(blinds))
            .isInstanceOf(InvalidDeviceException.class)
            .hasMessage("openingSensors hold the ids of devices, and one of them is null");
    }

    @Test
    void should_refuse_an_opening_sensor_named_twice() {
        final SmartDevice blinds = device(DeviceKind.BLINDS, SmartVendor.EATON, "eaton")
            .openingSensors(List.of(31L, 30L, 31L)).build();

        assertThatThrownBy(() -> DeviceRules.check(blinds))
            .isInstanceOf(InvalidDeviceException.class)
            .hasMessage("openingSensors name a sensor more than once: [31, 30, 31]");
    }

    private static SmartDevice.SmartDeviceBuilder<?, ?> device(
        final DeviceKind kind, final SmartVendor vendor, final String binding
    ) {
        return SmartDevice.builder()
            .name("A device")
            .room(RoomName.OFFICE)
            .kind(kind)
            .vendor(vendor)
            .enabled(true)
            .shelly("shelly".equals(binding) ? SHELLY : null)
            .eaton("eaton".equals(binding) ? EATON : null)
            .bthome("bthome".equals(binding) ? BTHOME : null);
    }
}
