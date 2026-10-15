package cloud.cholewa.data.device.eaton.mapper;

import cloud.cholewa.data.device.registry.model.DeviceEntity;
import cloud.cholewa.home.model.DeviceKind;
import cloud.cholewa.home.model.EatonDeviceConfiguration;
import cloud.cholewa.home.model.EatonGatewayType;
import cloud.cholewa.home.model.LightKind;
import cloud.cholewa.home.model.RoomName;
import cloud.cholewa.home.model.SmartDeviceType;
import cloud.cholewa.home.model.SmartVendor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

//Where the vocabulary of the Eaton configuration (a type) meets the one of the registry (a kind,
//and for a light how it is driven).
class EatonConfigurationMapperTest {

    private final EatonConfigurationMapper mapper = new EatonConfigurationMapper();

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
        "TEMPERATURE_SENSOR, TEMPERATURE_SENSOR, -",
        "BLINDS, BLINDS, -",
        "LIGHT, LIGHT, RELAY",
        "DIMMER, LIGHT, DIMMER",
        "PUSH_BUTTON, PUSH_BUTTON, -",
        "MOTION_SENSOR, MOTION_SENSOR, -"
    })
    void should_register_a_type_as_its_kind(final SmartDeviceType type, final DeviceKind kind, final LightKind lightKind) {
        final DeviceEntity device = mapper.toDevice(configuration(type, RoomName.OFFICE, 7)).orElseThrow();

        assertThat(device.getKind()).isEqualTo(kind);
        assertThat(device.getLightKind()).isEqualTo(lightKind);
        assertThat(device.getVendor()).isEqualTo(SmartVendor.EATON);
        assertThat(device.getEnabled()).isTrue();
        assertThat(device.getEatonGateway()).isEqualTo(EatonGatewayType.LIGHTS);
        assertThat(device.getEatonPoint()).isEqualTo(7);
        assertThat(device.getRoom()).isEqualTo(RoomName.OFFICE);
        //a new row: the database gives the id
        assertThat(device.getId()).isNull();
        assertThat(device.getCreatedAt()).isNotNull();
        //no other binding, nothing of blinds - the schema would refuse the row
        assertThat(device.getShellyAddress()).isNull();
        assertThat(device.getBthomeAddress()).isNull();
        assertThat(device.getPlacement()).isNull();
        assertThat(device.getPositioning()).isNull();
    }

    @Test
    void should_have_no_device_for_the_type_other() {
        assertThat(mapper.toDevice(configuration(SmartDeviceType.OTHER, RoomName.OFFICE, 7))).isEmpty();
    }

    //the wording of the migration that moved the configurations in (V13) - the two must agree,
    //or a configuration added through the API reads unlike its neighbours
    @ParameterizedTest
    @CsvSource({
        "TEMPERATURE_SENSOR, LIVING_ROOM, 45, Living room temperature sensor 45",
        "BLINDS, BATHROOM_DOWN, 7, Bathroom down blinds 7",
        "DIMMER, HALL_UP, 99, Hall up light 99",
        "LIGHT, LOFT, 1, Loft light 1",
        "PUSH_BUTTON, BATHROOM_UP, 80, Bathroom up push button 80",
        "MOTION_SENSOR, STAIRS, 92, Stairs motion sensor 92"
    })
    void should_name_a_configuration_by_its_room_its_kind_and_its_point(
        final SmartDeviceType type, final RoomName room, final int point, final String name
    ) {
        assertThat(mapper.toDevice(configuration(type, room, point)).orElseThrow().getName()).isEqualTo(name);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
        "TEMPERATURE_SENSOR, -, TEMPERATURE_SENSOR",
        "BLINDS, -, BLINDS",
        "LIGHT, RELAY, LIGHT",
        "LIGHT, DIMMER, DIMMER",
        "LIGHT, RGBW, LIGHT",
        "LIGHT, -, LIGHT",
        "PUSH_BUTTON, -, PUSH_BUTTON",
        "MOTION_SENSOR, -, MOTION_SENSOR"
    })
    void should_answer_a_device_as_the_type_amx_service_knows(
        final DeviceKind kind, final LightKind lightKind, final SmartDeviceType type
    ) {
        assertThat(mapper.toResponse(device(kind, lightKind)).orElseThrow().getType()).isEqualTo(type);
    }

    //A kind the Eaton vocabulary has no word for - an opening sensor on a point, should there ever
    //be one - is no configuration: amx-service fails on a datagram of the type "other", every
    //time it arrives, while a point that is not configured is a quiet 404
    @ParameterizedTest
    @EnumSource(
        value = DeviceKind.class,
        names = {"TEMPERATURE_SENSOR", "BLINDS", "LIGHT", "PUSH_BUTTON", "MOTION_SENSOR"},
        mode = EnumSource.Mode.EXCLUDE
    )
    void should_have_no_configuration_for_a_kind_the_eaton_vocabulary_lacks(final DeviceKind kind) {
        assertThat(mapper.toResponse(device(kind, null))).isEmpty();
    }

    @Test
    void should_answer_the_point_and_the_room_of_the_device() {
        assertThat(mapper.toResponse(device(DeviceKind.BLINDS, null)))
            .hasValueSatisfying(response -> {
                assertThat(response.getPoint()).isEqualTo(44);
                assertThat(response.getRoom()).isEqualTo(RoomName.KITCHEN);
            });
    }

    private static EatonDeviceConfiguration configuration(final SmartDeviceType type, final RoomName room, final int point) {
        return EatonDeviceConfiguration.builder()
            .point(point)
            .type(type)
            .gateway(EatonGatewayType.LIGHTS)
            .room(room)
            .build();
    }

    private static DeviceEntity device(final DeviceKind kind, final LightKind lightKind) {
        return DeviceEntity.builder()
            .id(3L)
            .name("A device")
            .room(RoomName.KITCHEN)
            .kind(kind)
            .vendor(SmartVendor.EATON)
            .enabled(true)
            .eatonGateway(EatonGatewayType.LIGHTS)
            .eatonPoint(44)
            .lightKind(lightKind)
            .build();
    }
}
