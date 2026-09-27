package cloud.cholewa.data.error;

public class HouseholdMemberNotFoundException extends RuntimeException {
    public HouseholdMemberNotFoundException(final String message) {
        super(message);
    }

    public static HouseholdMemberNotFoundException forName(final String name) {
        return new HouseholdMemberNotFoundException("No household member named [" + name + "]");
    }
}
