package cloud.cholewa.data.device.eaton.service;

import cloud.cholewa.data.device.eaton.mapper.EatonConfigurationMapper;
import cloud.cholewa.data.device.registry.repository.DeviceRepository;
import cloud.cholewa.data.error.DeviceConfigurationExistsException;
import cloud.cholewa.data.error.DeviceConfigurationNotFoundException;
import cloud.cholewa.data.error.InvalidDeviceConfigurationException;
import cloud.cholewa.home.model.EatonConfigurationResponse;
import cloud.cholewa.home.model.EatonDeviceConfiguration;
import cloud.cholewa.home.model.EatonGatewayType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

//The Eaton configuration - the lookup amx-service makes for every datagram. Since V13 it has no
//table of its own: a configuration is a device of the registry with an Eaton binding, and this
//service reads and writes those rows through the API it always had.
@Slf4j
@Service
@RequiredArgsConstructor
public class EatonDeviceConfigurationService {

    private static final String POINT_GATEWAY_UNIQUE_CONSTRAINT = "devices_eaton_gateway_point_uq";

    //the details of the error responses; what they are errors of is named by the processor
    private static final String ALREADY_REGISTERED = "Configuration exist in database";
    private static final String UNKNOWN_GATEWAY_DETAILS = "Unknown Eaton gateway: ";
    private static final String NO_KIND_FOR_TYPE = "A device of this type cannot be registered: ";

    private final DeviceRepository repository;
    private final EatonConfigurationMapper mapper;

    //a device already registered for the point + gateway is a conflict (409) whichever way it is
    //found - by the check below or, when two requests race past it, by the unique constraint (V13)
    public Mono<Void> add(final EatonDeviceConfiguration deviceConfiguration) {
        return Mono.fromCallable(() -> mapper.toDevice(deviceConfiguration)
                .orElseThrow(() -> new InvalidDeviceConfigurationException(
                    NO_KIND_FOR_TYPE + deviceConfiguration.getType().getValue())))
            .flatMap(device -> repository.existsByEatonPoint(deviceConfiguration.getGateway(), deviceConfiguration.getPoint())
                .flatMap(exists -> exists
                    ? Mono.error(() -> new DeviceConfigurationExistsException(ALREADY_REGISTERED))
                    : repository.insert(device).then()
                ))
            .onErrorMap(
                e -> e instanceof DuplicateKeyException && String.valueOf(e.getMessage()).contains(POINT_GATEWAY_UNIQUE_CONSTRAINT),
                e -> new DeviceConfigurationExistsException(ALREADY_REGISTERED)
            );
    }

    public Mono<EatonConfigurationResponse> get(final Integer dataPoint, final String gateway) {
        return Mono.fromCallable(() -> EatonGatewayType.fromValue(gateway))
            .onErrorMap(
                IllegalArgumentException.class,
                e -> new InvalidDeviceConfigurationException(UNKNOWN_GATEWAY_DETAILS + gateway)
            )
            .flatMap(gatewayType -> repository.findByEatonPoint(gatewayType, dataPoint))
            .doOnNext(device ->
                log.info(
                    "Found Eaton device configuration for dataPoint: {}, in room: {}",
                    dataPoint,
                    device.getRoom()
                )
            )
            .map(mapper::toResponse)
            .switchIfEmpty(Mono.error(() -> new DeviceConfigurationNotFoundException(
                "Device not found for point: " + dataPoint + " on gateway: " + gateway)));
    }
}
