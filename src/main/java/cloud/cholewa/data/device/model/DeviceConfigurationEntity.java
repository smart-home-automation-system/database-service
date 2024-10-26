package cloud.cholewa.data.device.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Size(min = 3, max = 20)
    String name;

    @NotNull
    @Size(min = 3, max = 20)
    String iotType;

    Integer dataPoint;
}
