package cloud.cholewa.data.error;

//A household request (HAS-409) that is not on the list of the member named in the path - never there,
//removed already, or of another member: NOT_FOUND_REQUEST, which the dashboard words
public class MemberRequestNotFoundException extends RuntimeException {

    public MemberRequestNotFoundException(final String message) {
        super(message);
    }

    public static MemberRequestNotFoundException forId(final String member, final Long id) {
        return new MemberRequestNotFoundException("No request " + id + " on the list of household member [" + member + "]");
    }
}
