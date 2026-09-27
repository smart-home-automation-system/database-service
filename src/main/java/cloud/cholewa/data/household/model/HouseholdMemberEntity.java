package cloud.cholewa.data.household.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Value
@Table(name = "household_members")
public class HouseholdMemberEntity {

    @Id
    Long id;

    @NotNull
    LocalDateTime createdAt;

    LocalDateTime updatedAt;

    @NotNull
    @Size(min = 3, max = 50)
    String name;

    @NotNull
    //E.164, as the SMS recipient it is used for (V9)
    @Size(max = 16)
    @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$")
    String phone;
    
    boolean active;
}
