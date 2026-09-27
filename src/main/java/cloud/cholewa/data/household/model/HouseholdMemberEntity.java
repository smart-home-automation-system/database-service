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
    @Size(min = 5, max = 50)
    String name;

    @NotNull
    @Size(min = 11, max = 11)
    @Pattern(regexp = "^[0-9]{3}-[0-9]{3}-[0-9]{3}$")
    String phone;
    
    boolean active;
}
