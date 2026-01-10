package cloud.cholewa.data.device.eaton.model;

import cloud.cholewa.home.model.EatonGatewayType;
import cloud.cholewa.home.model.RoomName;
import cloud.cholewa.home.model.SmartDeviceType;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Value
@Builder
@Table("eaton_devices")
public class EatonDeviceConfigurationEntity {

    @Id
    Long id;

    @NotNull
    LocalDateTime createdAt;

    LocalDateTime updatedAt;

    Integer point;

    @NotNull
    RoomName room;

    @NotNull
    SmartDeviceType type;

    @NotNull
    EatonGatewayType gateway;
}
