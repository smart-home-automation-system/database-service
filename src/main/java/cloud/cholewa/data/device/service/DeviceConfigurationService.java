package cloud.cholewa.data.device.service;

import cloud.cholewa.data.device.mapper.DeviceConfigurationMapper;
import cloud.cholewa.data.device.repository.DeviceConfigurationRepository;
import cloud.cholewa.data.error.DeviceConfigurationNotFoundException;
import cloud.cholewa.data.error.InvalidDeviceConfigurationException;
import cloud.cholewa.home.model.DeviceConfigurationRequest;
import cloud.cholewa.home.model.EatonConfigurationResponse;
import cloud.cholewa.home.model.EatonGateway;
import cloud.cholewa.home.model.IotVendor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static cloud.cholewa.data.error.CustomErrorDescription.CONFIGURATION_EXIST_IN_DATABASE;
import static cloud.cholewa.data.error.CustomErrorDescription.MISSING_DEVICE_CONFIGURATION;
import static cloud.cholewa.data.error.CustomErrorDescription.UNKNOWN_DEVICE_CONFIGURATION;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceConfigurationService {

    private final DeviceConfigurationRepository repository;

    public Mono<Void> addDeviceConfiguration(final DeviceConfigurationRequest request) {
        return Mono.justOrEmpty(request.getDeviceConfiguration())
            .switchIfEmpty(Mono.error(() -> new InvalidDeviceConfigurationException(
                MISSING_DEVICE_CONFIGURATION.getDescription())))
            .filter(DeviceConfigurationValidator::isOnlyOneConfigurationProvided)
            .flatMap(configuration -> {
                if (DeviceConfigurationValidator.isValidEatonDevice(configuration)) {
                    return repository.existsByIotVendorAndDataPointAndEatonGateway(
                        IotVendor.fromValue(request.getIotVendor()),
                        configuration.getEatonConfiguration().getDataPoint(),
                        configuration.getEatonConfiguration().getEatonGateway()
                    ).flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? Mono.error(() -> new InvalidDeviceConfigurationException(CONFIGURATION_EXIST_IN_DATABASE.getDescription()))
                        : repository.save(DeviceConfigurationMapper.addingEatonToEntity(request)).then());
                } else if (DeviceConfigurationValidator.isValidShellyDevice(configuration)) {
                    return Mono.error(() -> new InvalidDeviceConfigurationException(UNKNOWN_DEVICE_CONFIGURATION.getDescription()));
                } else {
                    return Mono.error(() -> new InvalidDeviceConfigurationException(UNKNOWN_DEVICE_CONFIGURATION.getDescription()));
                }
            });
    }

    public Mono<EatonConfigurationResponse> getDeviceConfigurationByDataPointAndGateway(
        final Integer dataPoint, final String gateway
    ) {
        return repository.findDeviceConfigurationEntityByDataPointAndEatonGateway(
            dataPoint, EatonGateway.fromValue(gateway)
        )
            .map(DeviceConfigurationMapper::toEatonConfigurationResponse)
            .switchIfEmpty(Mono.error(() -> new DeviceConfigurationNotFoundException(
                "Not found Eaton device configuration for dataPoint: " + dataPoint))
            );
    }
}
