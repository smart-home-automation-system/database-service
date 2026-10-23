package cloud.cholewa.data.household.repository;

import cloud.cholewa.data.household.model.PushSubscriptionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

//The push subscriptions of the browsers of the household (V19). Not an R2dbcRepository: storing a
//subscription is "this endpoint is now this member's", one statement keyed by the endpoint - a
//browser that subscribes again is the same row, also when another member subscribes on it. Asking
//first and writing second would be two statements a second caller could get between.
@Repository
@RequiredArgsConstructor
public class PushSubscriptionRepository {

    private static final String COLUMNS = "id, member_id, endpoint, p256dh, auth, user_agent, created_at, last_used_at";

    //the row keeps its id and the time it was first stored; everything a browser says replaces
    private static final String STORE = """
        INSERT INTO push_subscriptions (member_id, endpoint, p256dh, auth, user_agent, created_at)
        VALUES (:memberId, :endpoint, :p256dh, :auth, :userAgent, :createdAt)
        ON CONFLICT (endpoint) DO UPDATE SET
            member_id = EXCLUDED.member_id,
            p256dh = EXCLUDED.p256dh,
            auth = EXCLUDED.auth,
            user_agent = EXCLUDED.user_agent
        RETURNING\s""" + COLUMNS;

    //The names are compared the way the registry keeps them unique - upper() of PostgreSQL on both
    //sides (V10), not a Java toUpperCase, which disagrees for some letters. A switched-off member
    //has none to give, whatever a row says
    private static final String OF_ACTIVE_MEMBERS = """
        SELECT ps.id, ps.member_id, ps.endpoint, ps.p256dh, ps.auth, ps.user_agent, ps.created_at, ps.last_used_at
        FROM push_subscriptions ps
            JOIN household_members m ON m.id = ps.member_id
        WHERE m.active
          AND upper(m.name) IN (SELECT upper(n) FROM unnest(CAST(:names AS TEXT[])) AS n)
        ORDER BY ps.created_at, ps.id""";

    private final R2dbcEntityTemplate template;

    public Mono<PushSubscriptionEntity> store(final PushSubscriptionEntity subscription) {
        DatabaseClient.GenericExecuteSpec statement = template.getDatabaseClient().sql(STORE)
            .bind("memberId", subscription.getMemberId())
            .bind("endpoint", subscription.getEndpoint())
            .bind("p256dh", subscription.getP256dh())
            .bind("auth", subscription.getAuth())
            .bind("createdAt", subscription.getCreatedAt());
        statement = subscription.getUserAgent() == null
            ? statement.bindNull("userAgent", String.class)
            : statement.bind("userAgent", subscription.getUserAgent());

        return statement
            .map((row, metadata) -> template.getConverter().read(PushSubscriptionEntity.class, row, metadata))
            .one();
    }

    public Flux<PushSubscriptionEntity> findAllOfActiveMembers(final Collection<String> names) {
        return template.getDatabaseClient().sql(OF_ACTIVE_MEMBERS)
            .bind("names", names.toArray(String[]::new))
            .map((row, metadata) -> template.getConverter().read(PushSubscriptionEntity.class, row, metadata))
            .all();
    }

    //the number of rows that went: none when the member had no such subscription
    public Mono<Long> deleteOfMember(final Long memberId, final String endpoint) {
        return template.delete(
            Query.query(Criteria.where("member_id").is(memberId).and("endpoint").is(endpoint)),
            PushSubscriptionEntity.class
        );
    }

    public Mono<Long> deleteAllOfMember(final Long memberId) {
        return template.delete(Query.query(Criteria.where("member_id").is(memberId)), PushSubscriptionEntity.class);
    }
}
