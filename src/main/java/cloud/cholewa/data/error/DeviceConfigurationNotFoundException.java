package cloud.cholewa.data.error;

public class DeviceConfigurationNotFoundException extends RuntimeException {
    public DeviceConfigurationNotFoundException(final String message) {
        super(message);
    }
}
