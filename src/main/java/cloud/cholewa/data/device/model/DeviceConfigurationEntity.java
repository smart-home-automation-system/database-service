package cloud.cholewa.data.device.model;

import cloud.cholewa.home.model.DeviceType;
import cloud.cholewa.home.model.EatonGateway;
import cloud.cholewa.home.model.IotVendor;
import cloud.cholewa.home.model.RoomName;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("device_configuration")
@Value
@Builder
public class DeviceConfigurationEntity {

    @Id
    Long id;

    @NotNull
    LocalDateTime createdAt;

    @NotNull
    LocalDateTime updatedAt;

    @NotNull
    RoomName roomName;

    @NotNull
    IotVendor iotVendor;

    @NotNull
    DeviceType deviceType;

    Integer dataPoint;

    EatonGateway eatonGateway;
}
