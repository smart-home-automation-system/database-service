package cloud.cholewa.data.error;

//a device that is well formed and passes the SDK model, yet is no device the registry stores - a
//weather station on an Eaton point, a light with the placement of blinds. What the model itself
//refuses never gets this far
public class InvalidDeviceException extends RuntimeException {

    public InvalidDeviceException(final String message) {
        super(message);
    }
}
