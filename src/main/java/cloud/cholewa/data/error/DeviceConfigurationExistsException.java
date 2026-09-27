package cloud.cholewa.data.error;

public class DeviceConfigurationExistsException extends RuntimeException {
    public DeviceConfigurationExistsException(final String message) {
        super(message);
    }
}
