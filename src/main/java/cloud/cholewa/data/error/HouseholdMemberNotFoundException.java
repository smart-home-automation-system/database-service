package cloud.cholewa.data.error;

public class HouseholdMemberNotFoundException extends RuntimeException {
    public HouseholdMemberNotFoundException(final String message) {
        super(message);
    }
}
