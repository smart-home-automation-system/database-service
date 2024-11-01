package cloud.cholewa.data.mapper;

import cloud.cholewa.data.device.api.DeviceConfigurationRequest;
import cloud.cholewa.data.device.model.DeviceConfigurationEntity;
import cloud.cholewa.home.model.EatonConfiguration;
import cloud.cholewa.home.model.IotVendor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DeviceConfigurationMapper {

    public static DeviceConfigurationEntity addingEatonToEntity(final DeviceConfigurationRequest request) {
        EatonConfiguration eatonConfiguration = request.getDeviceConfiguration().getEatonConfiguration();

        return DeviceConfigurationEntity.builder()
            .createdAt(LocalDateTime.now())
            .roomName(request.getRoomName())
            .iotVendor(IotVendor.fromValue(request.getIotVendor()))
            .dataPoint(eatonConfiguration.getDataPoint())
            .eatonGateway(eatonConfiguration.getEatonGateway())
            .build();
    }
}
