package cloud.cholewa.data.device.registry.mapper;

import cloud.cholewa.data.device.registry.model.DeviceEntity;
import cloud.cholewa.home.model.BthomeBinding;
import cloud.cholewa.home.model.EatonBinding;
import cloud.cholewa.home.model.ShellyBinding;
import cloud.cholewa.home.model.SmartDevice;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

//The SDK model as a row of the registry - the other direction of SmartDeviceMapper, and written by
//hand for the same reason: the model is nested, the row is flat. Every write stores the whole row,
//so each method says where every column comes from: a column added to the entity and forgotten
//here would be written as null over what the row held (the lesson of V12).
@Component
public class DeviceEntityMapper {

    //a new row: the database gives the id, whatever id the payload carried
    public DeviceEntity toNewEntity(final SmartDevice device) {
        return described(device)
            .createdAt(now())
            .build();
    }

    //The row of a device that is replaced: what it is from here on is the payload, whole - a
    //binding or a field the payload leaves out is gone. The row keeps what the registry gave it:
    //the id and the time it was registered
    public DeviceEntity toReplacement(final DeviceEntity existing, final SmartDevice device) {
        return described(device)
            .id(existing.getId())
            .createdAt(existing.getCreatedAt())
            .updatedAt(now())
            .build();
    }

    //the row as it is, switched on or off: toBuilder copies every column, also the ones to come
    public DeviceEntity withEnabled(final DeviceEntity existing, final boolean enabled) {
        return existing.toBuilder()
            .enabled(enabled)
            .updatedAt(now())
            .build();
    }

    //everything a client says of a device; the id and the two times are the registry's
    private static DeviceEntity.DeviceEntityBuilder described(final SmartDevice device) {
        final ShellyBinding shelly = device.getShelly();
        final EatonBinding eaton = device.getEaton();
        final BthomeBinding bthome = device.getBthome();

        return DeviceEntity.builder()
            .name(device.getName())
            .room(device.getRoom())
            .kind(device.getKind())
            .vendor(device.getVendor())
            .enabled(device.getEnabled())
            .shellyAddress(shelly == null ? null : shelly.getAddress())
            .shellyGeneration(shelly == null ? null : shelly.getGeneration())
            .shellyDeviceId(shelly == null ? null : shelly.getDeviceId())
            .shellyChannel(shelly == null ? null : shelly.getChannel())
            .eatonGateway(eaton == null ? null : eaton.getGateway())
            .eatonPoint(eaton == null ? null : eaton.getPoint())
            .bthomeAddress(bthome == null ? null : bthome.getAddress())
            .lightKind(device.getLightKind())
            .placement(device.getPlacement())
            .positioning(device.getPositioning());
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }
}
