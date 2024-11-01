package cloud.cholewa.data.device.repository;

import cloud.cholewa.data.device.model.DeviceConfigurationEntity;
import cloud.cholewa.home.model.EatonGateway;
import cloud.cholewa.home.model.IotVendor;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface DeviceConfigurationRepository extends R2dbcRepository<DeviceConfigurationEntity, Long> {

    Mono<Boolean> existsByIotVendorAndDataPointAndEatonGateway(
        final @NotNull IotVendor iotVendor,
        final @NotNull Integer dataPoint,
        final @NotNull EatonGateway eatonGateway
    );

    Mono<DeviceConfigurationEntity> findDeviceConfigurationEntityByDataPointAndEatonGateway(
        final @NotNull Integer dataPoint,
        final @NotNull EatonGateway eatonGateway
    );
}
