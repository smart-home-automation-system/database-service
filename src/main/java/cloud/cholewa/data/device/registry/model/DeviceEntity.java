package cloud.cholewa.data.device.registry.model;

import cloud.cholewa.home.model.BlindsPlacement;
import cloud.cholewa.home.model.DeviceKind;
import cloud.cholewa.home.model.EatonGatewayType;
import cloud.cholewa.home.model.LightKind;
import cloud.cholewa.home.model.RoomName;
import cloud.cholewa.home.model.SmartVendor;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

//A row of the device registry. The three bindings of the SDK model (shelly, eaton, bthome) are
//columns of the one row here - exactly one of them is filled, which the schema checks (V13).
@Value
@Builder
@Table("devices")
public class DeviceEntity {

    @Id
    Long id;
    @NotNull
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    @NotNull
    String name;
    @NotNull
    RoomName room;
    @NotNull
    DeviceKind kind;
    @NotNull
    SmartVendor vendor;
    @NotNull
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
