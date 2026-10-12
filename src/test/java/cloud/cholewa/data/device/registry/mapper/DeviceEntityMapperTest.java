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
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

//Every write stores the whole row, so a column this mapper forgets is written as null. The rows
//are compared whole, but for the times the mapper reads from the clock.
class DeviceEntityMapperTest {

    private static final LocalDateTime REGISTERED = LocalDateTime.of(2026, 10, 12, 12, 0);

    private static final SmartDevice BLINDS = SmartDevice.builder()
        .id(99L)
        .name("Office window")
        .room(RoomName.OFFICE)
        .kind(DeviceKind.BLINDS)
        .vendor(SmartVendor.SHELLY)
        .enabled(false)
        .shelly(ShellyBinding.builder()
            .address("192.0.2.17")
            .generation(2)
            .deviceId("shellyplus2pm-a1b2c3d4e5f6")
            .channel(1)
            .build())
        .placement(BlindsPlacement.EXTERNAL)
        .positioning(true)
        .build();

    private final DeviceEntityMapper mapper = new DeviceEntityMapper();

    //the id of the payload is not the registry's: the database gives one
    @Test
    void should_make_a_new_row_of_a_shelly_device_without_the_id_it_was_sent_with() {
        final DeviceEntity entity = mapper.toNewEntity(BLINDS);

        assertThat(entity).usingRecursiveComparison().ignoringFields("createdAt").isEqualTo(DeviceEntity.builder()
            .name("Office window")
            .room(RoomName.OFFICE)
            .kind(DeviceKind.BLINDS)
            .vendor(SmartVendor.SHELLY)
            .enabled(false)
            .shellyAddress("192.0.2.17")
            .shellyGeneration(2)
            .shellyDeviceId("shellyplus2pm-a1b2c3d4e5f6")
            .shellyChannel(1)
            .placement(BlindsPlacement.EXTERNAL)
            .positioning(true)
            .build());
        assertThat(entity.getCreatedAt()).isBetween(aMinuteAgo(), now());
    }

    @Test
    void should_make_a_new_row_of_an_eaton_device_and_of_a_bluetooth_one() {
        final SmartDevice light = SmartDevice.builder()
            .name("Kitchen ceiling")
            .room(RoomName.KITCHEN)
            .kind(DeviceKind.LIGHT)
            .vendor(SmartVendor.EATON)
            .enabled(true)
            .eaton(EatonBinding.builder().gateway(EatonGatewayType.LIGHTS).point(44).build())
            .lightKind(LightKind.DIMMER)
            .build();
        final SmartDevice weather = SmartDevice.builder()
            .name("Garden weather")
            .room(RoomName.GARDEN)
            .kind(DeviceKind.WEATHER_STATION)
            .vendor(SmartVendor.OTHER)
            .enabled(true)
            .bthome(BthomeBinding.builder().address("7c:c6:b6:00:00:01").build())
            .build();

        assertThat(mapper.toNewEntity(light)).usingRecursiveComparison().ignoringFields("createdAt")
            .isEqualTo(DeviceEntity.builder()
                .name("Kitchen ceiling")
                .room(RoomName.KITCHEN)
                .kind(DeviceKind.LIGHT)
                .vendor(SmartVendor.EATON)
                .enabled(true)
                .eatonGateway(EatonGatewayType.LIGHTS)
                .eatonPoint(44)
                .lightKind(LightKind.DIMMER)
                .build());
        assertThat(mapper.toNewEntity(weather)).usingRecursiveComparison().ignoringFields("createdAt")
            .isEqualTo(DeviceEntity.builder()
                .name("Garden weather")
                .room(RoomName.GARDEN)
                .kind(DeviceKind.WEATHER_STATION)
                .vendor(SmartVendor.OTHER)
                .enabled(true)
                .bthomeAddress("7c:c6:b6:00:00:01")
                .build());
    }

    //What the row was is gone, but for what the registry gave it: an Eaton light replaced by a
    //Shelly one keeps its id and the time it was registered, and nothing of the Eaton binding
    @Test
    void should_replace_everything_a_client_says_of_a_device_and_keep_what_the_registry_gave_it() {
        final DeviceEntity existing = DeviceEntity.builder()
            .id(7L)
            .createdAt(REGISTERED)
            .name("Kitchen ceiling")
            .room(RoomName.KITCHEN)
            .kind(DeviceKind.LIGHT)
            .vendor(SmartVendor.EATON)
            .enabled(true)
            .eatonGateway(EatonGatewayType.LIGHTS)
            .eatonPoint(44)
            .lightKind(LightKind.DIMMER)
            .build();
        final SmartDevice replacement = SmartDevice.builder()
            .id(99L)
            .name("Kitchen spots")
            .room(RoomName.LIVING_ROOM)
            .kind(DeviceKind.LIGHT)
            .vendor(SmartVendor.SHELLY)
            .enabled(false)
            .shelly(ShellyBinding.builder().address("192.0.2.18").generation(1).build())
            .build();

        final DeviceEntity entity = mapper.toReplacement(existing, replacement);

        assertThat(entity).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(DeviceEntity.builder()
            .id(7L)
            .createdAt(REGISTERED)
            .name("Kitchen spots")
            .room(RoomName.LIVING_ROOM)
            .kind(DeviceKind.LIGHT)
            .vendor(SmartVendor.SHELLY)
            .enabled(false)
            .shellyAddress("192.0.2.18")
            .shellyGeneration(1)
            .build());
        assertThat(entity.getUpdatedAt()).isBetween(aMinuteAgo(), now());
    }

    //A device with everything filled - all three bindings, which no rule would let in, but the
    //mapper does not judge: a column added to the row and forgotten in the mapper comes out null
    //here, where the comparisons above would find null on both sides
    @Test
    void should_leave_no_column_out_of_a_new_row_or_of_a_replacement() {
        final SmartDevice everything = SmartDevice.builder()
            .name("Everything")
            .room(RoomName.OFFICE)
            .kind(DeviceKind.BLINDS)
            .vendor(SmartVendor.SHELLY)
            .enabled(true)
            .shelly(ShellyBinding.builder().address("192.0.2.17").generation(2).deviceId("shelly-1").channel(1).build())
            .eaton(EatonBinding.builder().gateway(EatonGatewayType.BLINDS).point(7).build())
            .bthome(BthomeBinding.builder().address("7c:c6:b6:00:00:01").build())
            .lightKind(LightKind.RELAY)
            .placement(BlindsPlacement.INTERNAL)
            .positioning(false)
            .build();
        final DeviceEntity existing = DeviceEntity.builder().id(7L).createdAt(REGISTERED).build();

        assertThat(mapper.toNewEntity(everything)).hasNoNullFieldsOrPropertiesExcept("id", "updatedAt");
        assertThat(mapper.toReplacement(existing, everything)).hasNoNullFieldsOrProperties();
    }

    //the row with every column filled: whatever is added to it later is copied as well
    @Test
    void should_switch_a_device_off_and_on_and_change_nothing_else_of_its_row() {
        final DeviceEntity existing = DeviceEntity.builder()
            .id(7L)
            .createdAt(REGISTERED)
            .name("Office window")
            .room(RoomName.OFFICE)
            .kind(DeviceKind.BLINDS)
            .vendor(SmartVendor.SHELLY)
            .enabled(true)
            .shellyAddress("192.0.2.17")
            .shellyGeneration(2)
            .shellyDeviceId("shellyplus2pm-a1b2c3d4e5f6")
            .shellyChannel(1)
            .eatonGateway(EatonGatewayType.BLINDS)
            .eatonPoint(7)
            .bthomeAddress("7c:c6:b6:00:00:01")
            .lightKind(LightKind.RELAY)
            .placement(BlindsPlacement.INTERNAL)
            .positioning(false)
            .build();

        final DeviceEntity off = mapper.withEnabled(existing, false);

        assertThat(off).usingRecursiveComparison().ignoringFields("enabled", "updatedAt").isEqualTo(existing);
        assertThat(off).hasNoNullFieldsOrProperties();
        assertThat(off.getEnabled()).isFalse();
        assertThat(off.getUpdatedAt()).isBetween(aMinuteAgo(), now());
        assertThat(mapper.withEnabled(off, true).getEnabled()).isTrue();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }

    private static LocalDateTime aMinuteAgo() {
        return now().minusMinutes(1);
    }
}
