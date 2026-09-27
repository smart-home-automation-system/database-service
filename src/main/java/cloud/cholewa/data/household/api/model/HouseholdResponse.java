package cloud.cholewa.data.household.api.model;

public record HouseholdResponse(
    String name,
    String phone,
    boolean active
) {
}
