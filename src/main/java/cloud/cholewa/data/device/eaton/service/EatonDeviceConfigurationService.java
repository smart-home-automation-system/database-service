package cloud.cholewa.data.device.eaton.service;

import cloud.cholewa.data.device.eaton.mapper.EatonDeviceConfigurationMapper;
import cloud.cholewa.data.device.eaton.repository.EatonDeviceConfigurationRepository;
import cloud.cholewa.data.error.DeviceConfigurationNotFoundException;
import cloud.cholewa.data.error.InvalidDeviceConfigurationException;
import cloud.cholewa.home.model.EatonConfigurationResponse;
import cloud.cholewa.home.model.EatonDeviceConfiguration;
import cloud.cholewa.home.model.EatonGatewayType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static cloud.cholewa.data.error.CustomErrorDescription.CONFIGURATION_EXIST;

@Slf4j
@Service
@RequiredArgsConstructor
public class EatonDeviceConfigurationService {

    private final EatonDeviceConfigurationRepository repository;
    private final EatonDeviceConfigurationMapper mapper;

    public Mono<Void> add(final EatonDeviceConfiguration deviceConfiguration) {
        return repository.existsByPoint(deviceConfiguration.getPoint())
            .hasElement()
            .flatMap(exists -> exists
                ? Mono.error(() -> new InvalidDeviceConfigurationException(CONFIGURATION_EXIST.getDescription()))
                : repository.save(mapper.toEntity(deviceConfiguration)).then()
            );
    }

    public Mono<EatonConfigurationResponse> get(final Integer dataPoint, final String gateway) {
        return repository.findByPointAndGateway(dataPoint, EatonGatewayType.fromValue(gateway))
            .doOnNext(eatonConfiguration ->
                log.info(
                    "Found Eaton device configuration for dataPoint: {}, in room: {}",
                    dataPoint,
                    eatonConfiguration.getRoom()
                )
            )
            .map(mapper::map)
            .switchIfEmpty(Mono.error(() -> new DeviceConfigurationNotFoundException(
                "Device not found for point: " + dataPoint + " on gateway: " + gateway)));
    }
}
