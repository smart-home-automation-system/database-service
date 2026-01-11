package cloud.cholewa.data.device.eaton.repository;

import cloud.cholewa.data.device.eaton.model.EatonDeviceConfigurationEntity;
import cloud.cholewa.home.model.EatonGatewayType;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface EatonDeviceConfigurationRepository extends R2dbcRepository<EatonDeviceConfigurationEntity, Long> {

    Mono<EatonDeviceConfigurationEntity> findByPointAndGateway(int point, EatonGatewayType gateway);

    Mono<Boolean> existsByPointAndGateway(Integer point, EatonGatewayType gateway);
}
