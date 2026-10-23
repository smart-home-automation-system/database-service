package cloud.cholewa.data.device.registry.mapper;

import cloud.cholewa.data.device.registry.model.DeviceEntity;
import cloud.cholewa.home.model.ApplianceSettings;
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
import java.time.LocalTime;
import java.util.List;

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
            .openingSensors(List.of(31L, 30L))
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
            .openingSensors(List.of(31L, 30L))
            .build());
    }

    //An appliance (HAS-272): the eight settings of its row as the part of the model, the quiet times
    //as HH:mm text - minutes with a leading zero, never seconds
    @Test
    void should_map_an_appliance_with_its_settings() {
        final DeviceEntity entity = row()
            .id(64L)
            .name("Dishwasher")
            .room(RoomName.KITCHEN)
            .kind(DeviceKind.APPLIANCE)
            .vendor(SmartVendor.SHELLY)
            .enabled(true)
            .shellyAddress("192.0.2.64")
            .shellyGeneration(1)
            .shellyChannel(0)
            .applianceRunningAboveWatts(20)
            .applianceIdleBelowWatts(3)
            .applianceMinRunMinutes(5)
            .applianceFinishedAfterMinutes(10)
            .applianceReminderEveryMinutes(30)
            .applianceMaxReminders(0)
            .applianceQuietFrom(LocalTime.of(22, 0))
            .applianceQuietTo(LocalTime.of(7, 5))
            .build();

        assertThat(mapper.toDevice(entity)).isEqualTo(SmartDevice.builder()
            .id(64L)
            .name("Dishwasher")
            .room(RoomName.KITCHEN)
            .kind(DeviceKind.APPLIANCE)
            .vendor(SmartVendor.SHELLY)
            .enabled(true)
            .shelly(ShellyBinding.builder().address("192.0.2.64").generation(1).channel(0).build())
            .appliance(ApplianceSettings.builder()
                .runningAboveWatts(20)
                .idleBelowWatts(3)
                .minRunMinutes(5)
                .finishedAfterMinutes(10)
                .reminderEveryMinutes(30)
                .maxReminders(0)
                .quietFrom("22:00")
                .quietTo("07:05")
                .build())
            .build());
    }

    //a device of another kind has no settings of an appliance - the part is left out, not an empty object
    @Test
    void should_leave_out_the_settings_of_an_appliance_on_another_kind() {
        final DeviceEntity entity = row().id(17L).name("Office window").room(RoomName.OFFICE).kind(DeviceKind.BLINDS)
            .vendor(SmartVendor.SHELLY).enabled(true).shellyAddress("192.0.2.17").shellyGeneration(1).build();

        assertThat(mapper.toDevice(entity).getAppliance()).isNull();
    }

    //a window without a sensor: the list is left out, as the builder leaves it and the JSON omits it
    @Test
    void should_leave_out_the_opening_sensors_of_blinds_that_have_none() {
        final DeviceEntity entity = row().id(17L).name("Office window").room(RoomName.OFFICE).kind(DeviceKind.BLINDS)
            .vendor(SmartVendor.SHELLY).enabled(true).shellyAddress("192.0.2.17").shellyGeneration(1)
            .openingSensors(List.of()).build();

        assertThat(mapper.toDevice(entity).getOpeningSensors()).isNull();
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
    void should_map_a_bluetooth_sensor_by_its_address_alone() {
        final DeviceEntity entity = row()
            .id(19L)
            .name("Garden weather")
            .room(RoomName.GARDEN)
            .kind(DeviceKind.WEATHER_STATION)
            .vendor(SmartVendor.SHELLY)
            .enabled(true)
            .bthomeAddress("7c:c6:b6:00:00:01")
            .build();

        assertThat(mapper.toDevice(entity)).isEqualTo(SmartDevice.builder()
            .id(19L)
            .name("Garden weather")
            .room(RoomName.GARDEN)
            .kind(DeviceKind.WEATHER_STATION)
            .vendor(SmartVendor.SHELLY)
            .enabled(true)
            .bthome(BthomeBinding.builder().address("7c:c6:b6:00:00:01").build())
            .build());
    }

    //A binding the device does not have is absent, not an object of nulls: the model is written
    //to JSON with NON_EMPTY, which leaves a null out and keeps an empty object - a reader would
    //find "eaton": {} on a Shelly and could not tell the binding by its presence
    @Test
    void should_leave_out_the_bindings_a_device_does_not_have() {
        final SmartDevice shelly = mapper.toDevice(row().shellyAddress("192.0.2.17").shellyGeneration(1).build());
        final SmartDevice eaton = mapper.toDevice(row().eatonGateway(EatonGatewayType.BLINDS).eatonPoint(7).build());
        final SmartDevice bthome = mapper.toDevice(row().bthomeAddress("7c:c6:b6:00:00:01").build());

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
