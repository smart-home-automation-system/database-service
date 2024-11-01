package cloud.cholewa.data.device.api;

import cloud.cholewa.data.device.model.DeviceConfiguration;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Getter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Builder
public class DeviceConfigurationRequest {

    @NotEmpty
    @Size(min = 3, max = 20)
    private String roomName;
    @NotEmpty
    @Size(min = 3, max = 20)
    private String iotVendor;
    @NotEmpty
    @Size(min = 3, max = 20)
    private String deviceType;

    private DeviceConfiguration deviceConfiguration;
}
