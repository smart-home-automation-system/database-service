package cloud.cholewa.data.error;

public class HouseholdNotFoundException extends RuntimeException {
    public HouseholdNotFoundException(final String message) {
        super(message);
    }
}
