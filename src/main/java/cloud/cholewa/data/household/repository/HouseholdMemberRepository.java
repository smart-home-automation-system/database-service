package cloud.cholewa.data.household.repository;

import cloud.cholewa.data.household.model.HouseholdMemberEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface HouseholdMemberRepository extends R2dbcRepository<HouseholdMemberEntity, Long> {

    Mono<Boolean> existsByNameIgnoreCase(String name);

    Mono<HouseholdMemberEntity> findByNameIgnoreCase(String name);

}
