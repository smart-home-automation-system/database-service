package cloud.cholewa.data.error;

import cloud.cholewa.commons.error.model.ErrorId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum CustomErrorDescription implements ErrorId {

    CONFIGURATION_EXIST("Configuration exist in database"),
    NOT_FOUND_DEVICE_CONFIGURATION("Device configuration not found");

    @Getter
    private final String description;
}
