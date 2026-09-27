package cloud.cholewa.data.household.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Value
@Table(name = "member_devices")
public class MemberDeviceEntity {

    @Id
    Long id;

    @NotNull
    LocalDateTime createdAt;

    LocalDateTime updatedAt;

    @NotNull
    Long memberId;

    @NotNull
    @Size(max = 50)
    String name;

    @NotNull
    @Pattern(regexp = "^([0-9a-f]{2}:){5}[0-9a-f]{2}$")
    String mac;
}
