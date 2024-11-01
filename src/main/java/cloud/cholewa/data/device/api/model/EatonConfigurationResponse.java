package cloud.cholewa.data.device.api.model;

import cloud.cholewa.home.model.DeviceType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class EatonConfigurationResponse {
    private Long id;
    private int dataPoint;
    private DeviceType deviceType;
}
