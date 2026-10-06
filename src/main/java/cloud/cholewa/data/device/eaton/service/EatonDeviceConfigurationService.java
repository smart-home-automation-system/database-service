package cloud.cholewa.data.device.eaton.service;

import cloud.cholewa.data.device.eaton.mapper.EatonDeviceConfigurationMapper;
import cloud.cholewa.data.device.eaton.repository.EatonDeviceConfigurationRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class EatonDeviceConfigurationService {

    private static final String POINT_GATEWAY_UNIQUE_CONSTRAINT = "eaton_devices_point_gateway_uq";
    //the details of the two error responses; what they are errors of is named by the processor
    private static final String ALREADY_REGISTERED = "Configuration exist in database";
    private static final String UNKNOWN_GATEWAY_DETAILS = "Unknown Eaton gateway: ";

    private final EatonDeviceConfigurationRepository repository;
    private final EatonDeviceConfigurationMapper mapper;

    //a configuration already registered for the point + gateway is a conflict (409) whichever way it is
    //found - by the check below or, when two requests race past it, by the unique constraint (V7)
    public Mono<Void> add(final EatonDeviceConfiguration deviceConfiguration) {
        return repository.existsByPointAndGateway(deviceConfiguration.getPoint(), deviceConfiguration.getGateway())
            .flatMap(exists -> exists
                ? Mono.error(() -> new DeviceConfigurationExistsException(ALREADY_REGISTERED))
                : repository.save(mapper.toEntity(deviceConfiguration)).then()
            )
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
            .flatMap(gatewayType -> repository.findByPointAndGateway(dataPoint, gatewayType))
            .doOnNext(eatonConfiguration ->
                log.info(
                    "Found Eaton device configuration for dataPoint: {}, in room: {}",
                    dataPoint,
                    eatonConfiguration.getRoom()
                )
            )
            .map(mapper::toResponse)
            .switchIfEmpty(Mono.error(() -> new DeviceConfigurationNotFoundException(
                "Device not found for point: " + dataPoint + " on gateway: " + gateway)));
    }
}
