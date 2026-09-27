package cloud.cholewa.data.household.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record HouseholdRequest(
    @Size(min = 5, max = 50)
    @NotBlank(message = "Householder name cannot be empty")
    String name,
    @Size(min = 11, max = 11)
    @NotBlank(message = "Phone number cannot be empty")
    @Pattern(regexp = "^\\d{3}-\\d{3}-\\d{3}$")
    String phone
) {
}
