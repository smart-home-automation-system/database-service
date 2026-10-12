package cloud.cholewa.data.device.registry.model;

import cloud.cholewa.home.model.BlindsPlacement;
import cloud.cholewa.home.model.DeviceKind;
import cloud.cholewa.home.model.EatonGatewayType;
import cloud.cholewa.home.model.LightKind;
import cloud.cholewa.home.model.RoomName;
import cloud.cholewa.home.model.SmartVendor;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

//A row of the device registry. The three bindings of the SDK model (shelly, eaton, bthome) are
//columns of the one row here - exactly one of them is filled, which the schema checks (V13).
//What may not be null is said there as well: nothing validates an entity, so an annotation here
//would promise what no code keeps.
@Value
@Builder
@Table("devices")
public class DeviceEntity {

    @Id
    Long id;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    String name;
    RoomName room;
    DeviceKind kind;
    SmartVendor vendor;
    Boolean enabled;

    String shellyAddress;
    Integer shellyGeneration;
    String shellyDeviceId;
    Integer shellyChannel;

    EatonGatewayType eatonGateway;
    Integer eatonPoint;

    String bthomeGatewayDeviceId;
    String bthomeAddress;

    LightKind lightKind;
    BlindsPlacement placement;
    Boolean positioning;
}
