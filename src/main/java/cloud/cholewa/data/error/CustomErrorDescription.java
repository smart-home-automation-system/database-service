package cloud.cholewa.data.error;

import cloud.cholewa.commons.error.model.ErrorId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

//The name of a constant is the code of the error response (ErrorMessage.code), so it is wire
//contract: a caller branches on it. Renaming one compiles and passes everything here, and silently
//changes what the caller does - CustomErrorDescriptionTest pins the names. The description is the
//message of the response, worded for people, and may be reworded.
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum CustomErrorDescription implements ErrorId {

    CONFIGURATION_EXIST("Device configuration already exists"),
    NOT_FOUND_DEVICE_CONFIGURATION("Device configuration not found"),
    UNKNOWN_GATEWAY("Invalid device configuration"),
    NOT_FOUND_HOUSEHOLD_MEMBER("Household member not found"),
    HOUSEHOLD_CONFLICT("Household member conflict"),
    INVALID_HOUSEHOLD_MEMBER("Invalid household member"),
    DEVICE_EXIST("Member device already exists"),
    NOT_FOUND_MEMBER_DEVICE("Member device not found");

    @Getter
    private final String description;
}
