package cloud.cholewa.data.device.registry.mapper;

import cloud.cholewa.data.device.registry.model.DeviceEntity;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SmartDeviceMapperTest {

    private final SmartDeviceMapper mapper = new SmartDeviceMapper();

    //whole models are compared: a field the mapper forgets is a field a reader never gets
    @Test
    void should_map_blinds_on_a_shelly_with_everything_they_have() {
        final DeviceEntity entity = row()
            .id(17L)
            .name("Office window")
            .room(RoomName.OFFICE)
            .kind(DeviceKind.BLINDS)
            .vendor(SmartVendor.SHELLY)
            .enabled(true)
            .shellyAddress("192.0.2.17")
            .shellyGeneration(2)
            .shellyDeviceId("shellyplus2pm-a1b2c3d4e5f6")
            .shellyChannel(0)
            .placement(BlindsPlacement.EXTERNAL)
            .positioning(true)
            .build();

        assertThat(mapper.toDevice(entity)).isEqualTo(SmartDevice.builder()
            .id(17L)
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
            .build());
    }

    @Test
    void should_map_a_light_on_an_eaton_point() {
        final DeviceEntity entity = row()
            .id(18L)
            .name("Kitchen ceiling")
            .room(RoomName.KITCHEN)
            .kind(DeviceKind.LIGHT)
            .vendor(SmartVendor.EATON)
            .enabled(false)
            .eatonGateway(EatonGatewayType.LIGHTS)
            .eatonPoint(44)
            .lightKind(LightKind.DIMMER)
            .build();

        assertThat(mapper.toDevice(entity)).isEqualTo(SmartDevice.builder()
            .id(18L)
            .name("Kitchen ceiling")
            .room(RoomName.KITCHEN)
            .kind(DeviceKind.LIGHT)
            .vendor(SmartVendor.EATON)
            .enabled(false)
            .eaton(EatonBinding.builder().gateway(EatonGatewayType.LIGHTS).point(44).build())
            .lightKind(LightKind.DIMMER)
            .build());
    }

    @Test
    void should_map_a_sensor_behind_a_bluetooth_gateway() {
        final DeviceEntity entity = row()
            .id(19L)
            .name("Garden weather")
            .room(RoomName.GARDEN)
            .kind(DeviceKind.WEATHER_STATION)
            .vendor(SmartVendor.SHELLY)
            .enabled(true)
            .bthomeGatewayDeviceId("shellyblugw-a1b2c3d4e5f6")
            .bthomeAddress("7c:c6:b6:00:00:01")
            .build();

        assertThat(mapper.toDevice(entity)).isEqualTo(SmartDevice.builder()
            .id(19L)
            .name("Garden weather")
            .room(RoomName.GARDEN)
            .kind(DeviceKind.WEATHER_STATION)
            .vendor(SmartVendor.SHELLY)
            .enabled(true)
            .bthome(BthomeBinding.builder()
                .gatewayDeviceId("shellyblugw-a1b2c3d4e5f6")
                .address("7c:c6:b6:00:00:01")
                .build())
            .build());
    }

    //A binding the device does not have is absent, not an object of nulls: the model is written
    //to JSON with NON_EMPTY, which leaves a null out and keeps an empty object - a reader would
    //find "eaton": {} on a Shelly and could not tell the binding by its presence
    @Test
    void should_leave_out_the_bindings_a_device_does_not_have() {
        final SmartDevice shelly = mapper.toDevice(row().shellyAddress("192.0.2.17").shellyGeneration(1).build());
        final SmartDevice eaton = mapper.toDevice(row().eatonGateway(EatonGatewayType.BLINDS).eatonPoint(7).build());
        final SmartDevice bthome = mapper.toDevice(
            row().bthomeGatewayDeviceId("shellyblugw-a1b2c3d4e5f6").bthomeAddress("7c:c6:b6:00:00:01").build());

        assertThat(shelly.getShelly()).isNotNull();
        assertThat(shelly.getEaton()).isNull();
        assertThat(shelly.getBthome()).isNull();

        assertThat(eaton.getShelly()).isNull();
        assertThat(eaton.getEaton()).isNotNull();
        assertThat(eaton.getBthome()).isNull();

        assertThat(bthome.getShelly()).isNull();
        assertThat(bthome.getEaton()).isNull();
        assertThat(bthome.getBthome()).isNotNull();
    }

    //a Gen1 Shelly has no MQTT id, and a device without channels no channel
    @Test
    void should_keep_the_optional_parts_of_a_shelly_binding_empty() {
        final SmartDevice device = mapper.toDevice(row().shellyAddress("192.0.2.17").shellyGeneration(1).build());

        assertThat(device.getShelly()).isEqualTo(ShellyBinding.builder().address("192.0.2.17").generation(1).build());
    }

    private static DeviceEntity.DeviceEntityBuilder row() {
        return DeviceEntity.builder()
            .id(1L)
            .createdAt(LocalDateTime.of(2026, 10, 12, 12, 0))
            .name("Device")
            .room(RoomName.OFFICE)
            .kind(DeviceKind.LIGHT)
            .vendor(SmartVendor.SHELLY)
            .enabled(true);
    }
}
