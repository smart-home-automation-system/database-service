package cloud.cholewa.data.device.eaton.model;

import cloud.cholewa.home.model.EatonGatewayType;
import cloud.cholewa.home.model.RoomName;
import cloud.cholewa.home.model.SmartDeviceType;
import lombok.Value;
import org.springframework.data.relational.core.mapping.Table;

@Value
@Table("eaton_devices")
public class EatonDeviceConfigurationEntity {

    int point;
    EatonGatewayType gateway;
    SmartDeviceType type;
    RoomName room;
}
