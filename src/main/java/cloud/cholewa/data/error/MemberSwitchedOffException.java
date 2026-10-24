package cloud.cholewa.data.error;

//A request for a member who exists but is switched off, and so takes nothing a switched-on member
//would - a push subscription (HAS-272). A state of the member, not a fault of the body: a code of its
//own (MEMBER_SWITCHED_OFF), which the dashboard words apart from INVALID_HOUSEHOLD_MEMBER
public class MemberSwitchedOffException extends RuntimeException {

    public MemberSwitchedOffException(final String message) {
        super(message);
    }
}
