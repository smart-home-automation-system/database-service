package cloud.cholewa.data.household.repository;

import cloud.cholewa.data.household.model.MemberRequestEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collection;

//The household requests (V21). Not an R2dbcRepository: the writes are conditional ("done unless done
//already", "sent unless sent already") and the read of notification-service joins the registry - each one
//statement, so a second caller cannot get between a read and a write
@Repository
@RequiredArgsConstructor
public class MemberRequestRepository {

    //every open request not yet texted of every member switched on, with the name of its member as the
    //registry spells it now - by member, then oldest first
    private static final String PENDING = """
        SELECT r.*, m.name AS member_name
        FROM member_requests r
            JOIN household_members m ON m.id = r.member_id
        WHERE m.active AND r.sent_at IS NULL AND r.done_at IS NULL
        ORDER BY upper(m.name), r.created_at, r.id""";

    private static final String MARK_SENT = """
        UPDATE member_requests SET sent_at = :sentAt
        WHERE id = ANY(CAST(:ids AS BIGINT[])) AND sent_at IS NULL""";

    private static final String MEMBER_ID = "member_id";
    private static final String DONE_AT = "done_at";

    private final R2dbcEntityTemplate template;

    public Mono<MemberRequestEntity> save(final MemberRequestEntity request) {
        return template.insert(request);
    }

    //the list of a member: the open requests, and with doneSince also those done since then - oldest first
    public Flux<MemberRequestEntity> findListOfMember(final Long memberId, final LocalDateTime doneSince) {
        final Criteria open = Criteria.where(DONE_AT).isNull();
        final Criteria shown = doneSince == null ? open : open.or(DONE_AT).greaterThanOrEquals(doneSince);
        return template.select(
            Query.query(Criteria.where(MEMBER_ID).is(memberId).and(shown))
                .sort(Sort.by("created_at", "id")),
            MemberRequestEntity.class);
    }

    public Mono<MemberRequestEntity> findOneOfMember(final Long memberId, final Long id) {
        return template.selectOne(
            Query.query(Criteria.where("id").is(id).and(MEMBER_ID).is(memberId)), MemberRequestEntity.class);
    }

    //done unless done already - the first doneAt and doneBy stay; the number of rows changed
    public Mono<Long> markDone(final Long memberId, final Long id, final LocalDateTime doneAt, final String doneBy) {
        return template.update(
            Query.query(Criteria.where("id").is(id).and(MEMBER_ID).is(memberId).and(DONE_AT).isNull()),
            Update.update(DONE_AT, doneAt).set("done_by", doneBy),
            MemberRequestEntity.class);
    }

    public Mono<Long> deleteOfMember(final Long memberId, final Long id) {
        return template.delete(
            Query.query(Criteria.where("id").is(id).and(MEMBER_ID).is(memberId)), MemberRequestEntity.class);
    }

    //the retention without a scheduler: the done requests of a member done before the time
    public Mono<Long> deleteDoneBefore(final Long memberId, final LocalDateTime before) {
        return template.delete(
            Query.query(Criteria.where(MEMBER_ID).is(memberId).and(DONE_AT).lessThan(before)),
            MemberRequestEntity.class);
    }

    public Flux<PendingRequest> findPending() {
        return template.getDatabaseClient().sql(PENDING)
            .map((row, metadata) -> new PendingRequest(
                template.getConverter().read(MemberRequestEntity.class, row, metadata),
                row.get("member_name", String.class)))
            .all();
    }

    //sent unless sent already: an id texted before, or unknown, is skipped. The number of rows changed
    public Mono<Long> markSent(final Collection<Long> ids, final LocalDateTime sentAt) {
        return template.getDatabaseClient().sql(MARK_SENT)
            .bind("ids", ids.toArray(Long[]::new))
            .bind("sentAt", sentAt)
            .fetch()
            .rowsUpdated();
    }

    //a pending request with the name of its member - the read of notification-service
    public record PendingRequest(MemberRequestEntity request, String memberName) {
    }
}
