package cloud.cholewa.data.error;

public class MemberDeviceNotFoundException extends RuntimeException {
    public MemberDeviceNotFoundException(final String message) {
        super(message);
    }
}
