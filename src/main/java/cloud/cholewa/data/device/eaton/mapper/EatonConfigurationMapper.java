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
            .build());
    }

    public EatonConfigurationResponse toResponse(final DeviceEntity device) {
        return EatonConfigurationResponse.builder()
            .point(device.getEatonPoint())
            .type(typeOf(device))
            .room(device.getRoom())
            .build();
    }

    private static Optional<DeviceKind> kindOf(final SmartDeviceType type) {
        return switch (type) {
            case TEMPERATURE_SENSOR -> Optional.of(DeviceKind.TEMPERATURE_SENSOR);
            case BLINDS -> Optional.of(DeviceKind.BLINDS);
            case LIGHT, DIMMER -> Optional.of(DeviceKind.LIGHT);
            case OTHER -> Optional.empty();
        };
    }

    private static LightKind lightKindOf(final SmartDeviceType type) {
        return switch (type) {
            case LIGHT -> LightKind.RELAY;
            case DIMMER -> LightKind.DIMMER;
            default -> null;
        };
    }

    //what amx-service reads a datagram by; a kind the old vocabulary has no word for is "other"
    private static SmartDeviceType typeOf(final DeviceEntity device) {
        return switch (device.getKind()) {
            case TEMPERATURE_SENSOR -> SmartDeviceType.TEMPERATURE_SENSOR;
            case BLINDS -> SmartDeviceType.BLINDS;
            case LIGHT -> device.getLightKind() == LightKind.DIMMER ? SmartDeviceType.DIMMER : SmartDeviceType.LIGHT;
            default -> SmartDeviceType.OTHER;
        };
    }

    //"Living room temperature sensor 45" - the room, the kind and the point, the wording of the
    //migration that moved the configurations in (V13). The point keeps two lights of one room apart
    private static String nameOf(final EatonDeviceConfiguration configuration, final DeviceKind kind) {
        final String room = configuration.getRoom().name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return room.substring(0, 1).toUpperCase(Locale.ROOT) + room.substring(1)
            + " " + kind.name().replace('_', ' ').toLowerCase(Locale.ROOT)
            + " " + configuration.getPoint();
    }
}
