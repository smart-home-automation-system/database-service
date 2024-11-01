package cloud.cholewa.data.device.model;

import cloud.cholewa.home.model.EatonConfiguration;
import cloud.cholewa.home.model.ShellyConfiguration;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@AllArgsConstructor
@Getter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DeviceConfiguration {
    private EatonConfiguration eatonConfiguration;
    private ShellyConfiguration shellyConfiguration;
}
