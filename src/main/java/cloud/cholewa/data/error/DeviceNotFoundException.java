package cloud.cholewa.data.error;

public class DeviceNotFoundException extends RuntimeException {

    private DeviceNotFoundException(final String message) {
        super(message);
    }

    //one place for the wording, for the reads and for the writes to come
    public static DeviceNotFoundException forId(final long id) {
        return new DeviceNotFoundException("Device not found for id: " + id);
    }
}
