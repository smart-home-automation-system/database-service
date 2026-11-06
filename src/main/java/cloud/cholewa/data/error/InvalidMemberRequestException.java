package cloud.cholewa.data.error;

//A household request (HAS-409) the rules refuse - no text, more than one line, an author or a member
//ticking it off who is no member switched on: INVALID_REQUEST, the reason in the message (details)
public class InvalidMemberRequestException extends RuntimeException {

    public InvalidMemberRequestException(final String message) {
        super(message);
    }
}
