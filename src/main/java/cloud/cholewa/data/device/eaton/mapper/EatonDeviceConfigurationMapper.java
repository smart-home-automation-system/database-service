package cloud.cholewa.data.device.eaton.mapper;

import cloud.cholewa.data.device.eaton.model.DeviceConfigurationEntity;
import cloud.cholewa.data.device.eaton.model.EatonDeviceConfigurationEntity;
import cloud.cholewa.home.model.EatonConfigurationResponse;
import cloud.cholewa.home.model.EatonDeviceConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EatonDeviceConfigurationMapper {

    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    DeviceConfigurationEntity toEntity(EatonDeviceConfiguration deviceConfiguration);

    EatonConfigurationResponse map(EatonDeviceConfigurationEntity entity);

}
