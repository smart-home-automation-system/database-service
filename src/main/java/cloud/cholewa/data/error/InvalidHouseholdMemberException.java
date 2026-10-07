package cloud.cholewa.data.error;

//a request that is well formed and passes the SDK model, yet says something the registry does not
//accept - a room listed twice. What the model itself refuses never gets this far
public class InvalidHouseholdMemberException extends RuntimeException {

    public InvalidHouseholdMemberException(final String message) {
        super(message);
    }
}
