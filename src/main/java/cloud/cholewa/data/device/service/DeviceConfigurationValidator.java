package cloud.cholewa.data.device.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DeviceConfigurationValidator {

//    public static boolean isOnlyOneConfigurationProvided(final DeviceConfiguration configuration) {
//        if (configuration.getEatonConfiguration() == null && configuration.getShellyConfiguration() == null) {
//            throw new InvalidDeviceConfigurationException(MISSING_DEVICE_CONFIGURATION.getDescription());
//        } else if (configuration.getEatonConfiguration() != null && configuration.getShellyConfiguration() != null) {
//            throw new InvalidDeviceConfigurationException(TOO_MANY_CONFIGURATIONS.getDescription());
//        }
//
//        return true;
//    }
//
//    public static boolean isValidEatonDevice(DeviceConfiguration configuration) {
//        if (configuration.getEatonConfiguration() != null
//            && configuration.getEatonConfiguration().getDataPoint() != null
//            && configuration.getEatonConfiguration().getEatonGateway() != null
//        ) {
//            return configuration.getEatonConfiguration().getDataPoint() >= 1
//                && configuration.getEatonConfiguration().getDataPoint() <= 99
//                && (configuration.getEatonConfiguration().getEatonGateway().equals(EatonGateway.LIGHTS)
//                || configuration.getEatonConfiguration().getEatonGateway().equals(EatonGateway.BLINDS));
//        }
//
//        return false;
//    }
//
//    public static boolean isValidShellyDevice(DeviceConfiguration configuration) {
//        return false;
//    }
}
