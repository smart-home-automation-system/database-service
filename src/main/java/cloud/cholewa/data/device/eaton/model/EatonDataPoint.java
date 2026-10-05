package cloud.cholewa.data.device.eaton.model;

//The range of an Eaton data point. It is stated in three more places that code here cannot share a
//constant with: the CHECK constraint of eaton_devices.point (V7), the schema of the SDK models
//(eaton.yaml, which gives the request body its @Min/@Max) and amx-service, which rejects a data
//point outside it before it asks (MessageUtilities.extractDataPoint) - change all four together.
public final class EatonDataPoint {

    public static final int MIN = 1;
    public static final int MAX = 99;

    //the message of the constraint, written out so that the answer does not follow the locale of
    //the JVM; a compile-time constant, because an annotation takes nothing else
    public static final String OUT_OF_RANGE = "point must be between " + MIN + " and " + MAX;

    private EatonDataPoint() {
    }
}
