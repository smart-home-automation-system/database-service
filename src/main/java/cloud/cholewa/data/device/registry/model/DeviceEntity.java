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
import java.time.LocalTime;
import java.util.List;

//A row of the device registry - the one table of the devices of the house. The three bindings of
//the SDK model (shelly, eaton, bthome) are columns of the one row here - exactly one of them is
//filled, which the schema checks (V13). What may not be null is said there as well: nothing
//validates an entity, so an annotation here would promise what no code keeps.
@Value
@Builder(toBuilder = true)
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

    String bthomeAddress;

    LightKind lightKind;
    BlindsPlacement placement;
    Boolean positioning;
    //the ids of the opening sensors on the window of blinds (V16) - an array column, never null
    List<Long> openingSensors;

    //the settings of an appliance (V18): all eight filled for the kind APPLIANCE, none for another
    Integer applianceRunningAboveWatts;
    Integer applianceIdleBelowWatts;
    Integer applianceMinRunMinutes;
    Integer applianceFinishedAfterMinutes;
    Integer applianceReminderEveryMinutes;
    Integer applianceMaxReminders;
    LocalTime applianceQuietFrom;
    LocalTime applianceQuietTo;
}
