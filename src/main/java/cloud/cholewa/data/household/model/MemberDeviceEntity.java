package cloud.cholewa.data.household.model;

import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

//no constraint annotations, for the same reason as in HouseholdMemberEntity
@Value
@Table(name = "member_devices")
public class MemberDeviceEntity {

    @Id
    Long id;

    LocalDateTime createdAt;

    LocalDateTime updatedAt;

    Long memberId;

    String name;

    String mac;
}
