package cloud.cholewa.data.household.repository;

import cloud.cholewa.data.household.model.MemberDeviceEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface MemberDeviceRepository extends R2dbcRepository<MemberDeviceEntity, Long> {

    Mono<MemberDeviceEntity> findByMemberIdAndMac(Long memberId, String mac);

}
