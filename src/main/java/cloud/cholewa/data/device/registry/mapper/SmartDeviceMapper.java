package cloud.cholewa.data.device.registry.mapper;

import cloud.cholewa.data.device.registry.model.DeviceEntity;
import cloud.cholewa.home.model.BthomeBinding;
import cloud.cholewa.home.model.EatonBinding;
import cloud.cholewa.home.model.ShellyBinding;
import cloud.cholewa.home.model.SmartDevice;
import org.springframework.stereotype.Component;

//A row of the registry as the SDK model. Written by hand, unlike the other mappers of the service:
//the row is flat and the model has a binding object per way a device is reached, of which a device
//has one. MapStruct fills a nested target whenever its source object is there - every device would
//answer with all three bindings, two of them empty objects - and the model has no setter for the
//id (the registry gives it), only the builder.
@Component
public class SmartDeviceMapper {

    public SmartDevice toDevice(final DeviceEntity entity) {
        return SmartDevice.builder()
            .id(entity.getId())
            .name(entity.getName())
            .room(entity.getRoom())
            .kind(entity.getKind())
            .enabled(entity.getEnabled())
            .vendor(entity.getVendor())
            .shelly(shelly(entity))
            .eaton(eaton(entity))
            .bthome(bthome(entity))
            .lightKind(entity.getLightKind())
            .placement(entity.getPlacement())
            .positioning(entity.getPositioning())
            .build();
    }

    //each binding is told by the column it cannot do without - the ones the schema counts (V13)
    private static ShellyBinding shelly(final DeviceEntity entity) {
        if (entity.getShellyAddress() == null) {
            return null;
        }
        return ShellyBinding.builder()
            .address(entity.getShellyAddress())
            .generation(entity.getShellyGeneration())
            .deviceId(entity.getShellyDeviceId())
            .channel(entity.getShellyChannel())
            .build();
    }

    private static EatonBinding eaton(final DeviceEntity entity) {
        if (entity.getEatonGateway() == null) {
            return null;
        }
        return EatonBinding.builder()
            .gateway(entity.getEatonGateway())
            .point(entity.getEatonPoint())
            .build();
    }

    private static BthomeBinding bthome(final DeviceEntity entity) {
        if (entity.getBthomeAddress() == null) {
            return null;
        }
        return BthomeBinding.builder()
            .gatewayDeviceId(entity.getBthomeGatewayDeviceId())
            .address(entity.getBthomeAddress())
            .build();
    }
}
