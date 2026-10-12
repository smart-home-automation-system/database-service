package cloud.cholewa.data.error;

//the binding of the device is taken: a channel of a Shelly, a data point of a gateway and a
//Bluetooth sensor are each one device of the registry
public class DeviceConflictException extends RuntimeException {

    public DeviceConflictException(final String message) {
        super(message);
    }
}
