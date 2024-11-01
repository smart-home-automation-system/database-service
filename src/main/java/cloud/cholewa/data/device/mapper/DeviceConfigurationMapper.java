package cloud.cholewa.data.device.mapper;

import cloud.cholewa.data.device.api.model.DeviceConfigurationRequest;
import cloud.cholewa.data.device.api.model.EatonConfigurationResponse;
import cloud.cholewa.data.device.model.DeviceConfigurationEntity;
import cloud.cholewa.home.model.DeviceType;
import cloud.cholewa.home.model.EatonConfiguration;
import cloud.cholewa.home.model.IotVendor;
import cloud.cholewa.home.model.RoomName;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DeviceConfigurationMapper {

    public static DeviceConfigurationEntity addingEatonToEntity(final DeviceConfigurationRequest request) {
        EatonConfiguration eatonConfiguration = request.getDeviceConfiguration().getEatonConfiguration();

        return DeviceConfigurationEntity.builder()
            .createdAt(LocalDateTime.now())
            .roomName(RoomName.fromValue(request.getRoomName()))
            .iotVendor(IotVendor.fromValue(request.getIotVendor()))
            .deviceType(DeviceType.fromValue(request.getDeviceType()))
            .dataPoint(eatonConfiguration.getDataPoint())
            .eatonGateway(eatonConfiguration.getEatonGateway())
            .build();
    }

    public static EatonConfigurationResponse toEatonConfigurationResponse (final DeviceConfigurationEntity entity) {
        return EatonConfigurationResponse.builder()
            .id(entity.getId())
            .dataPoint(entity.getDataPoint())
            .deviceType(entity.getDeviceType())
            .build();
    }
}
