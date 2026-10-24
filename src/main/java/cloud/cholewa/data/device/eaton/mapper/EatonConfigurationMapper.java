package cloud.cholewa.data.device.eaton.mapper;

import cloud.cholewa.data.device.registry.model.DeviceEntity;
import cloud.cholewa.home.model.DeviceKind;
import cloud.cholewa.home.model.EatonConfigurationResponse;
import cloud.cholewa.home.model.EatonDeviceConfiguration;
import cloud.cholewa.home.model.LightKind;
import cloud.cholewa.home.model.SmartDeviceType;
import cloud.cholewa.home.model.SmartVendor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

//The Eaton configuration as amx-service knows it - a data point of a gateway, its room and its
//type - on the rows of the device registry, where it lives since V13. The API of the configuration
//did not change; this is where its vocabulary meets the one of the registry: a type is a kind, a
//dimmer is a light that dims, and a configuration has no name, so the registry gives it one.
@Component
public class EatonConfigurationMapper {

    //empty for a type the registry has no kind for (other): nothing to register it as
    public Optional<DeviceEntity> toDevice(final EatonDeviceConfiguration configuration) {
        return kindOf(configuration.getType()).map(kind -> DeviceEntity.builder()
            .createdAt(LocalDateTime.now(ZoneId.systemDefault()))
            .name(nameOf(configuration, kind))
            .room(configuration.getRoom())
            .kind(kind)
            .vendor(SmartVendor.EATON)
            .enabled(true)
            .eatonGateway(configuration.getGateway())
            .eatonPoint(configuration.getPoint())
            .lightKind(lightKindOf(configuration.getType()))
            //never null, like the column (V16): no Eaton configuration names a window sensor
            .openingSensors(List.of())
            .build());
    }

    //Empty for a device of a kind the Eaton configuration has no type for (an opening sensor on a
    //data point, should the registry ever hold one): to amx-service that point is not configured.
    //Answered as "other" it would be a datagram amx-service fails on, every time it arrives
    public Optional<EatonConfigurationResponse> toResponse(final DeviceEntity device) {
        return typeOf(device).map(type -> EatonConfigurationResponse.builder()
            .point(device.getEatonPoint())
            .type(type)
            .room(device.getRoom())
            .build());
    }

    private static Optional<DeviceKind> kindOf(final SmartDeviceType type) {
        return switch (type) {
            case TEMPERATURE_SENSOR -> Optional.of(DeviceKind.TEMPERATURE_SENSOR);
            case BLINDS -> Optional.of(DeviceKind.BLINDS);
            case LIGHT, DIMMER -> Optional.of(DeviceKind.LIGHT);
            case PUSH_BUTTON -> Optional.of(DeviceKind.PUSH_BUTTON);
            case MOTION_SENSOR -> Optional.of(DeviceKind.MOTION_SENSOR);
            case OTHER -> Optional.empty();
        };
    }

    //every type named, like kindOf: a type added to the SDK has to be decided here too, or the
    //compiler stops - a default would store a new kind of light with no way it is driven
    private static LightKind lightKindOf(final SmartDeviceType type) {
        return switch (type) {
            case LIGHT -> LightKind.RELAY;
            case DIMMER -> LightKind.DIMMER;
            case TEMPERATURE_SENSOR, BLINDS, PUSH_BUTTON, MOTION_SENSOR, OTHER -> null;
        };
    }

    //what amx-service reads a datagram by; the kinds are all named, so a new one is decided here.
    //A push button and a motion sensor (the PIR sensors of the AMX system) only report: amx-service
    //publishes their frames as device events, and never sends them anything. amx-service must know
    //both types (smart-home-sdk 1.9.0) before this answers either - on an older version every
    //datagram of such a point fails its lookup
    private static Optional<SmartDeviceType> typeOf(final DeviceEntity device) {
        return switch (device.getKind()) {
            case TEMPERATURE_SENSOR -> Optional.of(SmartDeviceType.TEMPERATURE_SENSOR);
            case BLINDS -> Optional.of(SmartDeviceType.BLINDS);
            case LIGHT -> Optional.of(
                device.getLightKind() == LightKind.DIMMER ? SmartDeviceType.DIMMER : SmartDeviceType.LIGHT);
            case PUSH_BUTTON -> Optional.of(SmartDeviceType.PUSH_BUTTON);
            case MOTION_SENSOR -> Optional.of(SmartDeviceType.MOTION_SENSOR);
            //an appliance is a Shelly plug (HAS-272), never a data point of the AMX system
            case OPENING_SENSOR, PRESENCE_SENSOR, WEATHER_STATION, BTHOME_GATEWAY, APPLIANCE -> Optional.empty();
        };
    }

    //"Living room temperature sensor 45" - the room, the kind and the point, the wording of the
    //migration that moved the configurations in (V13). The point tells two lights of one room
    //apart; a name is not unique in the registry, and the same point on the other gateway in the
    //same room would read the same
    private static String nameOf(final EatonDeviceConfiguration configuration, final DeviceKind kind) {
        final String room = configuration.getRoom().name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return room.substring(0, 1).toUpperCase(Locale.ROOT) + room.substring(1)
            + " " + kind.name().replace('_', ' ').toLowerCase(Locale.ROOT)
            + " " + configuration.getPoint();
    }
}
