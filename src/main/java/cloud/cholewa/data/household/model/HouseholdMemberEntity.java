package cloud.cholewa.data.household.model;

import cloud.cholewa.home.model.MemberRole;
import cloud.cholewa.home.model.RoomName;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.List;

//no constraint annotations: nothing validates an entity, and the rules are stated where they are
//enforced - the SDK model at the API, the checks of the schema (V8-V11) in the database
@Value
@Table(name = "household_members")
public class HouseholdMemberEntity {

    @Id
    Long id;

    LocalDateTime createdAt;

    LocalDateTime updatedAt;

    String name;

    //E.164, as the SMS recipient it is used for (V9)
    String phone;

    boolean active;

    //never null in a row (V11). What a request without a role means is decided in HouseholdMemberMapper
    //and nowhere else: a resident when a member is added, the stored role when one is updated
    MemberRole role;

    //an array column (V11), in the order the rooms are shown; never null, empty for none. Stored as the
    //names of the constants, like the role: a constant renamed or removed in the SDK needs a migration
    //of the rows that hold it, or reading the registry fails on them
    List<RoomName> rooms;
}
