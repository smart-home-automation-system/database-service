package cloud.cholewa.data.device.repository;

import cloud.cholewa.data.device.model.DeviceConfigurationEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceConfigurationRepository extends R2dbcRepository<DeviceConfigurationEntity, Long> {
}
