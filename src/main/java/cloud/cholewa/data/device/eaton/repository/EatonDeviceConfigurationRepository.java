package cloud.cholewa.data.device.eaton.repository;

import cloud.cholewa.data.device.eaton.model.DeviceConfigurationEntity;
import cloud.cholewa.data.device.eaton.model.EatonDeviceConfigurationEntity;
import cloud.cholewa.home.model.EatonGatewayType;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface EatonDeviceConfigurationRepository extends R2dbcRepository<DeviceConfigurationEntity, Long> {

    Mono<EatonDeviceConfigurationEntity> findByPointAndGateway(
        final Integer point,
        final @NotNull EatonGatewayType gateway
    );

    Mono<Boolean> existsByPoint(Integer point);
}
