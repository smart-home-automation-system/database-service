package cloud.cholewa.data.device.registry.repository;

import cloud.cholewa.data.device.registry.model.DeviceEntity;
import cloud.cholewa.home.model.DeviceKind;
import cloud.cholewa.home.model.EatonGatewayType;
import cloud.cholewa.home.model.RoomName;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

//The table of the devices. Not a derived-query interface: the three filters of the list are each
//optional, which is eight derived methods or one query built from what was asked for - and
//"(:kind IS NULL OR kind = :kind)" in a hand-written statement leaves PostgreSQL unable to tell
//the type of a parameter that is null.
@Repository
@RequiredArgsConstructor
public class DeviceRepository {

    private final R2dbcEntityTemplate template;

    //a filter that is null is not applied; the order of the answer is the service's
    public Flux<DeviceEntity> findAll(final DeviceKind kind, final RoomName room, final Boolean enabled) {
        Criteria criteria = Criteria.empty();
        if (kind != null) {
            criteria = criteria.and("kind").is(kind);
        }
        if (room != null) {
            criteria = criteria.and("room").is(room);
        }
        if (enabled != null) {
            criteria = criteria.and("enabled").is(enabled);
        }
        return template.select(Query.query(criteria), DeviceEntity.class);
    }

    public Mono<DeviceEntity> findById(final long id) {
        return template.selectOne(Query.query(Criteria.where("id").is(id)), DeviceEntity.class);
    }

    //the device behind a data point of a gateway of the AMX system - at most one (V13)
    public Mono<DeviceEntity> findByEatonPoint(final EatonGatewayType gateway, final int point) {
        return template.selectOne(Query.query(eatonPoint(gateway, point)), DeviceEntity.class);
    }

    public Mono<Boolean> existsByEatonPoint(final EatonGatewayType gateway, final int point) {
        return template.exists(Query.query(eatonPoint(gateway, point)), DeviceEntity.class);
    }

    //a new row; the id comes back with it
    public Mono<DeviceEntity> insert(final DeviceEntity device) {
        return template.insert(device);
    }

    //the whole row, by its id - every column is written, so the entity has to carry every one
    public Mono<DeviceEntity> update(final DeviceEntity device) {
        return template.update(device);
    }

    //the number of rows that went: none when no device has the id
    public Mono<Long> deleteById(final long id) {
        return template.delete(Query.query(Criteria.where("id").is(id)), DeviceEntity.class);
    }

    private static Criteria eatonPoint(final EatonGatewayType gateway, final int point) {
        return Criteria.where("eatonGateway").is(gateway).and("eatonPoint").is(point);
    }
}
